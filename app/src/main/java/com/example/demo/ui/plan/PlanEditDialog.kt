package com.example.demo.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.demo.data.local.entity.Plan
import com.example.demo.domain.model.TimerMode

/**
 * 新建 / 编辑计划的表单对话框（P10-4）。范式对齐 [com.example.demo.ui.todo.TodoEditDialog]。
 *
 * @param initial 非空为编辑态（回填并显示删除）；为空为新建。
 * @param onSave  保存回调（含是否每日计划）；名称空白或倒计时目标非法时按钮已禁用，故此处只透传值。
 * @param onDelete 编辑态专属删除回调。
 *
 * ## 目标分钟输入的显隐
 * 只有 COUNTDOWN 需要用户填目标；POMODORO 的目标来自设置页专注时长（只读提示）；
 * COUNTUP 无目标（隐藏输入）。这样表单只暴露当前模式真正需要的字段，避免用户填了却不生效。
 */
@Composable
fun PlanEditDialog(
    initial: Plan?,
    onDismiss: () -> Unit,
    onSave: (name: String, mode: TimerMode, targetMinutes: Int, isDaily: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var mode by remember { mutableStateOf(TimerMode.fromCode(initial?.mode ?: TimerMode.COUNTDOWN.code)) }
    // 目标分钟用文本态承接输入，允许中途为空；解析失败按 0 处理，由下方 isValid 兜底
    var targetText by remember {
        mutableStateOf(initial?.targetMinutes?.takeIf { it > 0 }?.toString().orEmpty())
    }
    // 每日计划开关：开启后该计划从创建日起每天出现在日历日面板
    var isDaily by remember { mutableStateOf(initial?.isDaily ?: false) }

    val targetMinutes = targetText.toIntOrNull() ?: 0
    // 保存可用性：名称非空；且若为倒计时，目标必须是正整数
    val canSave = name.isNotBlank() && (mode != TimerMode.COUNTDOWN || targetMinutes > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建计划" else "编辑计划") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("计划名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text("计时模式", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                when (mode) {
                    TimerMode.COUNTDOWN -> {
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = { input -> targetText = input.filter { it.isDigit() }.take(4) },
                            label = { Text("目标分钟") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TimerMode.POMODORO -> {
                        Text(
                            text = "番茄钟使用「设置」页的专注时长，无需在此填写。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TimerMode.COUNTUP -> {
                        Text(
                            text = "正计时无目标：开始后从 0 计时，手动停止即落库。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "每日计划",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = isDaily, onCheckedChange = { isDaily = it })
                }
                Text(
                    text = "开启后：从创建日起每天出现在日历的当日面板，日期上带棕色小点。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(name, mode, targetMinutes, isDaily) },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
