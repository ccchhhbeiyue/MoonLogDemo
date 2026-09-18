package com.example.demo.domain.model

import java.time.LocalDate

/** 预测置信度：依据「有效周期条数 + 周期标准差」分级，UI 据此决定提示语气 */
enum class PredictionConfidence { LOW, MEDIUM, HIGH }

/**
 * 周期预测的输出值对象（计划 §4.2 Level 3）。
 *
 * ## 只存推论、不落库
 * 数据库只保留事实（period_record）；预测结果每次由
 * [com.example.demo.domain.predictor.CyclePredictor] 实时算出。
 * 用户改了一条记录，下一次计算自动反映新事实，不会留下脏的预测缓存。
 * 类比后端：这是「查询时计算的视图」，不是「物化视图」。
 */
data class PredictionResult(
    /** 预测的下次月经第 1 天 */
    val nextPeriodStart: LocalDate,
    /** 预测的下次月经最后 1 天 */
    val nextPeriodEnd: LocalDate,
    /** 排卵日 = nextPeriodStart − 14（倒推法，见 CyclePredictor 注释） */
    val ovulationDay: LocalDate,
    /** 易孕窗口起 = 排卵日 − 5 */
    val fertileWindowStart: LocalDate,
    /** 易孕窗口止 = 排卵日 + 1 */
    val fertileWindowEnd: LocalDate,
    /** 距下次月经还有几天（首页倒计时） */
    val daysUntilNextPeriod: Long,
    /** 本次采用的预测周期长度（天） */
    val predictedCycleLength: Int,
    /** 有效周期的标准差，越大越不规律；>5 时 UI 提示咨询医生 */
    val cycleStdDev: Double,
    val confidence: PredictionConfidence,
)
