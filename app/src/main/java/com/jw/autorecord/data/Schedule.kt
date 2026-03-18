package com.jw.autorecord.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dayOfWeek: Int,   // 1=월 2=화 3=수 4=목 5=금
    val period: Int,      // 1~7교시
    val startTime: String, // "08:30"
    val subject: String,
    val teacher: String
) {
    val startHour: Int get() = try {
        startTime.split(":").getOrNull(0)?.toIntOrNull() ?: 0
    } catch (_: Exception) { 0 }

    val startMinute: Int get() = try {
        startTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
    } catch (_: Exception) { 0 }

    fun toFileName(dateStr: String): String {
        val safeSubject = subject.replace(Regex("[/\\\\:*?\"<>|\\x00]"), "_")
        val safeTeacher = teacher.replace(Regex("[/\\\\:*?\"<>|\\x00]"), "_")
        return "${dateStr}_${period}교시_${safeSubject}_${safeTeacher}.m4a"
    }

    companion object {
        fun dayName(dayOfWeek: Int): String = when (dayOfWeek) {
            1 -> "월"
            2 -> "화"
            3 -> "수"
            4 -> "목"
            5 -> "금"
            else -> "?"
        }
    }
}
