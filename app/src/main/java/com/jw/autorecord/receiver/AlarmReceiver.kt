package com.jw.autorecord.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jw.autorecord.service.RecordingService

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: return
        val teacher = intent.getStringExtra(EXTRA_TEACHER) ?: return
        val period = intent.getIntExtra(EXTRA_PERIOD, 0)

        Log.i("AlarmReceiver", "Alarm fired: period=$period subject=$subject")

        val serviceIntent = Intent(context, RecordingService::class.java).apply {
            putExtra(RecordingService.EXTRA_SUBJECT, subject)
            putExtra(RecordingService.EXTRA_TEACHER, teacher)
            putExtra(RecordingService.EXTRA_PERIOD, period)
            putExtra(RecordingService.EXTRA_DURATION_MIN, 50)
        }

        context.startForegroundService(serviceIntent)
    }

    companion object {
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_TEACHER = "extra_teacher"
        const val EXTRA_PERIOD = "extra_period"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
    }
}
