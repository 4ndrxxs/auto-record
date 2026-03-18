package com.jw.autorecord.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jw.autorecord.AutoRecordApp
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.service.AlarmScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as AutoRecordApp).database.scheduleDao()

    private val _selectedDay = MutableStateFlow(1)
    val selectedDay: StateFlow<Int> = _selectedDay

    val schedules: StateFlow<List<Schedule>> = _selectedDay
        .flatMapLatest { day -> dao.getSchedulesByDay(day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDay(day: Int) {
        _selectedDay.value = day
    }

    fun saveSchedule(dayOfWeek: Int, period: Int, startTime: String, subject: String, teacher: String) {
        viewModelScope.launch {
            val existing = dao.getSchedule(dayOfWeek, period)
            dao.upsert(
                Schedule(
                    id = existing?.id ?: 0,
                    dayOfWeek = dayOfWeek,
                    period = period,
                    startTime = startTime,
                    subject = subject,
                    teacher = teacher
                )
            )
            rescheduleAlarms()
        }
    }

    fun deleteSchedule(dayOfWeek: Int, period: Int) {
        viewModelScope.launch {
            dao.deleteByDayAndPeriod(dayOfWeek, period)
            rescheduleAlarms()
        }
    }

    private suspend fun rescheduleAlarms() {
        val all = mutableListOf<Schedule>()
        for (day in 1..5) {
            all.addAll(dao.getSchedulesByDayOnce(day))
        }
        AlarmScheduler.scheduleAll(getApplication(), all)
    }
}
