package com.jw.autorecord.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 특정 날짜의 시간표 변경(Override).
 *
 * 조회 우선순위: override > 기본 시간표
 * - CHANGE: 과목/선생님/시간 변경
 * - CANCEL: 해당 교시 수업 취소 (녹음 스킵)
 * - ADD:    기본 시간표에 없는 교시 추가
 */
@Entity(tableName = "schedule_overrides")
data class ScheduleOverride(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,       // "2026-03-20" 형식
    val period: Int,        // 교시 (1~7)
    val type: String,       // "CHANGE", "CANCEL", "ADD"
    val subject: String = "",
    val teacher: String = "",
    val startTime: String = ""  // "HH:mm" — 빈 문자열이면 기본 시간표의 시간 사용
) {
    val startHour: Int get() = try {
        startTime.split(":").getOrNull(0)?.toIntOrNull() ?: 0
    } catch (_: Exception) { 0 }

    val startMinute: Int get() = try {
        startTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
    } catch (_: Exception) { 0 }

    /**
     * Override를 Schedule 객체로 변환 (서비스에서 통일된 처리를 위해)
     */
    fun toSchedule(dayOfWeek: Int, fallbackStartTime: String = ""): Schedule {
        return Schedule(
            dayOfWeek = dayOfWeek,
            period = period,
            startTime = if (startTime.isNotBlank()) startTime else fallbackStartTime,
            subject = subject,
            teacher = teacher
        )
    }

    companion object {
        const val TYPE_CHANGE = "CHANGE"
        const val TYPE_CANCEL = "CANCEL"
        const val TYPE_ADD = "ADD"
    }
}
