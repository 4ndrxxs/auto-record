package com.jw.autorecord.service

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jw.autorecord.AutoRecordApp
import com.jw.autorecord.MainActivity
import com.jw.autorecord.data.AppDatabase
import com.jw.autorecord.data.Schedule
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
        Log.i(TAG, "ScheduleMonitorService started")

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
                val schedules = db.scheduleDao().getSchedulesByDayOnce(dayOfWeek)

                for (schedule in schedules) {
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
        val durationMin = 50

        val baseDir = StoragePaths.getDateDir(this, dateDirStr)
        val safeSubject = StoragePaths.sanitizeFileName(schedule.subject)
        val safeTeacher = StoragePaths.sanitizeFileName(schedule.teacher)
        val fileName = "${dateStr}_${schedule.period}교시_${safeSubject}_${safeTeacher}.m4a"
        outputFile = File(baseDir, fileName)

        try {
            mediaRecorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
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
                    val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
                    val fileSize = outputFile?.length() ?: 0L
                    val amplitude = try {
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

        val notification = NotificationCompat.Builder(this, AutoRecordApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
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

    override fun onDestroy() {
        Log.w(TAG, "ScheduleMonitorService destroyed — will be restarted by START_STICKY")
        handler.removeCallbacks(checkRunnable)

        // 녹음 중이었다면 정리
        stopRecording()

        scope.cancel()
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val TAG = "ScheduleMonitor"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 2000
        const val CHECK_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, ScheduleMonitorService::class.java)
            context.startForegroundService(intent)
        }
    }
}
