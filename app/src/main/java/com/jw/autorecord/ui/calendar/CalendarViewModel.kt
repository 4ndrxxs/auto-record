package com.jw.autorecord.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jw.autorecord.AutoRecordApp
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.data.ScheduleOverride
import com.jw.autorecord.service.AlarmScheduler
import com.jw.autorecord.util.StoragePaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val baseDir = StoragePaths.getBaseDir(app)
    private val db = (app as AutoRecordApp).database
    private val scheduleDao = db.scheduleDao()
    private val overrideDao = db.scheduleOverrideDao()

    // Pair(year, month) - 1-indexed month
    private val _currentMonth = MutableStateFlow(
        Calendar.getInstance().let { it.get(Calendar.YEAR) to (it.get(Calendar.MONTH) + 1) }
    )
    val currentMonth: StateFlow<Pair<Int, Int>> = _currentMonth

    // 녹음이 있는 날짜 Set ("2026-03-18" 형식)
    private val _recordedDates = MutableStateFlow<Set<String>>(emptySet())
    val recordedDates: StateFlow<Set<String>> = _recordedDates

    // Override가 있는 날짜 Set
    val overrideDates: StateFlow<List<String>> = overrideDao.getOverrideDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 선택된 날짜 Triple(year, month, day)
    private val _selectedDate = MutableStateFlow<Triple<Int, Int, Int>?>(null)
    val selectedDate: StateFlow<Triple<Int, Int, Int>?> = _selectedDate

    // 선택된 날짜의 파일 목록
    private val _selectedDateFiles = MutableStateFlow<List<String>>(emptyList())
    val selectedDateFiles: StateFlow<List<String>> = _selectedDateFiles

    // ★ Override 편집 시트용 상태
    private val _editingDate = MutableStateFlow<String?>(null)
    val editingDate: StateFlow<String?> = _editingDate

    private val _baseSchedulesForEdit = MutableStateFlow<List<Schedule>>(emptyList())
    val baseSchedulesForEdit: StateFlow<List<Schedule>> = _baseSchedulesForEdit

    private val _overridesForEdit = MutableStateFlow<List<ScheduleOverride>>(emptyList())
    val overridesForEdit: StateFlow<List<ScheduleOverride>> = _overridesForEdit

    init {
        loadRecordedDates()
    }

    fun loadRecordedDates() {
        val dates = baseDir.listFiles()
            ?.filter { it.isDirectory && (it.listFiles()?.any { f -> f.extension == "m4a" } == true) }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
        _recordedDates.value = dates
    }

    fun previousMonth() {
        val (year, month) = _currentMonth.value
        _currentMonth.value = if (month == 1) (year - 1) to 12 else year to (month - 1)
        _selectedDate.value = null
        _selectedDateFiles.value = emptyList()
    }

    fun nextMonth() {
        val (year, month) = _currentMonth.value
        _currentMonth.value = if (month == 12) (year + 1) to 1 else year to (month + 1)
        _selectedDate.value = null
        _selectedDateFiles.value = emptyList()
    }

    fun selectDate(date: Triple<Int, Int, Int>) {
        _selectedDate.value = date
        val dateStr = "%04d-%02d-%02d".format(date.first, date.second, date.third)
        val dir = java.io.File(baseDir, dateStr)
        _selectedDateFiles.value = if (dir.exists()) {
            dir.listFiles()
                ?.filter { it.extension == "m4a" }
                ?.map { it.nameWithoutExtension }
                ?.sorted()
                ?: emptyList()
        } else emptyList()
    }

    /** ★ 날짜 길게 눌러서 Override 편집 시트 열기 */
    fun openOverrideEditor(date: Triple<Int, Int, Int>) {
        val dateStr = "%04d-%02d-%02d".format(date.first, date.second, date.third)

        // 해당 날짜의 요일 계산
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, date.first)
            set(Calendar.MONTH, date.second - 1)
            set(Calendar.DAY_OF_MONTH, date.third)
        }
        val dayOfWeek = AlarmScheduler.calendarDayToOurDay(cal.get(Calendar.DAY_OF_WEEK))

        viewModelScope.launch {
            // 기본 시간표 + 기존 override 로드
            _baseSchedulesForEdit.value = if (dayOfWeek > 0) {
                scheduleDao.getSchedulesByDayOnce(dayOfWeek)
            } else emptyList()

            _overridesForEdit.value = overrideDao.getOverridesByDateOnce(dateStr)
            _editingDate.value = dateStr
        }
    }

    fun closeOverrideEditor() {
        _editingDate.value = null
    }

    /** Override 저장 */
    fun saveOverride(override: ScheduleOverride) {
        viewModelScope.launch {
            // 같은 date+period의 기존 override가 있으면 삭제 후 insert
            overrideDao.deleteByDateAndPeriod(override.date, override.period)
            overrideDao.upsert(override)
            // 편집 시트 데이터 새로고침
            _overridesForEdit.value = overrideDao.getOverridesByDateOnce(override.date)
        }
    }

    /** Override 삭제 (기본 시간표로 복원) */
    fun deleteOverride(date: String, period: Int) {
        viewModelScope.launch {
            overrideDao.deleteByDateAndPeriod(date, period)
            _overridesForEdit.value = overrideDao.getOverridesByDateOnce(date)
        }
    }

    fun isToday(date: Triple<Int, Int, Int>): Boolean {
        val cal = Calendar.getInstance()
        return date.first == cal.get(Calendar.YEAR) &&
                date.second == cal.get(Calendar.MONTH) + 1 &&
                date.third == cal.get(Calendar.DAY_OF_MONTH)
    }

    /**
     * 해당 월의 달력 데이터 생성.
     * null = 빈 칸, Triple(year, month, day)
     */
    fun getMonthDays(year: Int, month: Int): List<Triple<Int, Int, Int>?> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=일, 1=월, ...
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val result = mutableListOf<Triple<Int, Int, Int>?>()

        // 앞쪽 빈 칸
        repeat(firstDayOfWeek) { result.add(null) }

        // 날짜
        for (day in 1..daysInMonth) {
            result.add(Triple(year, month, day))
        }

        // 뒤쪽 빈 칸 (7의 배수로 맞추기)
        while (result.size % 7 != 0) {
            result.add(null)
        }

        return result
    }
}
