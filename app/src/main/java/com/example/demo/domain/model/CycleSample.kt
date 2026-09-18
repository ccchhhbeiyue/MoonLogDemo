package com.example.demo.domain.model

import java.time.LocalDate

/**
 * 喂给预测算法的单条「事实样本」。
 *
 * ## 为什么不直接把 Room 的 PeriodRecord 传给算法
 * PeriodRecord 带 androidx.room 注解，若算法直接引用它，domain 层就沾上了
 * Android/Room 依赖，破坏「domain 纯 Kotlin、可跑 JVM 单测」的边界（计划 §5.2）。
 * 故在 ViewModel 层把 entity 映射成这个纯值对象，再交给 CyclePredictor。
 * 类比后端：DAO 的 entity 不外泄到 service 之外，跨层传递用 DTO。
 *
 * @param start 月经第 1 天
 * @param periodLength 经期天数（end−start+1）；进行中/未回填为 null
 */
data class CycleSample(
    val start: LocalDate,
    val periodLength: Int?,
)
