package com.jw.autorecord.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jw.autorecord.AutoRecordApp
import com.jw.autorecord.MainActivity
import com.jw.autorecord.R
import com.jw.autorecord.data.AppDatabase
import com.jw.autorecord.service.AlarmScheduler
import com.jw.autorecord.service.RecordingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: return
        val teacher = intent.getStringExtra(EXTRA_TEACHER) ?: return
        val period = intent.getIntExtra(EXTRA_PERIOD, 0)
        val dayOfWeek = intent.getIntExtra(EXTRA_DAY_OF_WEEK, 0)

        Log.i("AlarmReceiver", "Alarm fired: period=$period subject=$subject")

        // 1) 푸시 알림 보내기 (조용한 알림)
        sendNotification(context, subject, teacher, period)

        // 2) 녹음 시작
        val serviceIntent = Intent(context, RecordingService::class.java).apply {
            putExtra(RecordingService.EXTRA_SUBJECT, subject)
            putExtra(RecordingService.EXTRA_TEACHER, teacher)
            putExtra(RecordingService.EXTRA_PERIOD, period)
            putExtra(RecordingService.EXTRA_DURATION_MIN, 50)
        }
        context.startForegroundService(serviceIntent)

        // 3) 다음 주 같은 시간에 알람 다시 등록
        rescheduleNextWeek(context, dayOfWeek)
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
            Log.w("AlarmReceiver", "Notification permission not granted", e)
        }
    }

    private fun rescheduleNextWeek(context: Context, dayOfWeek: Int) {
        if (dayOfWeek == 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val schedules = db.scheduleDao().getSchedulesByDayOnce(dayOfWeek)
                AlarmScheduler.scheduleAll(context, schedules)
                Log.i("AlarmReceiver", "Rescheduled ${schedules.size} alarms for dayOfWeek=$dayOfWeek")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_TEACHER = "extra_teacher"
        const val EXTRA_PERIOD = "extra_period"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
        const val ALERT_NOTIFICATION_ID = 2000
    }
}
