package com.example.demo.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.repository.PeriodRepository
import com.example.demo.data.repository.PomodoroRepository
import com.example.demo.data.repository.TodoRepository
import com.example.demo.domain.model.TimerMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

/**
 * 统计页 ViewModel（P8）。
 *
 * ## 三条流 combine 成一份 UI 状态
 * 周期趋势来自经期记录、番茄完成率来自番茄记录、待办完成率来自待办——
 * 三张表互不依赖，但页面要同时显示，所以用 [combine] 做三路 join，
 * 任一张表变化整页统计自动重算（与 CalendarViewModel 的双流 join 同理）。
 *
 * ## 统计口径与预测口径保持一致
 * 周期长度数组复用预测算法的异常剔除规则（<20 或 >40 天不进统计），
 * 避免一次误录把均值和折线一起带偏。原始记录仍完整保留在库里。
 */
class StatsViewModel(
    periodRepository: PeriodRepository,
    pomodoroRepository: PomodoroRepository,
    todoRepository: TodoRepository,
) : ViewModel() {

    val state: StateFlow<StatsUiState> = combine(
        periodRepository.observeAllAscending(),
        pomodoroRepository.observeAll(),
        todoRepository.observeAll(),
    ) { records, sessions, todos ->
        // 相邻两次开始日之差 = 一个周期长度；zipWithNext 天然是「滑动窗口取相邻对」
        val cycles = records
            .zipWithNext { a, b -> ChronoUnit.DAYS.between(a.startDate, b.startDate).toInt() }
            .filter { it in VALID_CYCLE_RANGE }
        // 「番茄完成率」只统计番茄钟模式的会话：P10 引入的倒计时/正计时会话
        // 语义上不是番茄，混入会把完成率口径带偏。
        val pomodoroSessions = sessions.filter { it.mode == TimerMode.POMODORO.code }
        StatsUiState(
            cycleLengths = cycles.takeLast(MAX_TREND_POINTS),
            averageCycle = cycles.average().takeIf { cycles.isNotEmpty() },
            cycleStdDev = cycles.stdDev().takeIf { cycles.isNotEmpty() },
            pomodoroTotal = pomodoroSessions.size,
            pomodoroCompleted = pomodoroSessions.count { it.isCompleted },
            todoTotal = todos.size,
            todoDone = todos.count { it.isDone },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = StatsUiState(),
    )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        /** 与 CyclePredictor 相同的异常周期剔除区间 */
        val VALID_CYCLE_RANGE = 20..40

        /** 折线最多画最近 12 个周期，再多的历史点挤在屏上没有信息量 */
        const val MAX_TREND_POINTS = 12
    }
}

/**
 * 统计页的一份不可变快照。
 *
 * 派生指标（评分、完成率）做成计算属性而非字段：它们完全由原始计数决定，
 * 存字段反而要保证「别忘了同步」，计算属性天然一致。
 */
data class StatsUiState(
    val cycleLengths: List<Int> = emptyList(),
    val averageCycle: Double? = null,
    val cycleStdDev: Double? = null,
    val pomodoroTotal: Int = 0,
    val pomodoroCompleted: Int = 0,
    val todoTotal: Int = 0,
    val todoDone: Int = 0,
) {
    /** 规律性评分：标准差 0 天=100 分，每多 1 天扣 10 分，扣到 0 为止 */
    val regularityScore: Int?
        get() = cycleStdDev?.let { (100 - it * 10).toInt().coerceIn(0, 100) }

    /** 标准差超过 5 天视为波动较大，UI 出提示（与方案 §4.2 的加分细节一致） */
    val isIrregular: Boolean
        get() = (cycleStdDev ?: 0.0) > 5.0

    val pomodoroCompletionRate: Float?
        get() = if (pomodoroTotal == 0) null else pomodoroCompleted.toFloat() / pomodoroTotal

    val todoCompletionRate: Float?
        get() = if (todoTotal == 0) null else todoDone.toFloat() / todoTotal
}

/** 总体标准差（除以 n 而非 n-1）：这里描述的是「这几次的波动」而非样本推断总体 */
private fun List<Int>.stdDev(): Double {
    if (isEmpty()) return 0.0
    val mean = average()
    return sqrt(sumOf { (it - mean) * (it - mean) } / size)
}
