package com.example.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.demo.data.local.entity.WorkLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WorkLogDao {

    /** 某天的工作日志（日历页当日面板 + 工作页日视图） */
    @Query(
        """
        SELECT * FROM work_log
        WHERE work_date = :date
        ORDER BY created_at DESC
        """
    )
    fun observeByDate(date: LocalDate): Flow<List<WorkLog>>

    /** 区间内的工作日志，按日期倒序（最近的日期在上面），工作页列表用 */
    @Query(
        """
        SELECT * FROM work_log
        WHERE work_date BETWEEN :from AND :to
        ORDER BY work_date DESC, created_at DESC
        """
    )
    fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<WorkLog>>

    /** 某天总耗时（分钟）。COALESCE 保证无记录时返回 0 而不是 null */
    @Query("SELECT COALESCE(SUM(duration_minutes), 0) FROM work_log WHERE work_date = :date")
    fun observeTotalMinutesByDate(date: LocalDate): Flow<Int>

    /** 区间总耗时，用于"本周工时"统计 */
    @Query(
        """
        SELECT COALESCE(SUM(duration_minutes), 0) FROM work_log
        WHERE work_date BETWEEN :from AND :to
        """
    )
    fun observeTotalMinutesByRange(from: LocalDate, to: LocalDate): Flow<Int>

    @Query("SELECT * FROM work_log WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WorkLog?

    @Insert
    suspend fun insert(workLog: WorkLog): Long

    @Update
    suspend fun update(workLog: WorkLog): Int

    @Delete
    suspend fun delete(workLog: WorkLog): Int

    /**
     * 给某条日志追加耗时。番茄钟完成后调用，
     * 用增量 UPDATE 而不是"读出来改完再写回"，避免并发覆盖。
     */
    @Query("UPDATE work_log SET duration_minutes = duration_minutes + :minutes WHERE id = :id")
    suspend fun addDuration(id: Long, minutes: Int)
}
