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
    val startHour: Int get() = startTime.split(":")[0].toInt()
    val startMinute: Int get() = startTime.split(":")[1].toInt()

    fun toFileName(dateStr: String): String {
        return "${dateStr}_${period}교시_${subject}_${teacher}.m4a"
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
