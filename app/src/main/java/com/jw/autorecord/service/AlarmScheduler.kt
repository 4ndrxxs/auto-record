package com.jw.autorecord.service

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jw.autorecord.MainActivity
import com.jw.autorecord.data.AppDatabase
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.receiver.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * setAlarmClock() 사용 — Android에서 절대 지연시키지 않는 유일한 알람 API.
 * Doze, 배터리 최적화, OEM 킬러 전부 무시.
 * 포그라운드 서비스 시작 권한도 10초간 자동 부여.
 */
object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    /**
     * 앱 시작 시 호출 — DB에서 모든 시간표를 읽어 알람 등록
     */
    fun registerAllOnStartup(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.getSharedPreferences("prefs", 0)
            if (!prefs.getBoolean("master_enabled", true)) {
                Log.i(TAG, "Master toggle OFF, skipping alarm registration")
                return@launch
            }

            val db = AppDatabase.getInstance(context)
            val all = mutableListOf<Schedule>()
            for (day in 1..5) {
                all.addAll(db.scheduleDao().getSchedulesByDayOnce(day))
            }
            if (all.isEmpty()) {
                Log.i(TAG, "No schedules found, skipping")
                return@launch
            }
            scheduleAll(context, all)
            Log.i(TAG, "Registered ${all.size} alarms on startup")
        }
    }

    fun scheduleAll(context: Context, schedules: List<Schedule>) {
        cancelAll(context, schedules)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()

        for (schedule in schedules) {
            val triggerTime = getNextTriggerTime(schedule)
            if (triggerTime <= now) continue

            scheduleOneAlarm(
                context, alarmManager, triggerTime,
                schedule.dayOfWeek, schedule.period,
                schedule.subject, schedule.teacher, schedule.startTime
            )
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

            scheduleOneAlarm(
                context, alarmManager, cal.timeInMillis,
                schedule.dayOfWeek, schedule.period,
                schedule.subject, schedule.teacher, schedule.startTime
            )
        }
    }

    /**
     * 단일 알람을 setAlarmClock()으로 등록.
     * setAlarmClock()은:
     * - Doze 모드에서 지연되지 않음
     * - 배터리 최적화 영향 없음
     * - FGS 시작 권한 자동 부여 (10초)
     * - 상태바에 알람 아이콘 표시
     */
    fun scheduleOneAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerTime: Long,
        dayOfWeek: Int,
        period: Int,
        subject: String,
        teacher: String,
        startTime: String
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_SUBJECT, subject)
            putExtra(AlarmReceiver.EXTRA_TEACHER, teacher)
            putExtra(AlarmReceiver.EXTRA_PERIOD, period)
            putExtra(AlarmReceiver.EXTRA_DAY_OF_WEEK, dayOfWeek)
            putExtra(AlarmReceiver.EXTRA_START_TIME, startTime)
        }

        val requestCode = dayOfWeek * 100 + period
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 알람 클릭 시 앱 열기용 PendingIntent
        val showIntent = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmClockInfo(triggerTime, showIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

        Log.i(TAG, "AlarmClock set: day=$dayOfWeek period=$period " +
                "subject=$subject at ${Date(triggerTime)}")
    }

    /**
     * Android 12+ 정확한 알람 권한 확인.
     * setAlarmClock()은 USE_EXACT_ALARM이 있으면 항상 허용됨.
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            return alarmManager.canScheduleExactAlarms()
        }
        return true
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
