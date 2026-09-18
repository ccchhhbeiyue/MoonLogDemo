package com.example.demo.data.repository

import androidx.room.Transaction
import com.example.demo.data.local.dao.PomodoroSessionDao
import com.example.demo.data.local.dao.WorkLogDao
import com.example.demo.data.local.entity.WorkLog
import com.example.demo.domain.model.PomodoroLinkType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 每日工作日志的 Repository。
 *
 * 结构与 [TodoRepository] 对称：observe 系列透传给 UI 订阅，
 * [delete] 是跨表事务（删日志 + 解除番茄引用）。
 *
 * [addDuration] 特意透传 DAO 的增量 UPDATE（`duration_minutes = duration_minutes + :minutes`），
 * 而不是「读出来 → 加 → 写回」：后者在两个番茄同时结束时会互相覆盖，
 * 增量 UPDATE 把加法下推到数据库一行原子完成。类比后端：
 * 这就是 `UPDATE account SET balance = balance + ?` 优于 SELECT-then-UPDATE 的原因。
 */
class WorkRepository(
    private val workLogDao: WorkLogDao,
    private val pomodoroDao: PomodoroSessionDao,
) {

    fun observeByDate(date: LocalDate): Flow<List<WorkLog>> = workLogDao.observeByDate(date)

    fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<WorkLog>> =
        workLogDao.observeByDateRange(from, to)

    /** 某天总耗时（分钟），日历当日面板用 */
    fun observeTotalMinutesByDate(date: LocalDate): Flow<Int> = workLogDao.observeTotalMinutesByDate(date)

    /** 区间总耗时，工作页「本周工时」用 */
    fun observeTotalMinutesByRange(from: LocalDate, to: LocalDate): Flow<Int> =
        workLogDao.observeTotalMinutesByRange(from, to)

    suspend fun getById(id: Long): WorkLog? = workLogDao.getById(id)

    suspend fun insert(workLog: WorkLog): Long = workLogDao.insert(workLog)

    suspend fun update(workLog: WorkLog): Int = workLogDao.update(workLog)

    /** 番茄钟完整走完后，把时长累加到关联的日志上 */
    suspend fun addDuration(id: Long, minutes: Int) = workLogDao.addDuration(id, minutes)

    /** 删日志 + 解除番茄引用，同一事务，理由同 [TodoRepository.delete] */
    @Transaction
    suspend fun delete(workLog: WorkLog): Int {
        pomodoroDao.unlink(PomodoroLinkType.WORK_LOG.code, workLog.id)
        return workLogDao.delete(workLog)
    }
}
