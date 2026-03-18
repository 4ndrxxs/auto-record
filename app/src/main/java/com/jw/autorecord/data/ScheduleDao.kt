package com.jw.autorecord.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE dayOfWeek = :day ORDER BY period")
    fun getSchedulesByDay(day: Int): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE dayOfWeek = :day ORDER BY period")
    suspend fun getSchedulesByDayOnce(day: Int): List<Schedule>

    @Query("SELECT * FROM schedules ORDER BY dayOfWeek, period")
    fun getAllSchedules(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules ORDER BY dayOfWeek, period")
    suspend fun getAllSchedulesOnce(): List<Schedule>

    @Query("SELECT * FROM schedules WHERE dayOfWeek = :day AND period = :period LIMIT 1")
    suspend fun getSchedule(day: Int, period: Int): Schedule?

    @Upsert
    suspend fun upsert(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)

    @Query("DELETE FROM schedules WHERE dayOfWeek = :day AND period = :period")
    suspend fun deleteByDayAndPeriod(day: Int, period: Int)
}
