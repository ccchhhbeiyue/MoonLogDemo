package com.example.demo.data.repository

import androidx.room.Transaction
import com.example.demo.data.local.dao.PomodoroSessionDao
import com.example.demo.data.local.dao.TodoDao
import com.example.demo.data.local.entity.Todo
import com.example.demo.domain.model.PomodoroLinkType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 待办的 Repository。
 *
 * ## 构造器为什么收两个 DAO
 * [delete] 是一个跨表业务动作：删待办的同时必须解除番茄记录对它的引用。
 * 这两步要么一起成功要么一起回滚，所以事务边界必须画在这个方法上，
 * 也就必须同时持有两张表的 DAO。类比后端：一个 Service 注入两个 Mapper 开一个事务，
 * 而不是让 Controller 自己注入两个 Mapper 去拼事务。
 *
 * ## [setDone] 为什么只收 isDone 不收 completedAt
 * 「完成时间」是持久层语义：完成时填当前时刻，取消完成时必须清空。
 * 把它暴露给 UI，就等于允许调用方传一个错误的完成时间进来，统计（完成率、完成耗时）全错。
 * DAO 的注释里专门强调了取消完成要传 null 清掉，这个知识收进 Repository 后 UI 就不可能犯。
 */
class TodoRepository(
    private val todoDao: TodoDao,
    private val pomodoroDao: PomodoroSessionDao,
) {

    fun observeAll(): Flow<List<Todo>> = todoDao.observeAll()

    /** 指定某天的待办，日历页当日面板用 */
    fun observeByDate(date: LocalDate): Flow<List<Todo>> = todoDao.observeByDate(date)

    fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<Todo>> =
        todoDao.observeByDateRange(from, to)

    /** 今日到期的未完成数，日历角标用 */
    fun observePendingCountByDate(date: LocalDate): Flow<Int> = todoDao.observePendingCountByDate(date)

    suspend fun getById(id: Long): Todo? = todoDao.getById(id)

    suspend fun insert(todo: Todo): Long = todoDao.insert(todo)

    suspend fun update(todo: Todo): Int = todoDao.update(todo)

    /** 切换完成态；完成时间由本层决定，见类注释 */
    suspend fun setDone(id: Long, isDone: Boolean) {
        todoDao.setDone(id, isDone, if (isDone) System.currentTimeMillis() else null)
    }

    /**
     * 删除待办，并同步解除番茄记录对它的引用。
     *
     * [Transaction] 保证两条 SQL 在同一 SQLite 事务里：
     * 若 delete 抛异常，unlink 的修改一并回滚，不会出现「待办还在但番茄关联已被清空」的半截状态。
     * 注意 Room 的 @Transaction **只对在 suspend 函数上生效**，加在普通函数上是静默失效。
     */
    @Transaction
    suspend fun delete(todo: Todo): Int {
        pomodoroDao.unlink(PomodoroLinkType.TODO.code, todo.id)
        return todoDao.delete(todo)
    }
}
