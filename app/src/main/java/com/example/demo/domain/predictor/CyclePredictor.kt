package com.example.demo.domain.predictor

import com.example.demo.domain.model.CycleSample
import com.example.demo.domain.model.PredictionConfidence
import com.example.demo.domain.model.PredictionResult
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

/**
 * 生理期周期预测算法（计划 §4.2）。纯 Kotlin、零 Android 依赖，可直接跑 JVM 单测。
 *
 * ## 医学前提（算法正确性的根）
 * - 周期长度 = 本次月经第 1 天 → 下次月经第 1 天，正常 21~35 天；
 * - **黄体期恒定**：排卵日 → 下次月经开始 ≈ 14 天，几乎不变；
 * - **卵泡期是变量**：周期波动主要来自这里。
 *
 * ## ⚠️ 排卵日必须倒推
 * `排卵日 = 预测下次月经开始日 − 14`，**不能**用 `本次开始日 + 14`——
 * 后者只在周期恰好 28 天时成立，周期 30 天就会算错 2 天。
 *
 * ## 分级（对照计划 Level 0~3）
 * - 0 条：返回 null，UI 提示先记录，不渲染预测标记；
 * - 1 条：算不出周期，用默认周期，confidence=LOW；
 * - 2 条：得 1 个周期长度直接用，confidence=LOW；
 * - ≥3 条：相邻周期长度数组 → 剔除异常(<20 或 >40) → 最近 3 次按 3:2:1 加权平均
 *   → 收敛到生理合理区间 [20,40]，避免日历标记乱跳。
 */
object CyclePredictor {

    /** 黄体期天数（排卵 → 下次月经），医学上近似恒定 */
    private const val LUTEAL_PHASE_DAYS = 14L

    /** 有效周期下限（天）：低于此视为误录/偶发紊乱，剔除出计算集 */
    private const val MIN_VALID_CYCLE = 20

    /** 有效周期上限（天） */
    private const val MAX_VALID_CYCLE = 40

    /** 标准差超过此值认为周期不规律，confidence 不给 HIGH 且 UI 提示咨询医生 */
    private const val HIGH_CONFIDENCE_STD_DEV = 5.0

    /** 经期长度兜底 clamp 区间（医学常识 3~7，放宽到 1~10 容错） */
    private const val MIN_PERIOD = 1
    private const val MAX_PERIOD = 10

    /**
     * @param samples 历史事实样本（无需有序，内部会排序）
     * @param today 今天，用于算倒计时
     * @param defaultCycleLength 冷启动/无有效周期时的兜底周期（来自 user_setting）
     * @param defaultPeriodLength 无已知经期长度时的兜底经期天数
     * @return 预测结果；0 条记录时返回 null
     */
    fun predict(
        samples: List<CycleSample>,
        today: LocalDate,
        defaultCycleLength: Int,
        defaultPeriodLength: Int,
    ): PredictionResult? {
        if (samples.isEmpty()) return null

        val sorted = samples.sortedBy { it.start }
        val lastStart = sorted.last().start

        // 相邻两条的开始日之差 = 一个周期长度
        val cycleLengths = sorted
            .zipWithNext { a, b -> ChronoUnit.DAYS.between(a.start, b.start).toInt() }
        val validCycles = cycleLengths.filter { it in MIN_VALID_CYCLE..MAX_VALID_CYCLE }

        // 无有效周期（0/1 条记录，或全被剔除）→ 兜底默认周期；否则加权平均并收敛
        val predictedCycle = if (validCycles.isEmpty()) {
            defaultCycleLength
        } else {
            weightedAverage(validCycles).toInt().coerceIn(MIN_VALID_CYCLE, MAX_VALID_CYCLE)
        }

        // 预测经期长度：已知经期天数的均值，全未知则兜底
        val periodLength = sorted
            .mapNotNull { it.periodLength }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toInt()?.coerceIn(MIN_PERIOD, MAX_PERIOD)
            ?: defaultPeriodLength

        val nextStart = lastStart.plusDays(predictedCycle.toLong())
        val nextEnd = nextStart.plusDays(periodLength.toLong() - 1)
        val ovulation = nextStart.minusDays(LUTEAL_PHASE_DAYS) // 倒推法
        val fertileStart = ovulation.minusDays(5)
        val fertileEnd = ovulation.plusDays(1)

        return PredictionResult(
            nextPeriodStart = nextStart,
            nextPeriodEnd = nextEnd,
            ovulationDay = ovulation,
            fertileWindowStart = fertileStart,
            fertileWindowEnd = fertileEnd,
            daysUntilNextPeriod = ChronoUnit.DAYS.between(today, nextStart),
            predictedCycleLength = predictedCycle,
            cycleStdDev = standardDeviation(validCycles),
            confidence = confidence(sorted.size, validCycles.size, standardDeviation(validCycles)),
        )
    }

    /** 最近 3 次有效周期按 3:2:1 加权（越近权重越高）；不足 3 个时按现有个数加权 */
    private fun weightedAverage(cycles: List<Int>): Double {
        val newestFirst = cycles.takeLast(3).reversed()
        val weights = listOf(3, 2, 1)
        var sum = 0.0
        var weightSum = 0.0
        newestFirst.forEachIndexed { index, cycle ->
            val w = weights[index].toDouble()
            sum += cycle * w
            weightSum += w
        }
        return sum / weightSum
    }

    /** 总体标准差；不足 2 个样本时无波动可言，返回 0 */
    private fun standardDeviation(cycles: List<Int>): Double {
        if (cycles.size < 2) return 0.0
        val mean = cycles.average()
        val variance = cycles.sumOf { (it - mean) * (it - mean) } / cycles.size
        return sqrt(variance)
    }

    private fun confidence(sampleCount: Int, validCycleCount: Int, stdDev: Double): PredictionConfidence =
        when {
            sampleCount <= 2 -> PredictionConfidence.LOW
            validCycleCount >= 3 && stdDev <= HIGH_CONFIDENCE_STD_DEV -> PredictionConfidence.HIGH
            else -> PredictionConfidence.MEDIUM
        }
}
