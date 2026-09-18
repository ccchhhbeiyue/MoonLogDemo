package com.example.demo.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.local.entity.Plan
import com.example.demo.data.local.entity.PeriodRecord
import com.example.demo.data.local.entity.Todo
import com.example.demo.data.repository.PeriodRepository
import com.example.demo.data.repository.PlanRepository
import com.example.demo.data.repository.PomodoroRepository
import com.example.demo.data.repository.SettingRepository
import com.example.demo.data.repository.TodoRepository
import com.example.demo.domain.model.CycleSample
import com.example.demo.domain.model.PomodoroLinkType
import com.example.demo.domain.model.PredictionResult
import com.example.demo.domain.model.Symptom
import com.example.demo.domain.model.TimerMode
import com.example.demo.domain.predictor.CyclePredictor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 经期记录的 ViewModel（日历页与历史列表页共用）。
 *
 * ## 职责边界（对照后端 = Controller）
 * - 暴露可订阅的经期记录集合 [periodRecords]；
 * - 把 UI 的「保存 / 删除」翻译成对 Repository 的调用（[upsertRecord] / [deleteRecord]）；
 * - 不写 SQL、不校验日期区间——校验在 Repository（requireValidRange），SQL 在 DAO。
 *
 * ## 为什么日历页和历史页共用这一个类
 * 两者操作的是同一份数据（period_record），CRUD 语义完全相同，
 * 只是展示形态不同（网格 vs 列表）。拆成两个 ViewModel 只会复制粘贴同一组方法。
 * 它们各自是 navigation-scoped 的独立实例，但背后订阅同一条 Room Flow，数据天然一致。
 */
class CalendarViewModel(
    private val periodRepository: PeriodRepository,
    private val settingRepository: SettingRepository,
    private val todoRepository: TodoRepository,
    private val planRepository: PlanRepository,
    private val pomodoroRepository: PomodoroRepository,
) : ViewModel() {

    /**
     * 全部经期记录（升序），订阅型状态。
     * 日历页把它展开成「日期 → 经期区间」的 Map 做渲染；历史页直接按行展示。
     * demo 数据量小，全量观察比按月份区间订阅更简单，且翻月不用重新订阅。
     */
    val periodRecords: StateFlow<List<PeriodRecord>> = periodRepository.observeAllAscending()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    /**
     * 周期预测结果（实时计算、不落库）。
     *
     * 由「经期记录流」与「配置流」[combine] 后交给纯逻辑 [CyclePredictor]：
     * 任一上游变化（改记录 / 改默认周期）都会自动重算，无需手动刷新。
     * 类比后端：两个 CDC 订阅流 join 后过一道纯函数 service。
     *
     * 0 条记录时为 null，UI 据此显示「请先记录」且不渲染任何预测标记。
     * 这里负责 entity → [CycleSample] 的映射，守住 domain 不沾 Room 的边界。
     */
    val prediction: StateFlow<PredictionResult?> =
        combine(periodRepository.observeAllAscending(), settingRepository.observe()) { records, setting ->
            val samples = records.map { it.toCycleSample() }
            CyclePredictor.predict(
                samples = samples,
                today = LocalDate.now(),
                defaultCycleLength = setting.defaultCycleLength,
                defaultPeriodLength = setting.defaultPeriodLength,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = null,
        )

    /**
     * 是否显示易孕窗口与排卵日标记（设置页开关，默认隐藏）。
     *
     * 只影响月格渲染（CalendarScreen 据此决定 buildPredictionMarks 要不要产出
     * FERTILE/OVULATION 标记），不影响 [prediction] 的计算——算法照常跑，UI 选择性展示。
     * initialValue 取 false 与「默认隐藏」一致：首帧未到时也不闪现易孕窗。
     */
    val showFertileWindow: StateFlow<Boolean> = settingRepository.observe()
        .map { it.showFertileWindow }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = false,
        )

    /**
     * 新增或更新一条经期记录。
     *
     * @param existingId 非空表示编辑已有记录；为空表示新建。
     *   编辑时先读原记录再 [PeriodRecord.copy]，是为了**保留 createdAt**——
     *   若直接 new 一个 PeriodRecord 去 update，整行写回会把创建时间覆盖成当前时刻，
     *   审计字段就失去意义了。
     */
    fun upsertRecord(
        startDate: LocalDate,
        endDate: LocalDate?,
        flow: Int?,
        symptoms: Set<Symptom>,
        note: String?,
        existingId: Long? = null,
    ) {
        viewModelScope.launch {
            val symptomCodes = Symptom.toCodes(symptoms)
            if (existingId != null) {
                val original = periodRepository.getById(existingId) ?: return@launch
                periodRepository.update(
                    original.copy(
                        startDate = startDate,
                        endDate = endDate,
                        flow = flow,
                        symptoms = symptomCodes,
                        note = note,
                    )
                )
            } else {
                periodRepository.insert(
                    PeriodRecord(
                        startDate = startDate,
                        endDate = endDate,
                        flow = flow,
                        symptoms = symptomCodes,
                        note = note,
                    )
                )
            }
        }
    }

    fun deleteRecord(record: PeriodRecord) {
        viewModelScope.launch { periodRepository.delete(record) }
    }

    /**
     * 把预测的下次经期「一键确认」成真实记录（P4 交互）。
     * 用户在预测区间点确认后，以预测的起止日新建一条 record；
     * 它随即成为事实样本参与下一轮预测（预测标记消失、实色带出现）。
     * flow/symptoms/note 留空，让用户事后再编辑补充。
     */
    fun confirmPredictedPeriod(prediction: PredictionResult) {
        viewModelScope.launch {
            periodRepository.insert(
                PeriodRecord(
                    startDate = prediction.nextPeriodStart,
                    endDate = prediction.nextPeriodEnd,
                )
            )
        }
    }

    // 选中日期由 CalendarScreen 从 activity-scoped 的 SelectedDateViewModel 同步过来（见 setPanelDate）。
    // 必须声明在 panelTodos 之前：Kotlin 属性按声明顺序初始化，flatMapLatest 在初始化时就要读它。
    private val _panelDate = MutableStateFlow(LocalDate.now())

    /**
     * 当日面板要显示的待办（选中日期驱动）。
     *
     * ## 为什么用 flatMapLatest 而不是 combine
     * 选中日期 [_panelDate] 一变，就要「换一条」对应日期的 Room Flow 来订阅——旧日期的订阅必须取消。
     * flatMapLatest 正是「上游每来一个新值，就切换到它映射出的新 Flow，并取消上一个」的语义。
     * combine 是「多条固定流并行 join」，流的数量不能随值变化，不适用这里。
     * 类比后端：像根据参数动态改订阅的 topic，而非固定的多路 join。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val panelTodos: StateFlow<List<Todo>> = _panelDate
        .flatMapLatest { date -> todoRepository.observeByDate(date) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    /** CalendarScreen 在选中日变化时调用，把跨 Tab 共享的选中日同步进本 VM 以驱动 [panelTodos] */
    fun setPanelDate(date: LocalDate) {
        _panelDate.value = date
    }

    /** 当日面板里直接勾选/取消完成；完成时间仍由 Repository 收口 */
    fun toggleTodoDone(todo: Todo) {
        viewModelScope.launch { todoRepository.setDone(todo.id, !todo.isDone) }
    }

    /**
     * 当日面板的每日计划（P11，选中日驱动）：每日计划从创建日起每天重复出现。
     * 每行带「该日已完成分钟」（当日 link 到该计划的 session 聚合现算），与计划页同一口径。
     * 工作日志段已随 P11 下线，由本段取代。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val panelPlans: StateFlow<List<PlanDayItem>> = _panelDate
        .flatMapLatest { date ->
            combine(
                planRepository.observeAll(),
                pomodoroRepository.observeMinutesGroupedByLinkOnDate(PomodoroLinkType.PLAN, date)
                    .map { list -> list.associate { it.linkId to it.minutes } },
                settingRepository.observe(),
            ) { plans, minutesMap, setting ->
                plans.filter { it.isDaily && !it.createdLocalDate().isAfter(date) }
                    .map { plan ->
                        val mode = TimerMode.fromCode(plan.mode)
                        PlanDayItem(
                            plan = plan,
                            modeLabel = mode.label,
                            targetMinutes = when (mode) {
                                TimerMode.COUNTDOWN -> plan.targetMinutes
                                TimerMode.POMODORO -> setting.pomodoroWorkMin
                                TimerMode.COUNTUP -> 0
                            },
                            minutes = minutesMap[plan.id] ?: 0,
                        )
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    // 可见月份由 CalendarScreen 从日历滚动状态同步过来（见 setVisibleMonth），驱动月格小点的区间查询。
    private val _visibleMonth = MutableStateFlow(YearMonth.now())

    /** CalendarScreen 在可见月变化时调用，切换月格待办小点的查询区间 */
    fun setVisibleMonth(month: YearMonth) {
        _visibleMonth.value = month
    }

    /**
     * 可见月内待办截止日集合（月格蓝点）。
     * flatMapLatest：翻月即换新区间的 Room Flow、取消旧订阅，理由同 [panelTodos]。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val todoDotDates: StateFlow<Set<LocalDate>> = _visibleMonth
        .flatMapLatest { month ->
            todoRepository.observeByDateRange(month.atDay(1), month.atEndOfMonth())
                .map { list -> list.mapNotNull { it.dueDate }.toSet() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = emptySet(),
        )

    /**
     * 月格棕点起始日：每日计划里最早的创建日。
     * 每日计划从创建日起每天重复，多个计划的「有计划日」并集恰为 [最早创建日, ∞)，
     * 故 UI 只需判断 day >= 本日，无需逐日查询。
     */
    val planDotStart: StateFlow<LocalDate?> = planRepository.observeAll()
        .map { plans -> plans.filter { it.isDaily }.minOfOrNull { it.createdLocalDate() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = null,
        )

    /** entity → domain 样本的映射，见 [CycleSample] 注释（domain 层不沾 Room 依赖） */
    private fun PeriodRecord.toCycleSample() = CycleSample(
        start = startDate,
        periodLength = endDate?.let { ChronoUnit.DAYS.between(startDate, it).toInt() + 1 },
    )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * 当日面板里的一行每日计划（P11）。
 * 模板 + 解析后的模式标签 + 目标分钟（番茄钟取设置页）+ 该日已完成分钟打包，
 * UI 不再自己算；与计划页 PlanRow 同一投影思路。
 */
data class PlanDayItem(
    val plan: Plan,
    val modeLabel: String,
    val targetMinutes: Int,
    val minutes: Int,
)
