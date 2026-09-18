package com.example.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.demo.data.local.entity.PomodoroSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PomodoroSessionDao {

    /** 某天的番茄记录（专注页 + 日历当日面板） */
    @Query(
        """
        SELECT * FROM pomodoro_session
        WHERE work_date = :date
        ORDER BY start_time DESC
        """
    )
    fun observeByDate(date: LocalDate): Flow<List<PomodoroSession>>

    /** 区间内的番茄记录，统计页画本周柱状图用 */
    @Query(
        """
        SELECT * FROM pomodoro_session
        WHERE work_date BETWEEN :from AND :to
        ORDER BY work_date ASC, start_time ASC
        """
    )
    fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<PomodoroSession>>

    /** 某天完成的番茄数 */
    @Query("SELECT COUNT(*) FROM pomodoro_session WHERE work_date = :date AND is_completed = 1")
    fun observeCompletedCountByDate(date: LocalDate): Flow<Int>

    /** 某天累计专注分钟数（只算完整走完的番茄） */
    @Query(
        """
        SELECT COALESCE(SUM(actual_minutes), 0) FROM pomodoro_session
        WHERE work_date = :date AND is_completed = 1
        """
    )
    fun observeCompletedMinutesByDate(date: LocalDate): Flow<Int>

    /** 最近 N 条记录，专注页历史列表用 */
    @Query("SELECT * FROM pomodoro_session ORDER BY start_time DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PomodoroSession>>

    /** 全部记录（按开始时间升序），统计页算总体完成率用 */
    @Query("SELECT * FROM pomodoro_session ORDER BY start_time ASC")
    fun observeAll(): Flow<List<PomodoroSession>>

    /** 某个待办关联的番茄数，显示在待办列表项上 */
    @Query(
        """
        SELECT COUNT(*) FROM pomodoro_session
        WHERE link_type = :linkType AND link_id = :linkId AND is_completed = 1
        """
    )
    fun observeCompletedCountByLink(linkType: Int, linkId: Long): Flow<Int>

    /**
     * 某天里、某类关联对象按 link_id 分组的分钟合计，计划页「今日 x/y 分钟」用。
     * 聚合下推到 SQL（GROUP BY + SUM），UI 不必拉全量 session 再内存分组；
     * 一条查询覆盖所有计划，避免 N 个计划发 N 条查询。
     */
    @Query(
        """
        SELECT link_id AS linkId, COALESCE(SUM(actual_minutes), 0) AS minutes
        FROM pomodoro_session
        WHERE link_type = :linkType AND work_date = :date
        GROUP BY link_id
        """
    )
    fun observeMinutesGroupedByLinkOnDate(linkType: Int, date: LocalDate): Flow<List<LinkDayMinutes>>

    /**
     * 某个关联对象的**全历史**统计（计划详情用），P10-7。
     *
     * 五个指标一条 SQL 出：CASE WHEN 做条件聚合，等价于后端
     * `SUM(CASE WHEN ... THEN 1 ELSE 0 END)` 的经典写法，避免为了拿四个数发四条查询。
     * 口径与专注页/统计页一致——**时长与天数只算已完成的记录**：
     * 中途放弃的 session 虽然也有 actual_minutes，但不该算进「专注了多久」。
     * 放弃次数由调用方用 totalCount - completedCount 现算，不再单开一列。
     *
     * 聚合查询恒返回一行，所以 Flow 的泛型是 [LinkStats] 而非 List；
     * 无任何记录时各列为 0、lastEpochDay 为 NULL（COUNT/SUM 对空集的行为）。
     */
    @Query(
        """
        SELECT
            COUNT(*) AS totalCount,
            COALESCE(SUM(CASE WHEN is_completed = 1 THEN 1 ELSE 0 END), 0) AS completedCount,
            COALESCE(SUM(CASE WHEN is_completed = 1 THEN actual_minutes ELSE 0 END), 0) AS totalMinutes,
            COUNT(DISTINCT CASE WHEN is_completed = 1 THEN work_date END) AS activeDays,
            MAX(CASE WHEN is_completed = 1 THEN work_date END) AS lastEpochDay
        FROM pomodoro_session
        WHERE link_type = :linkType AND link_id = :linkId
        """
    )
    fun observeStatsByLink(linkType: Int, linkId: Long): Flow<LinkStats>

    @Insert
    suspend fun insert(session: PomodoroSession): Long

    @Update
    suspend fun update(session: PomodoroSession): Int

    @Delete
    suspend fun delete(session: PomodoroSession): Int

    /**
     * 解除与某个任务的关联。
     *
     * 删除 todo / work_log 时必须调用，否则番茄记录里会残留指向已删除行的 link_id，
     * 统计时按任务聚合就会算进幽灵数据。因为 link_id 是多态外键，
     * SQLite 的 ON DELETE SET NULL 帮不上忙，只能应用层显式处理。
     */
    @Query("UPDATE pomodoro_session SET link_type = 0, link_id = NULL WHERE link_type = :linkType AND link_id = :linkId")
    suspend fun unlink(linkType: Int, linkId: Long)
}

/** [PomodoroSessionDao.observeMinutesGroupedByLinkOnDate] 的 GROUP BY 投影：某关联对象在某天的分钟合计 */
data class LinkDayMinutes(
    val linkId: Long,
    val minutes: Int,
)

/**
 * [PomodoroSessionDao.observeStatsByLink] 的投影：某关联对象的全历史统计。
 *
 * [lastEpochDay] 存的是 work_date 列的原始 epochDay 整数而非 LocalDate：
 * 投影类（POJO）不是 Entity，Room 不保证对它套用 TypeConverters，
 * 这里刻意用原生类型避开歧义，转换收口在 UI 层（LocalDate.ofEpochDay）。
 * null 表示还没有任何已完成记录。
 */
data class LinkStats(
    val totalCount: Int,
    val completedCount: Int,
    val totalMinutes: Int,
    val activeDays: Int,
    val lastEpochDay: Int?,
) {
    /** 中途放弃次数：总记录减去已完成的。不单开 SQL 列，能现算的就别冗余 */
    val abandonedCount: Int get() = totalCount - completedCount
}
