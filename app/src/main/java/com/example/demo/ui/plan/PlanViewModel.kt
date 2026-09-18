package com.example.demo.ui.plan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.local.dao.LinkStats
import com.example.demo.data.local.entity.Plan
import com.example.demo.data.repository.PlanRepository
import com.example.demo.data.repository.PomodoroRepository
import com.example.demo.data.repository.SettingRepository
import com.example.demo.domain.model.PomodoroLinkType
import com.example.demo.domain.model.TimerMode
import com.example.demo.service.PomodoroService
import com.example.demo.service.TimerPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 计划页 ViewModel（P10-4）。
 *
 * ## 三条流合成一份列表状态
 * [plans] 用 combine 把三个来源拼成 UI 直接可渲染的 [PlanRow]：
 * 1. planRepository.observeAll() —— 计划模板本身；
 * 2. 今日分钟聚合 —— 当日 link=PLAN 的 session 按 link_id 分组的 actual 之和，做成 Map；
 * 3. runningPlanId —— 从 [PomodoroService.uiState] 里挑出「正在计时且属于某个计划」的 id。
 *
 * 计划不存进度，进度靠第 2 条流现算（见 [Plan] 注释），这样落库一次、多处聚合，永不双写。
 * 类比后端：模板表 + 明细表按外键 GROUP BY 出来的实时统计视图，而非在模板表里冗余一个会被写坏的计数器。
 *
 * ## 启动计时的模式分派
 * [start] 按计划的 mode 决定传给服务的 plannedMinutes：
 * - COUNTDOWN：用计划自身的 targetMinutes；
 * - POMODORO：现读设置页专注时长（不落在 Plan 里，保持「设置即真相」）；
 * - COUNTUP：无目标，传 0（服务侧按正计时处理，见 PomodoroService.handleStart）。
 * 启动后回调 [onStarted] 让 UI 跳到专注 Tab，复用其暂停/恢复/停止界面。
 *
 * ## 详情统计按需订阅（P10-7）
 * 列表只要「今日进度」，而详情要「全历史累计」。后者没必要给每个计划都算一份，
 * 所以单独走 [_statsPlanId] + flatMapLatest：只在详情打开时才有一条聚合查询在跑。
 */
class PlanViewModel(
    private val planRepository: PlanRepository,
    pomodoroRepository: PomodoroRepository,
    settingRepository: SettingRepository,
) : ViewModel() {

    /** 设置页专注时长，POMODORO 计划启动时作为目标分钟。缓存成 StateFlow 避免每次启动都查库。 */
    private val workMinutes: StateFlow<Int> = settingRepository.observe()
        .map { it.pomodoroWorkMin }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = DEFAULT_WORK_MINUTES,
        )

    val plans: StateFlow<List<PlanRow>> = combine(
        planRepository.observeAll(),
        pomodoroRepository.observeMinutesGroupedByLinkOnDate(PomodoroLinkType.PLAN, LocalDate.now())
            .map { list -> list.associate { it.linkId to it.minutes } },
        PomodoroService.uiState.map { state ->
            // 只有「正在/暂停计时且关联的是计划」才算该计划进行中；
            // COMPLETED 是已结束待关闭的展示态，不能再占着「进行中」
            if (state.linkType == PomodoroLinkType.PLAN &&
                (state.phase == TimerPhase.RUNNING || state.phase == TimerPhase.PAUSED)
            ) {
                state.linkId
            } else {
                null
            }
        },
    ) { planList, todayMap, runningId ->
        planList.map { plan ->
            val mode = TimerMode.fromCode(plan.mode)
            PlanRow(
                plan = plan,
                mode = mode,
                // 目标分钟：COUNTDOWN 用计划的；POMODORO 用设置页的；COUNTUP 无目标为 0
                targetMinutes = when (mode) {
                    TimerMode.COUNTDOWN -> plan.targetMinutes
                    TimerMode.POMODORO -> workMinutes.value
                    TimerMode.COUNTUP -> 0
                },
                todayMinutes = todayMap[plan.id] ?: 0,
                isRunning = runningId == plan.id,
                isDaily = plan.isDaily,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = emptyList(),
    )

    /**
     * 当前要看统计的计划 id；null = 详情未打开。
     * 必须声明在 [planStats] 之前：Kotlin 属性按声明顺序初始化，flatMapLatest 在初始化时就要读它。
     */
    private val _statsPlanId = MutableStateFlow<Long?>(null)

    /**
     * 计划详情统计（P10-7）：累计次数 / 累计时长 / 坚持天数 / 最近一次。
     *
     * ## 为什么用 flatMapLatest 而不是给每个计划都算一份
     * 统计是「打开详情才需要」的数据。若在 [plans] 的 combine 里给 N 个计划各算一份，
     * 就是 N 条常驻的全表聚合查询——典型的「为了一个弹窗把列表接口拖垮」。
     * flatMapLatest 的语义正是「上游每来一个新值，切到它映射出的新 Flow 并取消上一个」，
     * 所以关闭详情（推 null）时 Room 的数据库订阅会立即被取消，零常驻开销。
     * 理由与 CalendarViewModel.panelTodos 一致。
     *
     * ## 为什么是 Flow 而不是一次性 suspend 查询
     * 详情开着的时候如果恰好有一个番茄结束落库，统计会自己刷新，不用「查一次 + 手动重查」。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val planStats: StateFlow<LinkStats?> = _statsPlanId
        .flatMapLatest { id ->
            if (id == null) flowOf<LinkStats?>(null)
            else pomodoroRepository.observeStatsByLink(PomodoroLinkType.PLAN, id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = null,
        )

    /** 打开（传计划 id）或关闭（传 null）详情统计。关闭时顺带停掉上游数据库订阅。 */
    fun setStatsPlan(planId: Long?) {
        _statsPlanId.value = planId
    }

    /** 新建或更新计划。existingId 非空为编辑态。 */
    fun upsert(
        name: String,
        mode: TimerMode,
        targetMinutes: Int,
        isDaily: Boolean,
        existingId: Long? = null,
    ) {
        viewModelScope.launch {
            // 只有 COUNTDOWN 存目标分钟，其余模式统一归零，避免脏数据误导聚合
            val target = if (mode == TimerMode.COUNTDOWN) targetMinutes else 0
            val plan = if (existingId == null) {
                Plan(name = name.trim(), mode = mode.code, targetMinutes = target, isDaily = isDaily)
            } else {
                val old = planRepository.getById(existingId) ?: return@launch
                old.copy(name = name.trim(), mode = mode.code, targetMinutes = target, isDaily = isDaily)
            }
            if (existingId == null) planRepository.insert(plan) else planRepository.update(plan)
        }
    }

    /** 删除计划；Repository 内部同事务解除番茄记录对它的引用。 */
    fun delete(plan: Plan) {
        viewModelScope.launch { planRepository.delete(plan) }
    }

    /**
     * 启动某计划的计时并跳转专注页。
     * 按 mode 现算 plannedMinutes，再发 intent 给前台服务，最后回调 [onStarted] 切 Tab。
     */
    fun start(context: Context, row: PlanRow, onStarted: () -> Unit) {
        val planned = when (row.mode) {
            TimerMode.COUNTDOWN -> row.plan.targetMinutes
            TimerMode.POMODORO -> workMinutes.value
            TimerMode.COUNTUP -> 0
        }
        PomodoroService.start(
            context = context,
            plannedMinutes = planned,
            link = PomodoroLinkType.PLAN,
            linkId = row.plan.id,
            mode = row.mode,
        )
        onStarted()
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_WORK_MINUTES = 25
    }
}

/**
 * 计划列表的一行（UI 渲染用的投影）。
 * 把 [Plan] 模板 + 今日进度 + 运行态 + 解析后的 [TimerMode] 打包，UI 不再自己算。
 */
data class PlanRow(
    val plan: Plan,
    val mode: TimerMode,
    val targetMinutes: Int,
    val todayMinutes: Int,
    val isRunning: Boolean,
    val isDaily: Boolean,
)
