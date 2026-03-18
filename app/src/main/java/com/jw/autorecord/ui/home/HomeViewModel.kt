package com.jw.autorecord.ui.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jw.autorecord.AutoRecordApp
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.service.AlarmScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as AutoRecordApp).database.scheduleDao()

    private val todayDayOfWeek: Int
        get() {
            val cal = Calendar.getInstance()
            return AlarmScheduler.calendarDayToOurDay(cal.get(Calendar.DAY_OF_WEEK))
        }

    val todaySchedules: StateFlow<List<Schedule>> = dao.getSchedulesByDay(todayDayOfWeek)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _masterEnabled = MutableStateFlow(
        app.getSharedPreferences("prefs", 0).getBoolean("master_enabled", true)
    )
    val masterEnabled: StateFlow<Boolean> = _masterEnabled

    private val _recordingPeriod = MutableStateFlow(-1)
    val recordingPeriod: StateFlow<Int> = _recordingPeriod

    fun setMasterEnabled(enabled: Boolean) {
        _masterEnabled.value = enabled
        getApplication<AutoRecordApp>()
            .getSharedPreferences("prefs", 0)
            .edit().putBoolean("master_enabled", enabled).apply()

        if (enabled) {
            rescheduleToday()
        }
    }

    fun setRecordingPeriod(period: Int) {
        _recordingPeriod.value = period
    }

    private fun rescheduleToday() {
        viewModelScope.launch {
            val schedules = dao.getSchedulesByDayOnce(todayDayOfWeek)
            AlarmScheduler.scheduleTodayAlarms(getApplication(), schedules)
        }
    }

    fun getRecordedFiles(): List<String> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val todayDir = File(
            Environment.getExternalStorageDirectory(),
            "AutoRecord/${dateFormat.format(Date())}"
        )
        return if (todayDir.exists()) {
            todayDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
        } else emptyList()
    }
}
