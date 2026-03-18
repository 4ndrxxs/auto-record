package com.jw.autorecord.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jw.autorecord.AutoRecordApp
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.data.ScheduleOverride
import com.jw.autorecord.service.AlarmScheduler
import com.jw.autorecord.service.RecordingState
import com.jw.autorecord.util.StoragePaths
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as AutoRecordApp).database
    private val dao = db.scheduleDao()
    private val overrideDao = db.scheduleOverrideDao()

    private val todayDayOfWeek: Int
        get() {
            val cal = Calendar.getInstance()
            return AlarmScheduler.calendarDayToOurDay(cal.get(Calendar.DAY_OF_WEEK))
        }

    private val todayDateStr: String
        get() {
            val cal = Calendar.getInstance()
            return "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

    /** ★ Override가 반영된 오늘의 최종 시간표 */
    val todaySchedules: StateFlow<List<Schedule>> = combine(
        dao.getSchedulesByDay(todayDayOfWeek),
        overrideDao.getOverridesByDate(todayDateStr)
    ) { baseSchedules, overrides ->
        buildEffectiveSchedules(baseSchedules, overrides)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 오늘 override가 있는지 여부 (홈 화면에 뱃지 표시용) */
    val hasOverridesToday: StateFlow<Boolean> = overrideDao.getOverridesByDate(todayDateStr)
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
                    refreshRecordedFiles()
                }
            }
        }
        // 앱 시작 시 오래된 override 정리 (7일 이전)
        viewModelScope.launch {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
            val cutoff = "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            overrideDao.deleteOlderThan(cutoff)
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

    /**
     * 기본 시간표 + override를 병합하여 최종 시간표 생성
     */
    private fun buildEffectiveSchedules(
        baseSchedules: List<Schedule>,
        overrides: List<ScheduleOverride>
    ): List<Schedule> {
        val overrideMap = overrides.associateBy { it.period }
        val result = mutableListOf<Schedule>()

        for (base in baseSchedules) {
            val override = overrideMap[base.period]
            when {
                override == null -> result.add(base)
                override.type == ScheduleOverride.TYPE_CANCEL -> { /* 스킵 */ }
                else -> result.add(override.toSchedule(base.dayOfWeek, base.startTime))
            }
        }

        // ADD: 기본 시간표에 없는 교시 추가
        val existingPeriods = baseSchedules.map { it.period }.toSet()
        for (override in overrides) {
            if (override.type == ScheduleOverride.TYPE_ADD && override.period !in existingPeriods) {
                result.add(override.toSchedule(todayDayOfWeek))
            }
        }

        return result.sortedBy { it.period }
    }
}
