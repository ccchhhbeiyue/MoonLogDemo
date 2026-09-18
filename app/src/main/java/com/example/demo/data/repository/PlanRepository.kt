package com.example.demo.data.repository

import androidx.room.Transaction
import com.example.demo.data.local.dao.PlanDao
import com.example.demo.data.local.dao.PomodoroSessionDao
import com.example.demo.data.local.entity.Plan
import com.example.demo.domain.model.PomodoroLinkType
import kotlinx.coroutines.flow.Flow

/**
 * 计划的 Repository。
 *
 * ## 构造器为什么收两个 DAO
 * 与 [TodoRepository] 完全同构：[delete] 是跨表业务动作——删计划的同时必须解除
 * 番茄记录（pomodoro_session）对它的多态引用，否则按 link 聚合今日进度时会算进幽灵数据。
 * 两步要么一起成功要么一起回滚，事务边界画在本方法，故同时持有两张表的 DAO。
 * 类比后端：一个 Service 注入两个 Mapper 开一个事务，而非 Controller 自己拼。
 */
class PlanRepository(
    private val planDao: PlanDao,
    private val pomodoroDao: PomodoroSessionDao,
) {

    fun observeAll(): Flow<List<Plan>> = planDao.observeAll()

    suspend fun getById(id: Long): Plan? = planDao.getById(id)

    suspend fun insert(plan: Plan): Long = planDao.insert(plan)

    suspend fun update(plan: Plan): Int = planDao.update(plan)

    /**
     * 删除计划，并同步解除番茄记录对它的引用。
     * [Transaction] 保证 unlink 与 delete 同事务；注意它只对 suspend 函数生效。
     */
    @Transaction
    suspend fun delete(plan: Plan): Int {
        pomodoroDao.unlink(PomodoroLinkType.PLAN.code, plan.id)
        return planDao.delete(plan)
    }
}
