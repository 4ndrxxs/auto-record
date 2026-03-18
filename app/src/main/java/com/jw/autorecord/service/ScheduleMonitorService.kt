package com.jw.autorecord.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import kotlinx.coroutines.*
import java.util.*

/**
 * 24시간 상시 실행되는 감시 서비스.
 * 30초마다 현재 시간이 시간표와 일치하는지 체크.
 * 일치하면 RecordingService를 시작.
 *
 * AlarmManager 대신 사용 — OEM 배터리 최적화에 영향받지 않음.
 * START_STICKY → 시스템이 죽여도 자동 재시작.
 */
class ScheduleMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    // 이미 녹음 트리거한 교시를 기록 (같은 교시 중복 시작 방지)
    // key = "dayOfWeek_period_dateStr" (예: "3_2_2026-03-18")
    private val triggeredToday = mutableSetOf<String>()

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

        // WakeLock 획득 — CPU가 슬립해도 서비스 유지
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoRecord::MonitorWakeLock"
        ).apply {
            acquire() // 무기한 (서비스가 살아있는 동안)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ScheduleMonitorService started")

        val notification = createMonitorNotification("자동녹음 대기 중")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                MONITOR_NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(MONITOR_NOTIFICATION_ID, notification)
        }

        // 날짜가 바뀌면 triggered 기록 초기화
        clearTriggeredIfNewDay()

        // 30초마다 체크 시작
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)

        // 시스템이 죽여도 자동 재시작
        return START_STICKY
    }

    private fun checkScheduleNow() {
        val prefs = getSharedPreferences("prefs", 0)
        if (!prefs.getBoolean("master_enabled", true)) return

        // 이미 녹음 중이면 스킵
        if (RecordingState.state.value.isRecording) return

        val now = Calendar.getInstance()
        val dayOfWeek = AlarmScheduler.calendarDayToOurDay(now.get(Calendar.DAY_OF_WEEK))
        if (dayOfWeek == 0) return // 주말

        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        val dateStr = "%04d-%02d-%02d".format(
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )

        // 날짜 바뀌면 초기화
        clearTriggeredIfNewDay()

        scope.launch {
            try {
                val db = AppDatabase.getInstance(this@ScheduleMonitorService)
                val schedules = db.scheduleDao().getSchedulesByDayOnce(dayOfWeek)

                for (schedule in schedules) {
                    val triggerKey = "${dayOfWeek}_${schedule.period}_$dateStr"

                    // 이미 이 교시 트리거했으면 스킵
                    if (triggerKey in triggeredToday) continue

                    // 시간 체크: 정확히 같은 시/분이거나, 1분 이내 지났을 때
                    if (isTimeToRecord(currentHour, currentMinute, schedule.startHour, schedule.startMinute)) {
                        triggeredToday.add(triggerKey)

                        Log.i(TAG, "★ Time matched! Starting recording: " +
                                "period=${schedule.period} subject=${schedule.subject} " +
                                "at $currentHour:$currentMinute (scheduled ${schedule.startTime})")

                        withContext(Dispatchers.Main) {
                            // 푸시 알림
                            sendRecordingAlert(schedule)

                            // 녹음 시작
                            val serviceIntent = Intent(
                                this@ScheduleMonitorService,
                                RecordingService::class.java
                            ).apply {
                                putExtra(RecordingService.EXTRA_SUBJECT, schedule.subject)
                                putExtra(RecordingService.EXTRA_TEACHER, schedule.teacher)
                                putExtra(RecordingService.EXTRA_PERIOD, schedule.period)
                                putExtra(RecordingService.EXTRA_DURATION_MIN, 50)
                            }
                            startForegroundService(serviceIntent)

                            // 모니터 알림 업데이트
                            updateMonitorNotification(
                                "${schedule.period}교시 ${schedule.subject} 녹음 시작됨"
                            )
                        }
                        break // 한번에 하나만 시작
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking schedule", e)
            }
        }
    }

    /**
     * 현재 시간이 예정 시간과 일치하는지 (1분 이내 허용)
     */
    private fun isTimeToRecord(
        currentHour: Int, currentMinute: Int,
        scheduleHour: Int, scheduleMinute: Int
    ): Boolean {
        val currentTotal = currentHour * 60 + currentMinute
        val scheduleTotal = scheduleHour * 60 + scheduleMinute
        val diff = currentTotal - scheduleTotal
        // 정확히 같거나 1분 지났을 때 (30초 체크 간격이므로 놓치지 않음)
        return diff in 0..1
    }

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

    private fun createMonitorNotification(text: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AutoRecordApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("자동녹음")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateMonitorNotification(text: String) {
        try {
            NotificationManagerCompat.from(this)
                .notify(MONITOR_NOTIFICATION_ID, createMonitorNotification(text))
        } catch (_: SecurityException) {}
    }

    private fun clearTriggeredIfNewDay() {
        val today = Calendar.getInstance()
        val dateStr = "%04d-%02d-%02d".format(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH) + 1,
            today.get(Calendar.DAY_OF_MONTH)
        )
        // 다른 날짜의 기록이 있으면 전부 지우기
        triggeredToday.removeAll { !it.endsWith(dateStr) }
    }

    override fun onDestroy() {
        Log.w(TAG, "ScheduleMonitorService destroyed — will be restarted by START_STICKY")
        handler.removeCallbacks(checkRunnable)
        scope.cancel()
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val TAG = "ScheduleMonitor"
        const val MONITOR_NOTIFICATION_ID = 1002
        const val ALERT_NOTIFICATION_ID = 2000
        const val CHECK_INTERVAL_MS = 30_000L // 30초

        fun start(context: Context) {
            val intent = Intent(context, ScheduleMonitorService::class.java)
            context.startForegroundService(intent)
        }
    }
}
