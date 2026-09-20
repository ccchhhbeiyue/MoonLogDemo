package com.example.demo.data.repository

import com.example.demo.data.local.dao.PeriodRecordDao
import com.example.demo.data.local.entity.PeriodRecord
import com.example.demo.data.local.entity.effectiveEnd
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

    /**
     * 新增记录；若与已有记录区间重叠则融合成一条（「同一段生理过程不会来两次」）。
     *
     * ## 为什么融合收口在这里而不是 ViewModel
     * 融合是数据不变量，与「结束日不早于开始日」同级；放在 Repository
     * 才能让所有调用方（日历页/历史页/未来的导入功能）都受保护。
     *
     * ## 重叠判定为什么用 effectiveEnd 而非 end_date
     * 进行中记录的 end_date 为 null，但它在日历上实际覆盖到今天；
     * 若按 null 退化成开始日一天判定，12 号开始的那条进行中记录
     * 就挡不住 19 号的补录，会出现两条重叠记录。
     *
     * ## 为什么衔接（紧挨着前一天/后一天）也算同一段
     * 「系统默认 22 号结束，但用户发现没完全结束，在 23 号补记」——
     * 医学上同一段生理过程不会隔一天再来一次，衔接的新增应视为延续而非新周期；
     * 正常两次经期间隔 20+ 天，不会误触这条规则。
     *
     * ## 融合规则
     * - 开始日取最小；结束日全非空取最大，任一进行中则融合后仍进行中；
     * - 预计天数取各条最大（渲染时还有 max(today) 兜底）；
     * - flow/symptoms/note 用新记录的非空值覆盖（用户刚填的意图优先）；
     * - 保留最小开始日那行的 id（start_date UNIQUE 索引），其余旧行删除，
     *   删+写由 DAO 的 @Transaction 保证原子。
     */
    suspend fun insertOrMerge(record: PeriodRecord, today: LocalDate): Long {
        requireValidRange(record)
        val newEnd = record.effectiveEnd(today)
        val overlapped = dao.getAllAscending().filter { old ->
            val oldEnd = old.effectiveEnd(today)
            val overlaps = !record.startDate.isAfter(oldEnd) && !old.startDate.isAfter(newEnd)
            val adjacent = record.startDate == oldEnd.plusDays(1) || old.startDate == newEnd.plusDays(1)
            overlaps || adjacent
        }
        if (overlapped.isEmpty()) return dao.insert(record)

        val pool = overlapped + record
        val keep = pool.minByOrNull { it.startDate }!!
        // 用 id 而非 startDate 判定 keep 是否为新记录：startDate 相同时 minByOrNull 返回
        // 排在前面的旧行（pool = overlapped + record），若误判为新记录会走 INSERT，
        // 而 keep 旧行未被删除 → start_date UNIQUE 索引直接撞约束崩溃
        val keepIsNew = keep.id == 0L
        val merged = PeriodRecord(
            id = if (keepIsNew) 0 else keep.id,
            startDate = keep.startDate,
            endDate = if (pool.all { it.endDate != null }) pool.maxOf { it.endDate!! } else null,
            expectedDays = pool.maxOf { it.expectedDays },
            flow = record.flow ?: keep.flow,
            symptoms = record.symptoms.ifBlank { keep.symptoms },
            note = record.note ?: keep.note,
            createdAt = keep.createdAt,
        )
        // 只删被吞掉的旧行：keep 行要留着承接 update（先删它再 update 会更新 0 行→丢数据）
        val deleteIds = overlapped.filter { it.id != keep.id }.map { it.id }
        return dao.mergePeriods(deleteIds, merged)
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
