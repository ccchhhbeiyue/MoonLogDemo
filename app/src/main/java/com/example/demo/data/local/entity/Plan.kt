package com.example.demo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 计划——「打算反复去做的事」的模板，跨天复用。
 *
 * 与 [WorkLog] 的区别：WorkLog 是「某一天做过了什么」的日记（按天一条），
 * Plan 是「我打算每天/经常做这件事」的模板（不绑定具体日期）。
 * 计划本身**不存进度**：今日已完成分钟由「当日 link 到本计划的 session 的
 * actual_minutes 之和」现算（见 PlanViewModel），避免「模板里存进度」带来的双写不一致。
 *
 * [mode] 存 [com.example.demo.domain.model.TimerMode] 的 code：
 * 启动计时时按它决定走倒计时 / 正计时 / 番茄钟。
 * [targetMinutes] 仅 COUNTDOWN 有意义；COUNTUP 无目标存 0；
 * POMODORO 的目标来自设置页专注时长，这里也存 0（启动时现读设置）。
 */
@Entity(tableName = "plan")
data class Plan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** 计时模式 code，见 [com.example.demo.domain.model.TimerMode] */
    val mode: Int = 0,

    /** 目标分钟；仅倒计时模式使用，其余模式存 0 */
    @ColumnInfo(name = "target_minutes")
    val targetMinutes: Int = 0,

    /** 是否每日计划：开启后从创建日起每天出现在日历日面板，日期上带棕色小点 */
    @ColumnInfo(name = "is_daily")
    val isDaily: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {

    /** 创建时间的本地日期：每日计划在日历上生效的起始日 */
    fun createdLocalDate(): LocalDate =
        Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
}
