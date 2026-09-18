package com.example.demo.ui.calendar

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.demo.DemoApplication
import com.example.demo.data.local.entity.PeriodRecord
import com.example.demo.data.local.entity.Todo
import com.example.demo.di.DemoViewModelFactory
import com.example.demo.domain.model.PredictionConfidence
import com.example.demo.domain.model.PredictionResult
import com.example.demo.domain.model.Symptom
import com.example.demo.ui.shared.SelectedDateViewModel
import com.example.demo.ui.theme.IconArrowBack
import com.example.demo.ui.theme.IconArrowForward
import com.example.demo.ui.theme.IconSettings
import com.example.demo.ui.theme.LocalPeriodColors
import com.example.demo.ui.theme.PlanDotBrown
import com.example.demo.ui.theme.TodoDotBlue
import com.example.demo.ui.todo.TodoEditDialog
import com.example.demo.ui.todo.TodoViewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 可翻月的范围：当前月前后各 24 个月，共 49 个月。demo 足够，正式项目可放宽。 */
private const val MONTH_RANGE = 24L

/** 星期标题从周日开始，与日历网格的列顺序对齐。 */
private val FIRST_DAY_OF_WEEK = DayOfWeek.SUNDAY

/** 跨月补位日（InDate/OutDate）的整体透明度 */
private const val OUT_MONTH_ALPHA = 0.4f

/** 经期实色带厚度占格高的比例：比整格窄，取 2/3（即缩小三分之一） */
private const val BAND_HEIGHT_FRACTION = 2f / 3f

/** 圆形标记（选中/今天/排卵/选中环）直径占格宽的比例：缩小到一半 */
private const val MARK_SIZE_FRACTION = 0.5f

/** 日期下方内容小点（待办蓝/计划棕）的直径 */
private val DOT_SIZE = 5.dp

/** 记录/编辑对话框的目标：新建（只有开始日）或编辑（带原记录） */
private sealed interface PeriodDialogState {
    data class New(val startDate: LocalDate) : PeriodDialogState
    data class Edit(val record: PeriodRecord) : PeriodDialogState
}

/**
 * 日历页（首页）。P3 在 P2 骨架上叠加经期实色渲染与记录/编辑/删除交互。
 *
 * ## 两个 ViewModel 的作用域对比
 * - [CalendarViewModel]：navigation-scoped，提供经期记录集合与 CRUD；
 * - [SelectedDateViewModel]：activity-scoped，"选中的那一天"跨 Tab 共享。
 *
 * ## 经期渲染的数据流
 * periodRecords（Room Flow → StateFlow）
 *   → [expandPeriodSpans] 展开成 Map<LocalDate, PeriodDaySpan>（O(1) 查表）
 *   → dayContent 里 spans[day.date] 决定该格是否染实色、染什么圆角。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateToStats: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val factory = remember(context) {
        DemoViewModelFactory((context.applicationContext as DemoApplication).container)
    }
    val viewModel: CalendarViewModel = viewModel(factory = factory)
    // 复用待办的 CRUD 逻辑（upsert 收口在 TodoViewModel），避免在日历页复制一份保存代码
    val todoViewModel: TodoViewModel = viewModel(factory = factory)

    val activity = LocalActivity.current as? ViewModelStoreOwner
        ?: error("CalendarScreen 必须宿主在实现 ViewModelStoreOwner 的 Activity 内")
    val selectedDateViewModel: SelectedDateViewModel = viewModel(viewModelStoreOwner = activity)

    val periodRecords by viewModel.periodRecords.collectAsStateWithLifecycle()
    val selectedDate by selectedDateViewModel.selectedDate.collectAsStateWithLifecycle()
    val spans = remember(periodRecords) { expandPeriodSpans(periodRecords) }
    val prediction by viewModel.prediction.collectAsStateWithLifecycle()
    val showFertile by viewModel.showFertileWindow.collectAsStateWithLifecycle()
    // 开关一变就重建 marks Map：隐藏时 Map 里根本没有易孕/排卵日期，DayCell 查表自然 miss
    val predictionMarks = remember(prediction, showFertile) { buildPredictionMarks(prediction, showFertile) }
    val panelTodos by viewModel.panelTodos.collectAsStateWithLifecycle()
    val panelPlans by viewModel.panelPlans.collectAsStateWithLifecycle()
    val todoDotDates by viewModel.todoDotDates.collectAsStateWithLifecycle()
    val planDotStart by viewModel.planDotStart.collectAsStateWithLifecycle()

    // 选中日一变就把它推进 CalendarViewModel，驱动 panelTodos 用 flatMapLatest 换订阅对应日期的待办
    LaunchedEffect(selectedDate) { viewModel.setPanelDate(selectedDate) }

    val today = remember { LocalDate.now() }
    val currentMonth = remember { YearMonth.now() }
    val calendarState = rememberCalendarState(
        startMonth = currentMonth.minusMonths(MONTH_RANGE),
        endMonth = currentMonth.plusMonths(MONTH_RANGE),
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = FIRST_DAY_OF_WEEK,
    )
    val scope = rememberCoroutineScope()
    val visibleMonth = calendarState.firstVisibleMonth.yearMonth

    // 可见月一变就推进 VM，切换月格待办小点的查询区间（蓝点）
    LaunchedEffect(visibleMonth) { viewModel.setVisibleMonth(visibleMonth) }

    var dialogState by remember { mutableStateOf<PeriodDialogState?>(null) }
    var confirmPredicted by remember { mutableStateOf(false) }
    var creatingTodo by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            scope.launch { calendarState.animateScrollToMonth(visibleMonth.minusMonths(1)) }
                        }) {
                            Icon(IconArrowBack, contentDescription = "上个月")
                        }
                        Text(
                            text = formatMonth(visibleMonth),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(onClick = {
                            scope.launch { calendarState.animateScrollToMonth(visibleMonth.plusMonths(1)) }
                        }) {
                            Icon(IconArrowForward, contentDescription = "下个月")
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToStats) { Text("统计") }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(IconSettings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { innerPadding ->
        // 日历本身固定不滚；内容变多时改由当日面板内部滚动（见 DayDetailPanel 的 verticalScroll），
        // 这样日历/预测卡始终可见，底部面板也不会被裁剪。
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            WeekdayHeaderRow()

            HorizontalCalendar(
                state = calendarState,
                modifier = Modifier.fillMaxWidth(),
                dayContent = { day ->
                    DayCell(
                        day = day,
                        span = spans[day.date],
                        mark = predictionMarks[day.date],
                        isSelected = day.date == selectedDate,
                        isToday = day.date == today,
                        showTodoDot = day.date in todoDotDates,
                        showPlanDot = planDotStart?.let { !day.date.isBefore(it) } == true,
                        onClick = {
                            selectedDateViewModel.selectDate(day.date)
                            // 点中「预测经期」且当天无真实记录 → 弹一键确认转真实
                            if (spans[day.date] == null &&
                                predictionMarks[day.date]?.kind == PredictionMark.Kind.PERIOD
                            ) {
                                confirmPredicted = true
                            }
                        }
                    )
                }
            )

            PredictionCard(prediction = prediction)

            HorizontalDivider()

            DayDetailPanel(
                modifier = Modifier.weight(1f),
                selectedDate = selectedDate,
                span = spans[selectedDate],
                isToday = selectedDate == today,
                todos = panelTodos,
                onToggleTodo = { todo -> viewModel.toggleTodoDone(todo) },
                onAddTodo = { creatingTodo = true },
                plans = panelPlans,
                onRecord = { dialogState = PeriodDialogState.New(selectedDate) },
                onEdit = { record -> dialogState = PeriodDialogState.Edit(record) },
                onDelete = { record -> viewModel.deleteRecord(record) },
                onNavigateToHistory = onNavigateToHistory,
            )
        }
    }

    dialogState?.let { state ->
        val startDate = when (state) {
            is PeriodDialogState.New -> state.startDate
            is PeriodDialogState.Edit -> state.record.startDate
        }
        val editing = (state as? PeriodDialogState.Edit)?.record
        RecordPeriodDialog(
            startDate = startDate,
            initialRecord = editing,
            onDismiss = { dialogState = null },
            onSave = { endDate, flow, symptoms, note ->
                viewModel.upsertRecord(
                    startDate = startDate,
                    endDate = endDate,
                    flow = flow,
                    symptoms = symptoms,
                    note = note,
                    existingId = editing?.id,
                )
                dialogState = null
            },
            onDelete = editing?.let { record ->
                {
                    viewModel.deleteRecord(record)
                    dialogState = null
                }
            },
        )
    }

    // 点预测经期日 → 一键确认转真实记录（计划 §4.4 核心交互闭环）
    prediction?.let { p ->
        if (confirmPredicted) {
            AlertDialog(
                onDismissRequest = { confirmPredicted = false },
                title = { Text("预计这天会来月经，对吗？") },
                text = {
                    Text(
                        "预测下次经期为 ${p.nextPeriodStart.monthValue}月${p.nextPeriodStart.dayOfMonth}日 ~ " +
                            "${p.nextPeriodEnd.monthValue}月${p.nextPeriodEnd.dayOfMonth}日。" +
                            "确认后将转为真实记录，用于后续更准的预测。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.upsertRecord(
                            startDate = p.nextPeriodStart,
                            endDate = p.nextPeriodEnd,
                            flow = null,
                            symptoms = emptySet(),
                            note = null,
                        )
                        confirmPredicted = false
                    }) { Text("确认") }
                },
                dismissButton = { TextButton(onClick = { confirmPredicted = false }) { Text("取消") } },
            )
        }
    }

    // 看着选中日期直接新增待办：截止日默认填选中日
    if (creatingTodo) {
        TodoEditDialog(
            initial = null,
            initialDueDate = selectedDate,
            onDismiss = { creatingTodo = false },
            onSave = { title, description, dueDate, priority, category ->
                todoViewModel.upsert(title, description, dueDate, priority, category)
                creatingTodo = false
            },
        )
    }
}

/**
 * 单个日期格子。P3 在 P2 三态（选中/今天/跨月淡化）基础上叠加经期实色带。
 *
 * 渲染优先级（写清避免叠加混乱）：
 * 1. 背景：有 [span] → 经期实色带（厚度 2/3 格高、圆角由 role+列位置决定）；无 span 且选中 → 浅灰实心圆；
 * 2. 描边：今天 → todayRing 圆环（无论是否经期，今天必须可见）；
 *    选中且处于经期内 → 白色圆环（实色带上用白环表示选中）；
 * 3. 跨月补位日整体降低透明度。
 */
@Composable
private fun BoxScope.DayCell(
    day: CalendarDay,
    span: PeriodDaySpan?,
    mark: PredictionMark?,
    isSelected: Boolean,
    isToday: Boolean,
    showTodoDot: Boolean,
    showPlanDot: Boolean,
    onClick: () -> Unit
) {
    val isOutMonth = day.position != DayPosition.MonthDate
    val periodColors = LocalPeriodColors.current

    val textColor = when {
        span != null -> periodColors.onPeriod
        mark?.kind == PredictionMark.Kind.OVULATION -> MaterialTheme.colorScheme.onSurface
        isSelected -> MaterialTheme.colorScheme.onSurface
        isOutMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .then(if (isOutMonth) Modifier.alpha(OUT_MONTH_ALPHA) else Modifier)
            .clickable(enabled = !isOutMonth, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 背景层：经期实色带（厚度 2/3 格高、垂直居中）或选中实心圆
        if (span != null) {
            val column = (day.date.dayOfWeek.value - FIRST_DAY_OF_WEEK.value + 7) % 7
            val roundLeft = span.role == PeriodDayRole.SINGLE ||
                span.role == PeriodDayRole.START || column == 0
            val roundRight = span.role == PeriodDayRole.SINGLE ||
                span.role == PeriodDayRole.END || column == 6
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .fillMaxHeight(BAND_HEIGHT_FRACTION)
                    .clip(spanShape(roundLeft, roundRight))
                    .background(periodColors.periodConfirmed)
            )
        } else if (mark != null) {
            // 底色带：预测经期=浅粉；易孕窗与排卵日=浅紫（排卵日垫易孕底色，保证带子连续不断开）
            val bandColor = when (mark.kind) {
                PredictionMark.Kind.PERIOD -> periodColors.periodPredicted
                else -> periodColors.fertileWindow
            }
            val column = (day.date.dayOfWeek.value - FIRST_DAY_OF_WEEK.value + 7) % 7
            val roundLeft = mark.role == PeriodDayRole.SINGLE ||
                mark.role == PeriodDayRole.START || column == 0
            val roundRight = mark.role == PeriodDayRole.SINGLE ||
                mark.role == PeriodDayRole.END || column == 6
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .fillMaxHeight(BAND_HEIGHT_FRACTION)
                    .clip(spanShape(roundLeft, roundRight))
                    .background(bandColor)
            )
            // 排卵日在底色带之上再叠一个浅黄半圆
            if (mark.kind == PredictionMark.Kind.OVULATION) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(MARK_SIZE_FRACTION)
                        .clip(CircleShape)
                        .background(periodColors.ovulation)
                )
            }
        } else if (isSelected) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(MARK_SIZE_FRACTION)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        // 描边层：今天圆环；选中且处于经期内用白环
        if (isToday) {
            Box(Modifier.align(Alignment.Center).fillMaxSize(MARK_SIZE_FRACTION).border(1.5.dp, periodColors.todayRing, CircleShape))
        } else if (isSelected && span != null) {
            Box(Modifier.align(Alignment.Center).fillMaxSize(MARK_SIZE_FRACTION).border(2.dp, periodColors.onPeriod, CircleShape))
        } else if (isSelected && mark != null) {
            Box(Modifier.align(Alignment.Center).fillMaxSize(MARK_SIZE_FRACTION).border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
        }

        Text(
            text = day.date.dayOfMonth.toString(),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        // 内容小点行：蓝点=当天有待办截止；棕点=当天有每日计划生效；两者都有→并排
        if (showTodoDot || showPlanDot) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (showTodoDot) {
                    Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(TodoDotBlue))
                }
                if (showPlanDot) {
                    Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(PlanDotBrown))
                }
            }
        }
    }
}

/**
 * 经期实色带两端圆角。roundLeft/roundRight 为真时该端取 50%（= 带高一半）成半圆收口。
 * 换行处（column==0 / column==6）即使逻辑上是 MIDDLE 也收半圆，避免直切边。
 */
private fun spanShape(roundLeft: Boolean, roundRight: Boolean): Shape = RoundedCornerShape(
    topStartPercent = if (roundLeft) 50 else 0,
    bottomStartPercent = if (roundLeft) 50 else 0,
    topEndPercent = if (roundRight) 50 else 0,
    bottomEndPercent = if (roundRight) 50 else 0,
)

/** 预测类标记（非已确认经期）：种类 + 该日在区间中的位置（决定色带圆角） */
private data class PredictionMark(val kind: Kind, val role: PeriodDayRole) {
    enum class Kind { PERIOD, FERTILE, OVULATION }
}

/** 某一天在 [start]..[end] 区间中的位置（首/中/尾/单日），决定色带圆角 */
private fun spanRole(date: LocalDate, start: LocalDate, end: LocalDate): PeriodDayRole = when {
    date == start && date == end -> PeriodDayRole.SINGLE
    date == start -> PeriodDayRole.START
    date == end -> PeriodDayRole.END
    else -> PeriodDayRole.MIDDLE
}

/**
 * 把预测结果展开成「日期 → 预测标记」Map，供 dayContent 查表（O(1)）。
 * 写入顺序：易孕窗 → 排卵日覆盖 → 预测经期覆盖，故同日期优先级 预测经期 > 排卵 > 易孕。
 * 已确认经期不在此 Map（由 spans 负责，且渲染时 spans 优先于 mark）。
 *
 * @param showFertile 设置页开关：false 时不产出 FERTILE/OVULATION 标记（易孕窗默认隐藏），
 *   预测经期标记不受影响。在「生成」阶段过滤而不是在 DayCell 里加 if，
 *   是为了让隐藏态的查表直接 miss，渲染路径零分支。
 */
private fun buildPredictionMarks(p: PredictionResult?, showFertile: Boolean): Map<LocalDate, PredictionMark> {
    if (p == null) return emptyMap()
    val marks = mutableMapOf<LocalDate, PredictionMark>()
    if (showFertile) {
        var fertileDay = p.fertileWindowStart
        while (!fertileDay.isAfter(p.fertileWindowEnd)) {
            marks[fertileDay] = PredictionMark(PredictionMark.Kind.FERTILE, spanRole(fertileDay, p.fertileWindowStart, p.fertileWindowEnd))
            fertileDay = fertileDay.plusDays(1)
        }
        marks[p.ovulationDay] = PredictionMark(
            PredictionMark.Kind.OVULATION,
            spanRole(p.ovulationDay, p.fertileWindowStart, p.fertileWindowEnd),
        )
    }
    var day = p.nextPeriodStart
    while (!day.isAfter(p.nextPeriodEnd)) {
        marks[day] = PredictionMark(PredictionMark.Kind.PERIOD, spanRole(day, p.nextPeriodStart, p.nextPeriodEnd))
        day = day.plusDays(1)
    }
    return marks
}

/** 星期标题行（日一二三四五六），7 等分，与下方日期网格的列对齐。 */
@Composable
private fun WeekdayHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdayLabels(FIRST_DAY_OF_WEEK).forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 当日详情面板（P3）：显示选中日期的经期状态，并提供记录/编辑/删除/历史入口。
 * P5/P6/P7 会在此继续叠加待办/工作/专注信息。
 */
@Composable
private fun DayDetailPanel(
    selectedDate: LocalDate,
    span: PeriodDaySpan?,
    isToday: Boolean,
    todos: List<Todo>,
    onToggleTodo: (Todo) -> Unit,
    onAddTodo: () -> Unit,
    plans: List<PlanDayItem>,
    onRecord: () -> Unit,
    onEdit: (PeriodRecord) -> Unit,
    onDelete: (PeriodRecord) -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 面板内部纵向滚动：日历页整体不滚，内容变多时只在本面板区域内滚动，底部不被裁剪。
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = buildString {
                append(formatDateChinese(selectedDate))
                if (isToday) append(" · 今天")
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (span == null) {
            Text(
                text = "这一天没有经期记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = buildString {
                    append("经期第 ${span.dayIndex} 天 / 共 ${span.totalDays} 天")
                    span.record.flow?.let { append(" · 流量${flowLabel(it)}") }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = LocalPeriodColors.current.periodConfirmed
            )
            val symptomLabels = Symptom.parseCodes(span.record.symptoms).joinToString("、") { it.label }
            if (symptomLabels.isNotEmpty()) {
                Text(
                    text = "症状：$symptomLabels",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            span.record.note?.let {
                Text(
                    text = "备注：$it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row {
            if (span == null) {
                TextButton(onClick = onRecord) { Text("+ 记录经期") }
            } else {
                TextButton(onClick = { onEdit(span.record) }) { Text("编辑") }
                TextButton(onClick = { onDelete(span.record) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onNavigateToHistory) { Text("历史记录") }
        }

        // 当天待办（P5）：日历下方直接展示 / 勾选选中日的待办
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "当天待办（${todos.count { !it.isDone }} 项未完成）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAddTodo) { Text("+ 新增待办") }
        }
        if (todos.isEmpty()) {
            Text(
                text = "这一天没有待办。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            todos.forEach { todo ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = todo.isDone, onCheckedChange = { onToggleTodo(todo) })
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                        color = if (todo.isDone) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 当天每日计划（P11）：取代原「当天工作」段；每日计划从创建日起每天重复出现
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "当天每日计划（${plans.size}）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (plans.isEmpty()) {
            Text(
                text = "这一天没有每日计划，创建计划时可开启「每日计划」。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            plans.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(PlanDotBrown))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.plan.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (item.targetMinutes > 0) {
                            "${item.minutes}/${item.targetMinutes} 分钟"
                        } else {
                            "${item.minutes} 分钟"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 首页预测提示卡片：倒计时 + 置信度 + 免责声明（计划 §6.2 / §4.5） */
@Composable
private fun PredictionCard(prediction: PredictionResult?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (prediction == null) {
            Text(
                text = "暂无经期记录，先记录一次月经才能开始预测。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "距下次月经还有 ${prediction.daysUntilNextPeriod} 天 · " +
                    "预计 ${prediction.nextPeriodStart.monthValue}月${prediction.nextPeriodStart.dayOfMonth}日",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    append(confidenceLabel(prediction.confidence))
                    if (prediction.cycleStdDev > 5.0) append(" · 周期波动较大，建议咨询医生")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "预测结果仅供参考，不构成医疗建议",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

private fun confidenceLabel(confidence: PredictionConfidence): String = when (confidence) {
    PredictionConfidence.LOW -> "置信度低"
    PredictionConfidence.MEDIUM -> "置信度中"
    PredictionConfidence.HIGH -> "置信度高"
}

// ===== 格式化与标签（纯函数，无状态） =====

private fun flowLabel(flow: Int): String = when (flow) {
    1 -> "轻"
    2 -> "中"
    3 -> "重"
    else -> "未记录"
}

private fun formatMonth(yearMonth: YearMonth): String =
    "${yearMonth.year}年${yearMonth.monthValue}月"

private fun formatDateChinese(date: LocalDate): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日 星期${weekdayCn(date.dayOfWeek)}"

private fun weekdayCn(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.SUNDAY -> "日"
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
}

/** 从 firstDayOfWeek 起生成 7 个星期标签，保证与日历网格的列顺序一致。 */
private fun weekdayLabels(firstDayOfWeek: DayOfWeek): List<String> =
    (0..6).map { weekdayCn(firstDayOfWeek.plus(it.toLong())) }
