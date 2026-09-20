package com.example.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.demo.data.local.entity.PeriodRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 经期记录 DAO。
 *
 * 查询分两类：
 * - 返回 [Flow] 的：给 UI 订阅，数据一变界面自动刷新（相当于后端的 CDC/消息订阅）
 * - suspend 一次性查询：给算法、Repository 内部逻辑用
 */
@Dao
interface PeriodRecordDao {

    /**
     * 查询与 [from, to] 区间有重叠的经期记录，供日历渲染。
     *
     * COALESCE(end_date, start_date) 的作用：结束日为空（经期进行中/未回填）时，
     * 把该记录的覆盖范围退化成「仅开始日那一天」，
     * 否则一条很久以前开始、end_date 为空的记录会污染之后所有月份的渲染。
     */
    @Query(
        """
        SELECT * FROM period_record
        WHERE start_date <= :to AND COALESCE(end_date, start_date) >= :from
        ORDER BY start_date ASC
        """
    )
    fun observeOverlapping(from: LocalDate, to: LocalDate): Flow<List<PeriodRecord>>

    /** 全部记录，按开始日升序。预测算法的输入 */
    @Query("SELECT * FROM period_record ORDER BY start_date ASC")
    fun observeAllAscending(): Flow<List<PeriodRecord>>

    /** 同上，一次性查询版本，供算法/测试使用 */
    @Query("SELECT * FROM period_record ORDER BY start_date ASC")
    suspend fun getAllAscending(): List<PeriodRecord>

    /** 全部记录，按开始日降序。历史列表页用 */
    @Query("SELECT * FROM period_record ORDER BY start_date DESC")
    fun observeAllDescending(): Flow<List<PeriodRecord>>

    @Query("SELECT * FROM period_record WHERE start_date = :date LIMIT 1")
    fun observeByStartDate(date: LocalDate): Flow<PeriodRecord?>

    @Query("SELECT * FROM period_record WHERE start_date = :date LIMIT 1")
    suspend fun getByStartDate(date: LocalDate): PeriodRecord?

    @Query("SELECT * FROM period_record WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PeriodRecord?

    /** 记录总数，用于决定预测算法走哪个 Level */
    @Query("SELECT COUNT(*) FROM period_record")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: PeriodRecord): Long

    @Update
    suspend fun update(record: PeriodRecord): Int

    @Delete
    suspend fun delete(record: PeriodRecord): Int

    @Query("DELETE FROM period_record WHERE start_date = :date")
    suspend fun deleteByStartDate(date: LocalDate): Int

    @Query("DELETE FROM period_record WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /**
     * 区间融合写入：先删被合并的旧行，再写入融合结果，单事务保证「删+写」原子。
     *
     * 为什么融合不在 Repository 里顺序调 delete/update：两步之间若进程被杀，
     * 会留下「旧行已删、新行未写」的丢数据窗口；@Transaction 把两步合成一个
     * 数据库事务（类比后端 Service 方法上的 @Transactional）。
     *
     * @param deleteIds 被融合吞掉的旧行 id
     * @param merged 融合结果；id==0 表示开始日不与任何旧行重合、走 insert，
     *   否则 update 该 id（开始日 UNIQUE 索引要求融合行复用最小开始日那行的 id）
     */
    @Transaction
    suspend fun mergePeriods(deleteIds: List<Long>, merged: PeriodRecord): Long {
        deleteIds.forEach { deleteById(it) }
        return if (merged.id == 0L) insert(merged) else update(merged).toLong()
    }
}
