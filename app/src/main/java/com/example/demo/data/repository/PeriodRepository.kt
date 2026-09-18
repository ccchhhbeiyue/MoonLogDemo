package com.example.demo.data.repository

import com.example.demo.data.local.dao.PeriodRecordDao
import com.example.demo.data.local.entity.PeriodRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 经期记录的 Repository。
 *
 * ## 这一层存在的意义（对照后端）
 * DAO 是 Mapper：一句 SQL 一个方法，不含业务语义。
 * Repository 是 Service：把「数据不变量」「持久层细节」收拢到一处，
 * 让 ViewModel 只说业务语言。
 *
 * 本类承担的两个持久层细节：
 * 1. [insert] / [update] 前校验「结束日不早于开始日」。数据库的 UNIQUE 索引
 *    只能挡「同一天两次开始」，挡不住这种逻辑错误，Repository 是最后一道防线；
 * 2. [update] 自动刷新 updatedAt。这是审计字段，让每个调用方记得传必然出错。
 *
 * ## 为什么所有读方法都是透传
 * Flow 是冷流：不收集就不执行查询。Repository 只负责把「可订阅的数据源」递出去，
 * 收集（collect）是 ViewModel 用 stateIn 做的事。所以这里**不能**出现 collect/first，
 * 否则会把一个订阅型接口悄悄变成一次性查询。
 */
class PeriodRepository(private val dao: PeriodRecordDao) {

    /** 与 [from, to] 区间重叠的记录，日历渲染用 */
    fun observeOverlapping(from: LocalDate, to: LocalDate): Flow<List<PeriodRecord>> =
        dao.observeOverlapping(from, to)

    /** 全部记录升序，预测算法的输入 */
    fun observeAllAscending(): Flow<List<PeriodRecord>> = dao.observeAllAscending()

    /** 全部记录降序，历史列表页用 */
    fun observeAllDescending(): Flow<List<PeriodRecord>> = dao.observeAllDescending()

    fun observeByStartDate(date: LocalDate): Flow<PeriodRecord?> = dao.observeByStartDate(date)

    /** 记录总数，决定预测算法走哪个 Level，也用于 P1 的自检展示 */
    fun observeCount(): Flow<Int> = dao.observeCount()

    /** 一次性查询：算法与自检用，不给 UI 订阅 */
    suspend fun getAllAscending(): List<PeriodRecord> = dao.getAllAscending()

    suspend fun getById(id: Long): PeriodRecord? = dao.getById(id)

    suspend fun getByStartDate(date: LocalDate): PeriodRecord? = dao.getByStartDate(date)

    /**
     * 新增记录。
     *
     * @throws IllegalArgumentException 结束日早于开始日时抛出。
     *   选抛异常而不是静默修正：静默修正会掩盖调用方的 bug，
     *   而这条不变量被破坏意味着预测算法的周期长度会算出负数。
     */
    suspend fun insert(record: PeriodRecord): Long {
        requireValidRange(record)
        return dao.insert(record)
    }

    suspend fun update(record: PeriodRecord): Int {
        requireValidRange(record)
        return dao.update(record.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(record: PeriodRecord): Int = dao.delete(record)

    suspend fun deleteByStartDate(date: LocalDate): Int = dao.deleteByStartDate(date)

    private fun requireValidRange(record: PeriodRecord) {
        val end = record.endDate
        require(end == null || !end.isBefore(record.startDate)) {
            "endDate($end) 不能早于 startDate(${record.startDate})"
        }
    }
}
