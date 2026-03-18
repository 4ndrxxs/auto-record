package com.jw.autorecord.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jw.autorecord.AutoRecordApp
import com.jw.autorecord.MainActivity
import com.jw.autorecord.service.AlarmScheduler
import com.jw.autorecord.service.RecordingService
import java.util.*

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: return
        val teacher = intent.getStringExtra(EXTRA_TEACHER) ?: return
        val period = intent.getIntExtra(EXTRA_PERIOD, 0)
        val dayOfWeek = intent.getIntExtra(EXTRA_DAY_OF_WEEK, 0)
        val startTime = intent.getStringExtra(EXTRA_START_TIME) ?: ""

        Log.i(TAG, "Alarm fired: period=$period subject=$subject dayOfWeek=$dayOfWeek")

        // ★ WakeLock 획득 — CPU가 서비스 시작 전에 다시 잠드는 것을 방지
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoRecord::AlarmReceiverWakeLock"
        )
        wakeLock.acquire(60_000L) // 최대 60초 (서비스 시작하면 서비스가 자체 WakeLock 관리)

        try {
            // 이 알람만 다음 주에 재등록 (다른 알람 건드리지 않음)
            rescheduleThisAlarmNextWeek(context, dayOfWeek, period, subject, teacher, startTime)

            // 마스터 토글 확인
            val prefs = context.getSharedPreferences("prefs", 0)
            if (!prefs.getBoolean("master_enabled", true)) {
                Log.i(TAG, "Master toggle OFF, skipping recording")
                return
            }

            // 1) 푸시 알림 보내기
            sendNotification(context, subject, teacher, period)

            // 2) 녹음 서비스 시작
            val serviceIntent = Intent(context, RecordingService::class.java).apply {
                putExtra(RecordingService.EXTRA_SUBJECT, subject)
                putExtra(RecordingService.EXTRA_TEACHER, teacher)
                putExtra(RecordingService.EXTRA_PERIOD, period)
                putExtra(RecordingService.EXTRA_DURATION_MIN, 50)
            }
            context.startForegroundService(serviceIntent)

        } finally {
            // 서비스가 시작되면 WakeLock 해제 (5초 후 안전하게)
            try {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock release failed", e)
            }
        }
    }

    private fun sendNotification(context: Context, subject: String, teacher: String, period: Int) {
        val tapIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AutoRecordApp.ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("녹음 시작")
            .setContentText("${period}교시 ${subject} (${teacher}) 녹음을 시작합니다")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID + period, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted", e)
        }
    }

    /**
     * 이 알람 1개만 정확히 7일 뒤에 setAlarmClock()으로 재등록.
     * 다른 교시의 알람은 절대 건드리지 않음 (TOCTOU 방지).
     */
    private fun rescheduleThisAlarmNextWeek(
        context: Context,
        dayOfWeek: Int,
        period: Int,
        subject: String,
        teacher: String,
        startTime: String
    ) {
        if (dayOfWeek == 0) return

        val alarmManager = context.getSystemService(AlarmManager::class.java)

        // startTime에서 시/분 파싱
        val parts = startTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return

        // 정확히 7일 뒤 같은 시간
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        AlarmScheduler.scheduleOneAlarm(
            context, alarmManager, cal.timeInMillis,
            dayOfWeek, period, subject, teacher, startTime
        )

        Log.i(TAG, "Rescheduled period=$period for next week at ${Date(cal.timeInMillis)}")
    }

    companion object {
        const val TAG = "AlarmReceiver"
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_TEACHER = "extra_teacher"
        const val EXTRA_PERIOD = "extra_period"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
        const val EXTRA_START_TIME = "extra_start_time"
        const val ALERT_NOTIFICATION_ID = 2000
    }
}
