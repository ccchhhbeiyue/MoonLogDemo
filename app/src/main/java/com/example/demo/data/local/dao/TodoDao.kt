package com.example.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.demo.data.local.entity.Todo
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TodoDao {

    /**
     * 全部待办。排序规则：未完成在前 → 有截止日的按日期升序 → 无截止日的沉底 → 再按手动排序权重。
     *
     * `due_date IS NULL` 在 SQLite 里参与排序时 NULL 最小，会把无期限待办顶到最前，
     * 所以这里显式用 CASE 把它们排到最后。
     */
    @Query(
        """
        SELECT * FROM todo
        ORDER BY is_done ASC,
                 CASE WHEN due_date IS NULL THEN 1 ELSE 0 END ASC,
                 due_date ASC,
                 sort_order ASC,
                 created_at DESC
        """
    )
    fun observeAll(): Flow<List<Todo>>

    /** 指定某天的待办（日历页当日面板用） */
    @Query(
        """
        SELECT * FROM todo
        WHERE due_date = :date
        ORDER BY is_done ASC, priority DESC, sort_order ASC
        """
    )
    fun observeByDate(date: LocalDate): Flow<List<Todo>>

    /** 截止日期落在 [from, to] 区间内的待办 */
    @Query(
        """
        SELECT * FROM todo
        WHERE due_date BETWEEN :from AND :to
        ORDER BY due_date ASC, is_done ASC, priority DESC
        """
    )
    fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<Todo>>

    /** 今日到期的未完成待办数，用于日历角标 */
    @Query("SELECT COUNT(*) FROM todo WHERE due_date = :date AND is_done = 0")
    fun observePendingCountByDate(date: LocalDate): Flow<Int>

    @Query("SELECT * FROM todo WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Todo?

    @Insert
    suspend fun insert(todo: Todo): Long

    @Update
    suspend fun update(todo: Todo): Int

    @Delete
    suspend fun delete(todo: Todo): Int

    /**
     * 切换完成态。completedAt 在取消完成时要传 null 清掉，
     * 否则会残留一个"已完成时间"但其实没完成，统计就错了。
     */
    @Query("UPDATE todo SET is_done = :isDone, completed_at = :completedAt WHERE id = :id")
    suspend fun setDone(id: Long, isDone: Boolean, completedAt: Long?)
}
