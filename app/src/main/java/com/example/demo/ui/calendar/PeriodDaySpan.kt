package com.example.demo.ui.calendar

import com.example.demo.data.local.entity.PeriodRecord
import com.example.demo.data.local.entity.effectiveEnd
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 某一天在经期区间中的位置，决定日历实色带的圆角形状 */
enum class PeriodDayRole { SINGLE, START, MIDDLE, END }

/**
 * 日历上某一天的经期信息。
 *
 * @param record 覆盖这一天的经期记录
 * @param role 这一天在区间中的位置（决定实色带圆角：首/中/尾/单日）
 * @param dayIndex 区间内第几天（1 起），当日面板显示「经期第 N 天」用
 * @param totalDays 区间总天数
 */
data class PeriodDaySpan(
    val record: PeriodRecord,
    val role: PeriodDayRole,
    val dayIndex: Int,
    val totalDays: Int,
)

/**
 * 把经期记录列表展开成「日期 → 经期信息」的 Map，供日历 dayContent 查表渲染。
 *
 * ## 为什么展开成 Map 而不是在 dayContent 里遍历 records
 * dayContent 每个格子都会调用一次（一个月 35+ 格），若每格都遍历 records 找覆盖记录，
 * 是 O(天数 × 记录数)；先展开成 Map 后每格 O(1) 查表。
 * 类比后端：把「每次请求都扫全表」改成「先建索引，请求查索引」。
 *
 * ## 进行中（endDate 为 null）的处理
 * 覆盖范围取 [effectiveEnd]：预计天数还没走完时画到预计结束日
 * （未来部分由 DayCell 染浅粉、已发生部分染深色），已经超过预计天数
 * 还没点结束时至少覆盖到今天（「尚未结束就连续到当天」）。
 * 不再退化成「仅开始日一天」，否则进行中的经期在日历上只剩孤零零一个格子。
 */
fun expandPeriodSpans(records: List<PeriodRecord>, today: LocalDate): Map<LocalDate, PeriodDaySpan> {
    val result = mutableMapOf<LocalDate, PeriodDaySpan>()
    for (record in records) {
        val end = record.effectiveEnd(today)
        val totalDays = ChronoUnit.DAYS.between(record.startDate, end).toInt() + 1
        var current = record.startDate
        var index = 0
        while (!current.isAfter(end)) {
            val role = when {
                totalDays == 1 -> PeriodDayRole.SINGLE
                index == 0 -> PeriodDayRole.START
                current == end -> PeriodDayRole.END
                else -> PeriodDayRole.MIDDLE
            }
            result[current] = PeriodDaySpan(record, role, index + 1, totalDays)
            current = current.plusDays(1)
            index++
        }
    }
    return result
}
