package com.jw.autorecord.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleOverrideDao {

    /** 특정 날짜의 모든 override 조회 (UI용 Flow) */
    @Query("SELECT * FROM schedule_overrides WHERE date = :date ORDER BY period")
    fun getOverridesByDate(date: String): Flow<List<ScheduleOverride>>

    /** 특정 날짜의 모든 override 조회 (서비스용 일회성) */
    @Query("SELECT * FROM schedule_overrides WHERE date = :date ORDER BY period")
    suspend fun getOverridesByDateOnce(date: String): List<ScheduleOverride>

    /** 특정 날짜+교시 override 조회 */
    @Query("SELECT * FROM schedule_overrides WHERE date = :date AND period = :period LIMIT 1")
    suspend fun getOverride(date: String, period: Int): ScheduleOverride?

    /** Override가 있는 날짜 목록 (달력에서 표시용) */
    @Query("SELECT DISTINCT date FROM schedule_overrides")
    fun getOverrideDates(): Flow<List<String>>

    /** 특정 날짜에 override가 있는지 확인 */
    @Query("SELECT COUNT(*) FROM schedule_overrides WHERE date = :date")
    suspend fun countByDate(date: String): Int

    @Upsert
    suspend fun upsert(override: ScheduleOverride)

    @Delete
    suspend fun delete(override: ScheduleOverride)

    @Query("DELETE FROM schedule_overrides WHERE date = :date AND period = :period")
    suspend fun deleteByDateAndPeriod(date: String, period: Int)

    /** 특정 날짜의 모든 override 삭제 */
    @Query("DELETE FROM schedule_overrides WHERE date = :date")
    suspend fun deleteAllByDate(date: String)

    /** 모든 override 조회 (백업용) */
    @Query("SELECT * FROM schedule_overrides ORDER BY date, period")
    suspend fun getAllOverridesOnce(): List<ScheduleOverride>

    /** 지난 override 정리 (date < 기준일) */
    @Query("DELETE FROM schedule_overrides WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}
