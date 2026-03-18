package com.jw.autorecord.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jw.autorecord.AutoRecordApp
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.service.AlarmScheduler
import com.jw.autorecord.service.RecordingState
import com.jw.autorecord.util.StoragePaths
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    /** 실시간 녹음 상태 (RecordingService가 매초 업데이트) */
    val recordingState: StateFlow<RecordingState.State> = RecordingState.state

    /** 오늘 녹음된 파일 목록 (주기적으로 갱신) */
    private val _recordedFiles = MutableStateFlow<List<String>>(emptyList())
    val recordedFiles: StateFlow<List<String>> = _recordedFiles.asStateFlow()

    init {
        refreshRecordedFiles()
        // 녹음 상태가 변할 때마다 파일 목록도 갱신
        viewModelScope.launch {
            RecordingState.state.collect { state ->
                if (!state.isRecording && state.period == -1) {
                    // 녹음이 끝났을 때 파일 목록 새로고침
                    refreshRecordedFiles()
                }
            }
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        _masterEnabled.value = enabled
        getApplication<AutoRecordApp>()
            .getSharedPreferences("prefs", 0)
            .edit().putBoolean("master_enabled", enabled).apply()

        if (enabled) {
            rescheduleToday()
        }
    }

    fun refreshRecordedFiles() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val todayDir = StoragePaths.getDateDir(getApplication(), dateFormat.format(Date()))
        _recordedFiles.value = if (todayDir.exists()) {
            todayDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
        } else emptyList()
    }

    private fun rescheduleToday() {
        viewModelScope.launch {
            val schedules = dao.getSchedulesByDayOnce(todayDayOfWeek)
            AlarmScheduler.scheduleTodayAlarms(getApplication(), schedules)
        }
    }
}
