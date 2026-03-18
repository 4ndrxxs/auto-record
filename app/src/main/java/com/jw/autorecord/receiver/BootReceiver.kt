package com.jw.autorecord.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jw.autorecord.data.AppDatabase
import com.jw.autorecord.service.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i("BootReceiver", "Boot completed, rescheduling alarms")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val allSchedules = mutableListOf<com.jw.autorecord.data.Schedule>()
                for (day in 1..5) {
                    allSchedules.addAll(db.scheduleDao().getSchedulesByDayOnce(day))
                }
                AlarmScheduler.scheduleAll(context, allSchedules)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
