package com.example.demo.data.repository

import com.example.demo.data.local.dao.LinkDayMinutes
import com.example.demo.data.local.dao.LinkStats
import com.example.demo.data.local.dao.PomodoroSessionDao
import com.example.demo.data.local.entity.PomodoroSession
import com.example.demo.domain.model.PomodoroLinkType
import com.example.demo.domain.model.TimerMode
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 番茄记录的 Repository。
 *
 * ## 本层唯一但关键的职责：保证 workDate 与 startTime 一致
 * [PomodoroSession.workDate] 是从 [PomodoroSession.startTime] 冗余出来的反规范化字段
 * （为什么冗余见 Entity 的注释）。冗余字段的代价是「写入时必须两边一致」，
 * 而这个一致性靠 UI 层自觉是守不住的——所以这里**不提供裸 insert**，
 * 只给 [recordSession]：调用方传时刻，本层自己算出归属日期。
 * 类比后端：冗余列/缓存字段的写入必须收口在一个地方，否则必然出现两边对不上。
 *
 * ## 关联完整性
 * [link] 与 [linkId] 必须配对：有关联类型就必须有 id，无关联类型就必须没有 id。
 * 数据库层面对多态外键无能为力（见 [PomodoroLinkType] 的注释），这条不变量只能在这里守。
 */
class PomodoroRepository(private val dao: PomodoroSessionDao) {

    /** 某天的番茄记录，专注页当日列表 + 日历当日面板用 */
    fun observeByDate(date: LocalDate): Flow<List<PomodoroSession>> = dao.observeByDate(date)

    /** 区间记录，统计页本周柱状图用 */
    fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<PomodoroSession>> =
        dao.observeByDateRange(from, to)

    fun observeCompletedCountByDate(date: LocalDate): Flow<Int> = dao.observeCompletedCountByDate(date)

    fun observeCompletedMinutesByDate(date: LocalDate): Flow<Int> = dao.observeCompletedMinutesByDate(date)

    fun observeRecent(limit: Int): Flow<List<PomodoroSession>> = dao.observeRecent(limit)

    /** 全部番茄记录，统计页算总体完成率用 */
    fun observeAll(): Flow<List<PomodoroSession>> = dao.observeAll()

    /** 某个任务关联的已完成番茄数，待办列表项上的角标 */
    fun observeCompletedCountByLink(link: PomodoroLinkType, linkId: Long): Flow<Int> =
        dao.observeCompletedCountByLink(link.code, linkId)

    /**
     * 某天里、某类关联对象按 link_id 分组的分钟合计，计划页「今日 x 分钟」用。
     * 直接透传 DAO 的 SQL 聚合，一条查询覆盖当天所有该类对象，避免 N 个计划发 N 条查询。
     */
    fun observeMinutesGroupedByLinkOnDate(
        link: PomodoroLinkType,
        date: LocalDate,
    ): Flow<List<LinkDayMinutes>> = dao.observeMinutesGroupedByLinkOnDate(link.code, date)

    /**
     * 某个关联对象的全历史统计（累计次数/时长/坚持天数），P10-7 计划详情用。
     * 同样是透传 DAO 的 SQL 聚合：统计口径属于数据层的事，不能让 UI 拉全量 session 自己算。
     */
    fun observeStatsByLink(link: PomodoroLinkType, linkId: Long): Flow<LinkStats> =
        dao.observeStatsByLink(link.code, linkId)

    /**
     * 落一条番茄记录。P7 的前台服务在计时结束/放弃时调用。
     *
     * @param startTime     开始时刻，epochMilli
     * @param plannedMinutes 计划时长；中途放弃时 actual < planned，完成率靠这两个字段算
     * @param actualMinutes  实际时长
     * @param isCompleted   是否完整走完
     * @param link          关联类型；[PomodoroLinkType.NONE] 表示不关联
     * @param linkId        关联的 todo.id / work_log.id / plan.id
     * @param mode          计时模式；COUNTUP 无目标，plannedMinutes 传 0
     */
    suspend fun recordSession(
        startTime: Long,
        plannedMinutes: Int,
        actualMinutes: Int,
        isCompleted: Boolean,
        link: PomodoroLinkType = PomodoroLinkType.NONE,
        linkId: Long? = null,
        mode: TimerMode = TimerMode.POMODORO,
    ): Long {
        require(actualMinutes >= 0) { "actualMinutes 必须非负：$actualMinutes" }
        if (mode == TimerMode.COUNTUP) {
            // 正计时没有目标时长：planned 记 0，actual 为实际走过的分钟，不受 planned 上界约束。
            require(plannedMinutes == 0) { "COUNTUP 模式 plannedMinutes 必须为 0：$plannedMinutes" }
        } else {
            // 倒计时/番茄钟：完成率靠 planned 与 actual 两个字段算，必须有正目标且 actual 不超界
            require(plannedMinutes > 0) { "plannedMinutes 必须为正：$plannedMinutes" }
            require(actualMinutes <= plannedMinutes) {
                "actualMinutes($actualMinutes) 必须不超过 plannedMinutes($plannedMinutes)"
            }
        }
        require((link == PomodoroLinkType.NONE) == (linkId == null)) {
            "link=$link 与 linkId=$linkId 必须配对：NONE 时 linkId 必为 null，否则必非 null"
        }
        return dao.insert(
            PomodoroSession(
                startTime = startTime,
                plannedMinutes = plannedMinutes,
                actualMinutes = actualMinutes,
                isCompleted = isCompleted,
                linkType = link.code,
                linkId = linkId,
                mode = mode.code,
                workDate = startTime.toLocalDate(),
            )
        )
    }

    suspend fun update(session: PomodoroSession): Int = dao.update(session)

    /**
     * epochMilli → 本机时区的 [LocalDate]。
     *
     * 必须显式指定 [ZoneId.systemDefault]：番茄的「归属哪天」是用户感知意义上的那天，
     * 用 UTC 算会让跨零点的番茄记到前一天去。
     */
    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
}
