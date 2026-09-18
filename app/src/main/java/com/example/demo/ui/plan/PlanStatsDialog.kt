package com.example.demo.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.demo.data.local.dao.LinkStats
import com.example.demo.data.local.entity.Plan
import java.time.LocalDate

/**
 * 计划详情统计对话框（P10-7）。入口是计划卡片右侧的柱状图图标。
 *
 * @param plan  要展示的计划；创建日期直接取自它的 [Plan.createdLocalDate]，不用查库。
 * @param stats 全历史统计；null 表示上游 Flow 还没吐出第一帧（打开瞬间），显示加载占位。
 *
 * ## 只是投影，不含任何计算
 * 所有口径（时长/天数只算已完成、放弃次数=总数−完成数）都在 DAO 的 SQL 与 [LinkStats] 里定死了，
 * 这里只做格式化。UI 层不该有第二套统计口径，否则两处数字迟早对不上——
 * 等价于后端「聚合逻辑留在 SQL/Service，Controller 只负责渲染 DTO」。
 *
 * ## 空态刻意不显示一排 0
 * 新建的计划没有任何记录时，展示「0 次 / 0 分钟 / 0 天」是在浪费屏幕且显得功能坏了，
 * 改成一句引导文案 + 创建日期，告诉用户怎么让它有数据。
 */
@Composable
fun PlanStatsDialog(
    plan: Plan,
    stats: LinkStats?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${plan.name} · 统计") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    stats == null -> Text(
                        text = "统计加载中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    stats.completedCount == 0 -> {
                        Text(
                            text = "还没有完成的专注记录。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "回列表点「开始」跑完一轮，这里就会出现累计次数、累计时长和坚持天数。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        StatRow("创建日期", formatDate(plan.createdLocalDate()))
                    }

                    else -> {
                        StatRow("累计次数", "${stats.completedCount} 次")
                        StatRow("累计时长", formatMinutes(stats.totalMinutes))
                        StatRow("坚持天数", "${stats.activeDays} 天")
                        // 日均只在有数据时才有意义；activeDays 为 0 时上面已走空态分支，不会除零
                        StatRow("日均时长", formatMinutes(stats.totalMinutes / stats.activeDays))

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))

                        StatRow(
                            "最近一次",
                            stats.lastEpochDay?.let { formatDate(LocalDate.ofEpochDay(it.toLong())) } ?: "—",
                        )
                        StatRow("创建日期", formatDate(plan.createdLocalDate()))
                        if (stats.abandonedCount > 0) {
                            StatRow("中途放弃", "${stats.abandonedCount} 次")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 一行统计：左标签右数值。用 weight 撑开而非固定宽度，长标签不会被截断。 */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 分钟 → 人类可读时长。
 * 满整点省略分钟（"2 小时" 而不是 "2 小时 0 分钟"），不足一小时只显示分钟。
 */
private fun formatMinutes(total: Int): String = when {
    total <= 0 -> "0 分钟"
    total < 60 -> "$total 分钟"
    total % 60 == 0 -> "${total / 60} 小时"
    else -> "${total / 60} 小时 ${total % 60} 分钟"
}

/** 日期格式沿用项目惯例（字符串模板，不引 DateTimeFormatter）：见 CalendarScreen.formatFull */
private fun formatDate(date: LocalDate): String = "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
