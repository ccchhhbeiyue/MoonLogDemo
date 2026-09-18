package com.example.demo.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.demo.DemoApplication
import com.example.demo.di.DemoViewModelFactory
import com.example.demo.ui.theme.IconArrowBack
import kotlin.math.roundToInt

/**
 * 统计页（P8 二级页面）。
 *
 * 布局自上而下：周期折线趋势 → 四个关键数字 → 波动提示。
 * 全部数据来自 [StatsViewModel.state] 一份快照，页面本身无状态、无计算。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val factory = remember(context) {
        DemoViewModelFactory((context.applicationContext as DemoApplication).container)
    }
    val viewModel: StatsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(IconArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CycleTrendSection(state)
            KeyNumbersSection(state)
            if (state.isIrregular) {
                Text(
                    text = "你的周期波动较大（标准差超过 5 天），预测仅供参考；若长期不规律建议咨询医生。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 折线趋势区：有点就画图，没点给空态文案 */
@Composable
private fun CycleTrendSection(state: StatsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "周期长度趋势（近 ${state.cycleLengths.size} 次）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.cycleLengths.isNotEmpty()) {
            CycleTrendChart(
                values = state.cycleLengths,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            Text(
                text = "虚线为平均周期 ${state.averageCycle?.formatDays() ?: "--"}；点为每次周期（天）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "暂无足够数据：至少需要 2 条经期记录才能算出周期长度。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 关键数字区：2×2 卡片网格（用两行 Row 实现，避免为四个格子引 Grid） */
@Composable
private fun KeyNumbersSection(state: StatsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "平均周期",
                value = state.averageCycle?.formatDays() ?: "--",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "规律性评分",
                value = state.regularityScore?.toString() ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "番茄完成率",
                value = state.pomodoroCompletionRate.formatPercent(state.pomodoroCompleted, state.pomodoroTotal),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "待办完成率",
                value = state.todoCompletionRate.formatPercent(state.todoDone, state.todoTotal),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 平均周期保留一位小数：28.0 天比 28 天更能看出「这是算出来的均值」 */
private fun Double.formatDays(): String = "%.1f 天".format(this)

/** 完成率显示成「50%（1/2）」：百分比看水平，括号里的原始计数防误读 */
private fun Float?.formatPercent(done: Int, total: Int): String =
    if (this == null) "--" else "${(this * 100).roundToInt()}%（$done/$total）"
