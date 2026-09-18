package com.example.demo.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.demo.DemoApplication
import com.example.demo.data.local.entity.Plan
import com.example.demo.di.DemoViewModelFactory
import com.example.demo.domain.model.TimerMode
import com.example.demo.ui.theme.IconAdd
import com.example.demo.ui.theme.IconStats

/**
 * 计划页（P10-4）。底部 Tab「计划」的槽位内容。
 *
 * ## 职责：计划管理 + 计时启动器
 * - 列表卡片展示每个计划的名称、模式、目标、今日进度；
 * - FAB 新建、点卡片编辑（含删除），走 [PlanEditDialog]；
 * - 卡片右侧柱状图图标看全历史统计（P10-7），走 [PlanStatsDialog]；
 * - 「开始」按钮启动该计划的计时并跳转专注 Tab（复用其暂停/恢复/停止 UI）。
 *   正在进行中的计划按钮变为「进行中」，点击同样跳专注页查看/控制。
 *
 * 计时真相在前台服务，本页只发起 intent；进度靠当日 session 聚合现算（见 [PlanViewModel]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    onNavigateToFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val factory = remember(context) {
        DemoViewModelFactory((context.applicationContext as DemoApplication).container)
    }
    val viewModel: PlanViewModel = viewModel(factory = factory)

    val rows by viewModel.plans.collectAsStateWithLifecycle()
    // 详情统计：只有对话框开着时上游才真正在查库（见 PlanViewModel.planStats 的 flatMapLatest）
    val stats by viewModel.planStats.collectAsStateWithLifecycle()

    // 新建 / 编辑对话框互斥
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Plan?>(null) }
    // 统计对话框；与上面两个互斥（三个变量同时最多一个非空/为 true）
    var statsPlan by remember { mutableStateOf<Plan?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("计划") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(IconAdd, contentDescription = "新建计划")
            }
        },
    ) { innerPadding ->
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "还没有计划，点右下角 + 新建一个。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(rows, key = { it.plan.id }) { row ->
                    PlanCard(
                        row = row,
                        onClick = { editing = row.plan },
                        onStart = { viewModel.start(context, row, onNavigateToFocus) },
                        onStats = {
                            // 先订阅再弹窗：顺序反了会先渲染一帧 null（显示「加载中」）
                            viewModel.setStatsPlan(row.plan.id)
                            statsPlan = row.plan
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (creating) {
        PlanEditDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, mode, target, isDaily ->
                viewModel.upsert(name, mode, target, isDaily)
                creating = false
            },
        )
    }

    editing?.let { plan ->
        PlanEditDialog(
            initial = plan,
            onDismiss = { editing = null },
            onSave = { name, mode, target, isDaily ->
                viewModel.upsert(name, mode, target, isDaily, existingId = plan.id)
                editing = null
            },
            onDelete = {
                viewModel.delete(plan)
                editing = null
            },
        )
    }

    statsPlan?.let { plan ->
        PlanStatsDialog(
            plan = plan,
            stats = stats,
            onDismiss = {
                statsPlan = null
                // 推 null 让 flatMapLatest 切到 flowOf(null)，Room 的数据库订阅随即取消
                viewModel.setStatsPlan(null)
            },
        )
    }
}

/**
 * 单个计划的卡片。
 *
 * 左侧信息列（名称 + 模式副标题 + 今日进度），右侧统计图标 + 启动按钮。
 * COUNTDOWN 额外显示 x/y 分钟与百分比环；COUNTUP/POMODORO 只显示「今日 x 分钟」。
 *
 * 统计用 IconButton 而不是长按：长按在 Compose 里要 combinedClickable + 实验性 API，
 * 而且不可发现（界面上没有任何线索告诉用户能长按）。显式图标多占 40dp，换来可用性。
 */
@Composable
private fun PlanCard(
    row: PlanRow,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TodayProgress(row = row)
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onStats) {
                Icon(
                    imageVector = IconStats,
                    contentDescription = "${row.plan.name} 的统计",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (row.isRunning) {
                OutlinedButton(onClick = onStart) { Text("进行中") }
            } else {
                Button(onClick = onStart) { Text("开始") }
            }
        }
    }
}

/** 副标题：模式 + 目标（COUNTUP 无目标，POMODORO 指向设置页）；每日计划加前缀标记。 */
private fun subtitle(row: PlanRow): String {
    val base = when (row.mode) {
        TimerMode.COUNTDOWN -> "${row.targetMinutes} 分钟 · 倒计时"
        TimerMode.POMODORO -> "番茄钟 · 设置页时长"
        TimerMode.COUNTUP -> "计时 · 无目标"
    }
    return if (row.isDaily) "每日 · $base" else base
}

/** 今日进度：倒计时显示 x/y 与百分比环；其余模式只显示已走分钟。 */
@Composable
private fun TodayProgress(row: PlanRow) {
    if (row.mode == TimerMode.COUNTDOWN && row.targetMinutes > 0) {
        val fraction = (row.todayMinutes.toFloat() / row.targetMinutes).coerceIn(0f, 1f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 4.dp,
                )
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "今日 ${row.todayMinutes}/${row.targetMinutes} 分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Text(
            text = "今日 ${row.todayMinutes} 分钟",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
