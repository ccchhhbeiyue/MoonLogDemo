package com.example.demo.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.demo.DemoApplication
import com.example.demo.data.local.entity.PeriodRecord
import com.example.demo.di.DemoViewModelFactory
import com.example.demo.domain.model.Symptom
import com.example.demo.ui.theme.IconArrowBack
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 经期历史列表页（二级页面，进入后覆盖底部导航栏）。
 *
 * 与日历页共用 [CalendarViewModel]：操作的是同一份 period_record，
 * 这里按开始日降序逐行展示（最新在前），点击行进入编辑对话框。
 * 空状态给出明确引导，避免一片空白让用户不知所措。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val factory = remember(context) {
        DemoViewModelFactory((context.applicationContext as DemoApplication).container)
    }
    val viewModel: CalendarViewModel = viewModel(factory = factory)
    val records by viewModel.periodRecords.collectAsStateWithLifecycle()
    val descending = remember(records) { records.reversed() }

    var editing by remember { mutableStateOf<PeriodRecord?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("经期历史") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(IconArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (descending.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "还没有经期记录。\n回到日历页点选某一天，「+ 记录经期」即可开始。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(descending, key = { it.id }) { record ->
                    PeriodHistoryItem(
                        record = record,
                        onClick = { editing = record },
                        onDelete = { viewModel.deleteRecord(record) }
                    )
                }
            }
        }
    }

    editing?.let { record ->
        RecordPeriodDialog(
            startDate = record.startDate,
            initialRecord = record,
            onDismiss = { editing = null },
            onSave = { endDate, flow, symptoms, note ->
                viewModel.upsertRecord(
                    startDate = record.startDate,
                    endDate = endDate,
                    flow = flow,
                    symptoms = symptoms,
                    note = note,
                    existingId = record.id,
                )
                editing = null
            },
            onDelete = {
                viewModel.deleteRecord(record)
                editing = null
            },
        )
    }
}

/** 单条历史记录卡片：区间 + 天数 + 流量 + 症状，右侧删除按钮 */
@Composable
private fun PeriodHistoryItem(
    record: PeriodRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val days = ChronoUnit.DAYS.between(record.startDate, record.endDate ?: record.startDate).toInt() + 1
    val symptomLabels = Symptom.parseCodes(record.symptoms).joinToString("、") { it.label }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${formatShort(record.startDate)} ~ " +
                        (record.endDate?.let { formatShort(it) } ?: "进行中"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        append("$days 天")
                        record.flow?.let { append(" · 流量${flowLabel(it)}") }
                        if (symptomLabels.isNotEmpty()) append(" · $symptomLabels")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun flowLabel(flow: Int): String = when (flow) {
    1 -> "轻"
    2 -> "中"
    3 -> "重"
    else -> "未记录"
}

private fun formatShort(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"
