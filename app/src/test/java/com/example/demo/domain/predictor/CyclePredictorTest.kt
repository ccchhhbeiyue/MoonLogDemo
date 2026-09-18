package com.example.demo.domain.predictor

import com.example.demo.domain.model.CycleSample
import com.example.demo.domain.model.PredictionConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * CyclePredictor 单元测试（计划 §4.3 强制配套）。
 *
 * 全部用固定日期手算期望值，不依赖"今天"，保证可重复。
 * 覆盖边界：0/1/2/3+ 条、含异常值、全异常值、跨月、闰年、倒推法反例。
 */
class CyclePredictorTest {

    private val today = LocalDate.of(2026, 1, 10)

    private fun sample(start: LocalDate, periodLength: Int? = 5) = CycleSample(start, periodLength)

    @Test
    fun zeroRecords_returnsNull() {
        assertNull(CyclePredictor.predict(emptyList(), today, 28, 5))
    }

    @Test
    fun oneRecord_usesDefaultCycle_andLowConfidence() {
        val result = CyclePredictor.predict(
            samples = listOf(sample(LocalDate.of(2026, 1, 1))),
            today = today,
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
        )!!
        // 1 条记录算不出周期 → 用默认 28
        assertEquals(LocalDate.of(2026, 1, 29), result.nextPeriodStart)
        assertEquals(LocalDate.of(2026, 2, 2), result.nextPeriodEnd) // 29 + 5 - 1
        assertEquals(LocalDate.of(2026, 1, 15), result.ovulationDay) // 倒推 29 - 14
        assertEquals(LocalDate.of(2026, 1, 10), result.fertileWindowStart) // 15 - 5
        assertEquals(LocalDate.of(2026, 1, 16), result.fertileWindowEnd) // 15 + 1
        assertEquals(19L, result.daysUntilNextPeriod) // 01-10 → 01-29
        assertEquals(28, result.predictedCycleLength)
        assertEquals(PredictionConfidence.LOW, result.confidence)
    }

    @Test
    fun twoRecords_singleCycleUsed_lowConfidence() {
        val result = CyclePredictor.predict(
            samples = listOf(
                sample(LocalDate.of(2026, 1, 1)),
                sample(LocalDate.of(2026, 1, 31)), // 周期 30
            ),
            today = today,
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
        )!!
        assertEquals(30, result.predictedCycleLength)
        assertEquals(LocalDate.of(2026, 3, 2), result.nextPeriodStart) // 01-31 + 30
        assertEquals(PredictionConfidence.LOW, result.confidence)
    }

    @Test
    fun ovulationUsesReverseMethod_notForwardFromLastStart() {
        val result = CyclePredictor.predict(
            samples = listOf(
                sample(LocalDate.of(2026, 1, 1)),
                sample(LocalDate.of(2026, 1, 31)), // 周期 30，非 28
            ),
            today = today,
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
        )!!
        // 倒推法：nextStart(03-02) - 14 = 02-16
        assertEquals(LocalDate.of(2026, 2, 16), result.ovulationDay)
        // 错误的"本次开始 + 14"会得到 02-14，必须不相等
        assertNotEquals(LocalDate.of(2026, 2, 14), result.ovulationDay)
    }

    @Test
    fun fourRecords_weightedAverageRecent3_andMediumConfidenceOnHighStdDev() {
        // 周期序列 [20, 28, 40]：加权 (40*3 + 28*2 + 20*1) / 6 = 32.67 → 32
        val result = CyclePredictor.predict(
            samples = listOf(
                sample(LocalDate.of(2026, 1, 1)),
                sample(LocalDate.of(2026, 1, 21)), // +20
                sample(LocalDate.of(2026, 2, 18)), // +28
                sample(LocalDate.of(2026, 3, 30)), // +40
            ),
            today = today,
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
        )!!
        assertEquals(32, result.predictedCycleLength)
        assertEquals(LocalDate.of(2026, 5, 1), result.nextPeriodStart) // 03-30 + 32
        // 3 个有效周期但 stdDev≈8.2 > 5 → 不给 HIGH
        assertEquals(PredictionConfidence.MEDIUM, result.confidence)
    }

    @Test
    fun excludesOutlierCycle_andHighConfidenceWhenStable() {
        // 周期序列 [28, 5, 28, 28]：5 是异常值被剔除，剩余稳定 28
        val result = CyclePredictor.predict(
            samples = listOf(
                sample(LocalDate.of(2026, 1, 1)),
                sample(LocalDate.of(2026, 1, 29)), // +28
                sample(LocalDate.of(2026, 2, 3)), // +5  异常
                sample(LocalDate.of(2026, 3, 3)), // +28
                sample(LocalDate.of(2026, 3, 31)), // +28
            ),
            today = today,
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
        )!!
        assertEquals(28, result.predictedCycleLength)
        assertEquals(LocalDate.of(2026, 4, 28), result.nextPeriodStart) // 03-31 + 28
        assertEquals(0.0, result.cycleStdDev, 0.001)
        assertEquals(PredictionConfidence.HIGH, result.confidence)
    }

    @Test
    fun allCyclesOutlier_fallsBackToDefault() {
        // 周期序列 [5, 41] 全部异常 → 无有效周期 → 兜底默认 28
        val result = CyclePredictor.predict(
            samples = listOf(
                sample(LocalDate.of(2026, 1, 1)),
                sample(LocalDate.of(2026, 1, 6)), // +5   异常
                sample(LocalDate.of(2026, 2, 16)), // +41  异常
            ),
            today = today,
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
        )!!
        assertEquals(28, result.predictedCycleLength)
        assertEquals(LocalDate.of(2026, 3, 16), result.nextPeriodStart) // 02-16 + 28
    }

    @Test
    fun predictedPeriodCanSpanMonthBoundary() {
        // nextStart=02-28，经期 5 天 → nextEnd 跨到 03-04
        val result = CyclePredictor.predict(
            samples = listOf(
                sample(LocalDate.of(2026, 1, 3)),
                sample(LocalDate.of(2026, 1, 31)), // +28
            ),
            today = today,
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
        )!!
        assertEquals(LocalDate.of(2026, 2, 28), result.nextPeriodStart)
        assertEquals(LocalDate.of(2026, 3, 4), result.nextPeriodEnd) // 跨月
    }

    @Test
    fun leapYear_february29Handled() {
        // 2024 闰年：01-31 → 02-29 是 29 天（有效周期），nextStart = 02-29 + 29
        val result = CyclePredictor.predict(
            samples = listOf(
                sample(LocalDate.of(2024, 1, 31)),
                sample(LocalDate.of(2024, 2, 29)), // +29
            ),
            today = LocalDate.of(2024, 2, 1),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
        )!!
        assertEquals(29, result.predictedCycleLength)
        assertEquals(LocalDate.of(2024, 3, 29), result.nextPeriodStart) // 02-29 + 29
    }

    @Test
    fun fertileWindowAlwaysBracketsOvulation() {
        val result = CyclePredictor.predict(
            samples = listOf(
                sample(LocalDate.of(2026, 1, 1)),
                sample(LocalDate.of(2026, 1, 29)),
            ),
            today = today,
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
        )!!
        assertEquals(result.ovulationDay.minusDays(5), result.fertileWindowStart)
        assertEquals(result.ovulationDay.plusDays(1), result.fertileWindowEnd)
    }
}
