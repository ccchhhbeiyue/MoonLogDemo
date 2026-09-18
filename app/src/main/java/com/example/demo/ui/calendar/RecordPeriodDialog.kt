package com.example.demo.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.demo.data.local.entity.PeriodRecord
import com.example.demo.domain.model.Symptom
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 流量选项：1 轻 / 2 中 / 3 重，与 period_record.flow 的取值对应 */
private val FLOW_OPTIONS = listOf(1 to "轻", 2 to "中", 3 to "重")

private const val DEFAULT_DURATION_DAYS = 5
private const val MAX_DURATION_DAYS = 10

/**
 * 记录 / 编辑一条经期的表单对话框。日历页与历史列表页共用。
 *
 * ## 开始日为什么锁定
 * [startDate] 由调用方传入且不可在对话框内修改：start_date 上有 UNIQUE 索引，
 * 编辑时改开始日可能撞另一条记录的约束，Repository 会抛 IllegalArgumentException。
 * P3 不做「改开始日」的冲突处理（那需要提示用户合并/覆盖），故锁定开始日，
 * 对话框只负责结束日 / 流量 / 症状 / 备注这几个无唯一性约束的字段。
 *
 * ## 结束日为什么用「持续天数」而不是日期选择器
 * 经期长度有明确的医学常识区间（3~7 天），用天数 stepper 比 Material DatePicker
 * 更贴合语义、也更省交互；「尚未结束」开关对应 endDate=null（经期进行中）。
 *
 * @param initialRecord 非空表示编辑已有记录并预填表单；为空表示新建。
 * @param onSave 保存回调，endDate 已按 ongoing/durationDays 算好。
 * @param onDelete 非空时显示「删除」按钮（编辑态）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordPeriodDialog(
    startDate: LocalDate,
    initialRecord: PeriodRecord? = null,
    onDismiss: () -> Unit,
    onSave: (endDate: LocalDate?, flow: Int?, symptoms: Set<Symptom>, note: String?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var ongoing by remember { mutableStateOf(initialRecord?.endDate == null) }
    var durationDays by remember {
        mutableIntStateOf(
            initialRecord?.endDate
                ?.let { ChronoUnit.DAYS.between(startDate, it).toInt() + 1 }
                ?: DEFAULT_DURATION_DAYS
        )
    }
    var flow by remember { mutableStateOf(initialRecord?.flow) }
    var symptoms by remember {
        mutableStateOf(initialRecord?.let { Symptom.parseCodes(it.symptoms) } ?: emptySet())
    }
    var note by remember { mutableStateOf(initialRecord?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRecord == null) "记录经期" else "编辑经期") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "开始日：${startDate.monthValue}月${startDate.dayOfMonth}日",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("尚未结束", modifier = Modifier.weight(1f))
                    Switch(checked = ongoing, onCheckedChange = { ongoing = it })
                }
                if (!ongoing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("持续天数", modifier = Modifier.weight(1f))
                        TextButton(onClick = { if (durationDays > 1) durationDays-- }) { Text("−") }
                        Text("${durationDays} 天")
                        TextButton(onClick = { if (durationDays < MAX_DURATION_DAYS) durationDays++ }) { Text("+") }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("流量", style = MaterialTheme.typography.labelLarge)
                Row {
                    FLOW_OPTIONS.forEach { (value, label) ->
                        FilterChip(
                            selected = flow == value,
                            onClick = { flow = if (flow == value) null else value },
                            label = { Text(label) },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("症状（可多选）", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Symptom.entries.forEach { symptom ->
                        FilterChip(
                            selected = symptom in symptoms,
                            onClick = {
                                symptoms = if (symptom in symptoms) symptoms - symptom else symptoms + symptom
                            },
                            label = { Text(symptom.label) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onDelete) {
                        Text("删除这条记录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val endDate = if (ongoing) null else startDate.plusDays(durationDays.toLong() - 1)
                onSave(endDate, flow, symptoms, note.ifBlank { null })
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

