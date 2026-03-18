package com.jw.autorecord.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.receiver.AlarmReceiver
import java.util.*

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    fun scheduleAll(context: Context, schedules: List<Schedule>) {
        cancelAll(context, schedules)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()

        for (schedule in schedules) {
            val triggerTime = getNextTriggerTime(schedule)
            if (triggerTime <= now) continue

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_SUBJECT, schedule.subject)
                putExtra(AlarmReceiver.EXTRA_TEACHER, schedule.teacher)
                putExtra(AlarmReceiver.EXTRA_PERIOD, schedule.period)
                putExtra(AlarmReceiver.EXTRA_DAY_OF_WEEK, schedule.dayOfWeek)
            }

            val requestCode = schedule.dayOfWeek * 100 + schedule.period
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )

            Log.i(TAG, "Alarm set: day=${schedule.dayOfWeek} period=${schedule.period} " +
                    "subject=${schedule.subject} at ${Date(triggerTime)}")
        }
    }

    fun scheduleTodayAlarms(context: Context, schedules: List<Schedule>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val today = Calendar.getInstance()

        val todayDayOfWeek = calendarDayToOurDay(today.get(Calendar.DAY_OF_WEEK))

        for (schedule in schedules.filter { it.dayOfWeek == todayDayOfWeek }) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, schedule.startHour)
                set(Calendar.MINUTE, schedule.startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (cal.timeInMillis <= now) continue

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_SUBJECT, schedule.subject)
                putExtra(AlarmReceiver.EXTRA_TEACHER, schedule.teacher)
                putExtra(AlarmReceiver.EXTRA_PERIOD, schedule.period)
                putExtra(AlarmReceiver.EXTRA_DAY_OF_WEEK, schedule.dayOfWeek)
            }

            val requestCode = schedule.dayOfWeek * 100 + schedule.period
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                pendingIntent
            )

            Log.i(TAG, "Today alarm set: period=${schedule.period} ${schedule.subject} at ${Date(cal.timeInMillis)}")
        }
    }

    private fun getNextTriggerTime(schedule: Schedule): Long {
        val cal = Calendar.getInstance()
        val targetDay = ourDayToCalendarDay(schedule.dayOfWeek)

        cal.set(Calendar.HOUR_OF_DAY, schedule.startHour)
        cal.set(Calendar.MINUTE, schedule.startMinute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        var daysUntil = targetDay - currentDay
        if (daysUntil < 0) daysUntil += 7
        if (daysUntil == 0 && cal.timeInMillis <= System.currentTimeMillis()) {
            daysUntil = 7
        }

        cal.add(Calendar.DAY_OF_YEAR, daysUntil)
        return cal.timeInMillis
    }

    private fun cancelAll(context: Context, schedules: List<Schedule>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (schedule in schedules) {
            val requestCode = schedule.dayOfWeek * 100 + schedule.period
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }
    }

    fun calendarDayToOurDay(calDay: Int): Int = when (calDay) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        else -> 0
    }

    private fun ourDayToCalendarDay(ourDay: Int): Int = when (ourDay) {
        1 -> Calendar.MONDAY
        2 -> Calendar.TUESDAY
        3 -> Calendar.WEDNESDAY
        4 -> Calendar.THURSDAY
        5 -> Calendar.FRIDAY
        else -> Calendar.MONDAY
    }
}
