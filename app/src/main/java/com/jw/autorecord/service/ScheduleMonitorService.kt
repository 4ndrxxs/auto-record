package com.jw.autorecord.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jw.autorecord.AutoRecordApp
import com.jw.autorecord.MainActivity
import com.jw.autorecord.data.AppDatabase
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.data.ScheduleOverride
import com.jw.autorecord.util.StoragePaths
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 24시간 상시 실행 + 직접 녹음하는 단일 서비스.
 *
 * 왜 하나의 서비스인가?
 * Android 12+는 백그라운드에서 새 Foreground Service 시작을 제한한다.
 * MonitorService → startForegroundService(RecordingService) 가 지연/차단됨.
 * 하나의 서비스에서 감시+녹음을 모두 처리하면 이 제한을 완전히 우회.
 *
 * foregroundServiceType="microphone|specialUse"
 *   - specialUse: 상시 감시
 *   - microphone: 녹음 시 마이크 접근
 */
class ScheduleMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    // ── 감시 관련 ──
    private val triggeredToday = mutableSetOf<String>()

    // ── 녹음 관련 ──
    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var tickerJob: Job? = null
    private var outputFile: File? = null
    private var recordingStartTime: Long = 0L
    private var pausedElapsedSeconds: Long = 0L  // 일시정지 시점 경과 시간
    private var isPaused: Boolean = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkScheduleNow()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ScheduleMonitorService created")

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoRecord::MonitorWakeLock"
        ).apply {
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ScheduleMonitorService started, action=${intent?.action}")

        // 녹음 조작 액션 처리
        when (intent?.action) {
            ACTION_PAUSE -> { pauseRecording(); return START_STICKY }
            ACTION_RESUME -> { resumeRecording(); return START_STICKY }
            ACTION_STOP -> { stopRecording(); return START_STICKY }
            ACTION_MANUAL_START -> {
                val period = intent.getIntExtra("period", -1)
                val subject = intent.getStringExtra("subject") ?: "수동녹음"
                val teacher = intent.getStringExtra("teacher") ?: ""
                val schedule = Schedule(
                    dayOfWeek = AlarmScheduler.calendarDayToOurDay(
                        Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                    ),
                    period = if (period > 0) period else 0,
                    subject = subject,
                    teacher = teacher,
                    startTime = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
                )
                startRecordingDirectly(schedule)
                return START_STICKY
            }
        }

        val notification = createIdleNotification()

        // ★ microphone + specialUse 타입으로 시작 — 녹음할 때 별도 서비스 불필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        clearTriggeredIfNewDay()

        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)

        return START_STICKY
    }

    // ═══════════════════════════════════════════
    // 감시 로직
    // ═══════════════════════════════════════════

    private fun checkScheduleNow() {
        val prefs = getSharedPreferences("prefs", 0)
        if (!prefs.getBoolean("master_enabled", true)) return

        if (RecordingState.state.value.isRecording) return

        val now = Calendar.getInstance()
        val dayOfWeek = AlarmScheduler.calendarDayToOurDay(now.get(Calendar.DAY_OF_WEEK))
        if (dayOfWeek == 0) return

        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        val dateStr = "%04d-%02d-%02d".format(
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )

        clearTriggeredIfNewDay()

        scope.launch {
            try {
                val db = AppDatabase.getInstance(this@ScheduleMonitorService)
                val overrides = db.scheduleOverrideDao().getOverridesByDateOnce(dateStr)
                val baseSchedules = db.scheduleDao().getSchedulesByDayOnce(dayOfWeek)

                // ★ Override 우선 병합: 오늘의 최종 시간표 생성
                val effectiveSchedules = buildEffectiveSchedules(baseSchedules, overrides, dayOfWeek)

                for (schedule in effectiveSchedules) {
                    val triggerKey = "${dayOfWeek}_${schedule.period}_$dateStr"
                    if (triggerKey in triggeredToday) continue

                    if (isTimeToRecord(currentHour, currentMinute, schedule.startHour, schedule.startMinute)) {
                        triggeredToday.add(triggerKey)

                        Log.i(TAG, "★ Time matched! period=${schedule.period} " +
                                "subject=${schedule.subject} at $currentHour:$currentMinute")

                        withContext(Dispatchers.Main) {
                            sendRecordingAlert(schedule)
                            startRecordingDirectly(schedule)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking schedule", e)
            }
        }
    }

    /**
     * 기본 시간표 + override를 병합하여 오늘의 최종 시간표를 생성.
     * - CANCEL override → 해당 교시 제거
     * - CHANGE override → 해당 교시 대체
     * - ADD override → 새 교시 추가
     */
    private fun buildEffectiveSchedules(
        baseSchedules: List<Schedule>,
        overrides: List<ScheduleOverride>,
        dayOfWeek: Int
    ): List<Schedule> {
        val overrideMap = overrides.associateBy { it.period }
        val result = mutableListOf<Schedule>()

        // 기본 시간표 순회: override 적용
        for (base in baseSchedules) {
            val override = overrideMap[base.period]
            when {
                override == null -> result.add(base) // override 없음 → 기본 사용
                override.type == ScheduleOverride.TYPE_CANCEL -> {
                    Log.i(TAG, "Period ${base.period} cancelled by override")
                    // 스킵 (녹음 안 함)
                }
                else -> {
                    // CHANGE: override 데이터로 대체 (시간은 override에 있으면 사용, 없으면 기본)
                    result.add(override.toSchedule(dayOfWeek, base.startTime))
                    Log.i(TAG, "Period ${base.period} overridden: ${override.subject}")
                }
            }
        }

        // ADD 타입: 기본 시간표에 없는 교시 추가
        val existingPeriods = baseSchedules.map { it.period }.toSet()
        for (override in overrides) {
            if (override.type == ScheduleOverride.TYPE_ADD && override.period !in existingPeriods) {
                result.add(override.toSchedule(dayOfWeek))
                Log.i(TAG, "Period ${override.period} added by override: ${override.subject}")
            }
        }

        return result.sortedBy { it.period }
    }

    private fun isTimeToRecord(
        currentHour: Int, currentMinute: Int,
        scheduleHour: Int, scheduleMinute: Int
    ): Boolean {
        val currentTotal = currentHour * 60 + currentMinute
        val scheduleTotal = scheduleHour * 60 + scheduleMinute
        val diff = currentTotal - scheduleTotal
        return diff in 0..1
    }

    // ═══════════════════════════════════════════
    // 녹음 로직 (RecordingService에서 이관)
    // ═══════════════════════════════════════════

    private fun startRecordingDirectly(schedule: Schedule) {
        if (RecordingState.state.value.isRecording) {
            Log.w(TAG, "Already recording, ignoring")
            return
        }

        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
        val dateDirFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val now = Date()

        val dateStr = dateFormat.format(now)
        val dateDirStr = dateDirFormat.format(now)

        // ★ 설정에서 녹음 시간 읽기 (기본 50분)
        val prefs = getSharedPreferences("prefs", 0)
        val durationMin = prefs.getInt("recording_duration", 50)

        val baseDir = StoragePaths.getDateDir(this, dateDirStr)
        val safeSubject = StoragePaths.sanitizeFileName(schedule.subject)
        val safeTeacher = StoragePaths.sanitizeFileName(schedule.teacher)
        val fileName = "${dateStr}_${schedule.period}교시_${safeSubject}_${safeTeacher}.m4a"
        outputFile = File(baseDir, fileName)

        try {
            // ★ 설정에서 오디오 품질 읽기
            val bitrate = prefs.getInt("audio_bitrate", 128000)
            val sampleRate = prefs.getInt("audio_sample_rate", 44100)

            mediaRecorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(bitrate)
                setAudioSamplingRate(sampleRate)
                setOutputFile(outputFile!!.absolutePath)
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaRecorder error: what=$what extra=$extra")
                    handler.post { stopRecording() }
                }
                prepare()
                start()
            }
            recordingStartTime = System.currentTimeMillis()
            Log.i(TAG, "★ Recording started: $fileName")

            // 알림 업데이트: 녹음 중 표시
            updateNotification("🔴 ${schedule.period}교시 ${schedule.subject} 녹음 중", "시작 중...")

            RecordingState.update {
                copy(
                    isRecording = true,
                    period = schedule.period,
                    subject = schedule.subject,
                    teacher = schedule.teacher,
                    startTimeMillis = recordingStartTime,
                    durationMin = durationMin,
                    elapsedSeconds = 0,
                    filePath = outputFile!!.absolutePath,
                    fileSizeBytes = 0,
                    amplitudeDb = 0
                )
            }

            // 매초 상태 업데이트 + 알림 업데이트
            tickerJob = scope.launch {
                while (isActive) {
                    delay(1000L)

                    // 일시정지 중이면 경과 시간 멈춤
                    val elapsed = if (isPaused) {
                        pausedElapsedSeconds
                    } else {
                        (System.currentTimeMillis() - recordingStartTime) / 1000
                    }

                    val fileSize = outputFile?.length() ?: 0L
                    val amplitude = if (isPaused) 0 else try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (_: Exception) { 0 }

                    RecordingState.update {
                        copy(
                            elapsedSeconds = elapsed,
                            fileSizeBytes = fileSize,
                            amplitudeDb = amplitude
                        )
                    }

                    // 알림 업데이트
                    val min = elapsed / 60
                    val sec = elapsed % 60
                    val status = "%02d:%02d / %d분 | %s".format(
                        min, sec, durationMin,
                        RecordingState.state.value.fileSizeFormatted()
                    )
                    withContext(Dispatchers.Main) {
                        updateNotification(
                            "🔴 ${schedule.period}교시 ${schedule.subject} 녹음 중",
                            status
                        )
                    }
                }
            }

            // 50분 후 자동 종료
            recordingJob = scope.launch {
                delay(durationMin * 60 * 1000L)
                withContext(Dispatchers.Main) {
                    stopRecording()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            RecordingState.reset()
            updateNotification("자동녹음", "녹음 시작 실패: ${e.message}")
        }
    }

    private fun stopRecording() {
        tickerJob?.cancel()
        tickerJob = null
        recordingJob?.cancel()
        recordingJob = null
        isPaused = false
        pausedElapsedSeconds = 0L

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.i(TAG, "★ Recording stopped: ${outputFile?.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
        mediaRecorder = null
        RecordingState.reset()

        // 대기 모드로 알림 복귀
        updateNotification("자동녹음", "자동녹음 대기 중")
    }

    private fun pauseRecording() {
        if (!RecordingState.state.value.isRecording || isPaused) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
            }
            isPaused = true
            pausedElapsedSeconds = RecordingState.state.value.elapsedSeconds
            RecordingState.update { copy(isPaused = true) }
            updateNotification("⏸ 녹음 일시정지", "${RecordingState.state.value.period}교시 ${RecordingState.state.value.subject}")
            Log.i(TAG, "Recording paused")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause", e)
        }
    }

    private fun resumeRecording() {
        if (!RecordingState.state.value.isRecording || !isPaused) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
            }
            isPaused = false
            // 재개 시점 기준으로 시작 시간 재계산
            recordingStartTime = System.currentTimeMillis() - (pausedElapsedSeconds * 1000)
            RecordingState.update { copy(isPaused = false) }
            updateNotification(
                "🔴 ${RecordingState.state.value.period}교시 ${RecordingState.state.value.subject} 녹음 중",
                "재개됨"
            )
            Log.i(TAG, "Recording resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume", e)
        }
    }

    // ═══════════════════════════════════════════
    // 알림
    // ═══════════════════════════════════════════

    private fun sendRecordingAlert(schedule: Schedule) {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, AutoRecordApp.ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("녹음 시작")
            .setContentText("${schedule.period}교시 ${schedule.subject} (${schedule.teacher}) 녹음을 시작합니다")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(ALERT_NOTIFICATION_ID + schedule.period, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted", e)
        }
    }

    private fun createIdleNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AutoRecordApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("자동녹음")
            .setContentText("자동녹음 대기 중")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, AutoRecordApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)

        // ★ 녹음 중일 때 알림에 일시정지/중지 버튼 추가
        if (RecordingState.state.value.isRecording) {
            if (RecordingState.state.value.isPaused) {
                val resumeIntent = Intent(this, ScheduleMonitorService::class.java).apply {
                    action = ACTION_RESUME
                }
                val resumePi = PendingIntent.getService(
                    this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_media_play, "재개", resumePi)
            } else {
                val pauseIntent = Intent(this, ScheduleMonitorService::class.java).apply {
                    action = ACTION_PAUSE
                }
                val pausePi = PendingIntent.getService(
                    this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_media_pause, "일시정지", pausePi)
            }

            val stopIntent = Intent(this, ScheduleMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPi = PendingIntent.getService(
                this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_delete, "중지", stopPi)
        }

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {}
    }

    private fun clearTriggeredIfNewDay() {
        val today = Calendar.getInstance()
        val dateStr = "%04d-%02d-%02d".format(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH) + 1,
            today.get(Calendar.DAY_OF_MONTH)
        )
        triggeredToday.removeAll { !it.endsWith(dateStr) }
    }

    /**
     * ★ 사용자가 최근 앱 목록에서 앱을 제거했을 때 호출됨.
     * stopWithTask="false" 이므로 서비스는 즉시 죽지 않고 이 콜백을 받음.
     * 여기서 AlarmManager로 1초 후 서비스 재시작을 예약.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "★ Task removed (swiped from recents) — scheduling restart")
        scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.w(TAG, "ScheduleMonitorService destroyed — scheduling restart")
        handler.removeCallbacks(checkRunnable)

        // 녹음 중이었다면 정리
        stopRecording()

        // ★ 서비스가 죽으면 1초 후 재시작 예약
        scheduleRestart()

        scope.cancel()
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    /**
     * AlarmManager를 통해 1초 후 서비스 재시작.
     * START_STICKY만으로는 task 제거 시 재시작이 보장되지 않으므로
     * AlarmManager를 백업으로 사용.
     */
    private fun scheduleRestart() {
        try {
            val restartIntent = Intent(this, ScheduleMonitorService::class.java)
            val pendingIntent = PendingIntent.getForegroundService(
                this, RESTART_REQUEST_CODE, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                pendingIntent
            )
            Log.i(TAG, "Restart scheduled via AlarmManager in 1 second")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule restart", e)
        }
    }

    companion object {
        const val TAG = "ScheduleMonitor"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 2000
        const val CHECK_INTERVAL_MS = 30_000L
        const val RESTART_REQUEST_CODE = 9999

        const val ACTION_PAUSE = "com.jw.autorecord.PAUSE"
        const val ACTION_RESUME = "com.jw.autorecord.RESUME"
        const val ACTION_STOP = "com.jw.autorecord.STOP"
        const val ACTION_MANUAL_START = "com.jw.autorecord.MANUAL_START"

        fun start(context: Context) {
            val intent = Intent(context, ScheduleMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, ScheduleMonitorService::class.java).apply {
                this.action = action
            }
            context.startService(intent)
        }

        /** 수동 녹음 시작 */
        fun startManualRecording(context: Context, period: Int, subject: String, teacher: String) {
            val intent = Intent(context, ScheduleMonitorService::class.java).apply {
                action = ACTION_MANUAL_START
                putExtra("period", period)
                putExtra("subject", subject)
                putExtra("teacher", teacher)
            }
            context.startService(intent)
        }
    }
}
