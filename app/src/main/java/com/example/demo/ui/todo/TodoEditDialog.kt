package com.example.demo.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.demo.data.local.entity.Todo
import com.example.demo.domain.model.TodoPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 分类预设。demo 用固定字典，正式项目可改成用户自定义 + 落库。 */
private val CATEGORIES = listOf("生活", "工作", "学习", "其他")

/**
 * 新建 / 编辑待办的表单对话框。
 *
 * @param initial 非空表示编辑态（回填原值并显示删除/开始专注）；为空表示新建。
 * @param onSave 保存回调；标题为空时按钮禁用，故此处不再校验。
 * @param onDelete 编辑态专属的删除回调。
 * @param onStartFocus 编辑态专属的「开始专注」回调（跳转专注页）。
 * @param initialDueDate 新建态的默认截止日（日历页「看着日期新增」时传选中日覆盖）；不传则默认当天；编辑态忽略、以原记录为准。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditDialog(
    initial: Todo?,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String?, dueDate: LocalDate?, priority: TodoPriority, category: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onStartFocus: (() -> Unit)? = null,
    initialDueDate: LocalDate? = null,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var dueDate by remember {
        // 编辑态以原记录为准（无期限保持无期限，不能回填成今天）；
        // 新建态默认当天，日历页传选中日时以选中日覆盖。
        mutableStateOf(if (initial != null) initial.dueDate else initialDueDate ?: LocalDate.now())
    }
    var priority by remember { mutableStateOf(TodoPriority.fromCode(initial?.priority ?: 0)) }
    var category by remember {
        mutableStateOf(initial?.category?.takeIf { it.isNotEmpty() } ?: CATEGORIES.first())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DueDatePickerDialog(
            initial = dueDate,
            onConfirm = { picked ->
                dueDate = picked
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建待办" else "编辑待办") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text("截止日", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(dueDate?.let { "${it.monthValue}月${it.dayOfMonth}日" } ?: "无期限")
                    }
                    if (dueDate != null) {
                        TextButton(onClick = { dueDate = null }) { Text("清除") }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("优先级", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TodoPriority.entries.forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.label) })
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("分类", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }

                if (initial != null && onStartFocus != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onStartFocus) { Text("开始专注（跳转专注页）") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title, description, dueDate, priority, category) },
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

/**
 * 截止日选择器（Material3 DatePicker）。
 *
 * ## 为什么用 UTC 做 millis ↔ LocalDate 转换
 * DatePicker 的 selectedDateMillis 是「该日 UTC 零点」的毫秒值。若用系统默认时区解析，
 * 在东半球会把当天零点往前偏成前一天（如 UTC+8 的 00:00 = UTC 前一天 16:00），日期就错了一天。
 * 统一按 [ZoneOffset.UTC] 往返转换，保证「选哪天就是哪天」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePickerDialog(
    initial: LocalDate?,
    onConfirm: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.toEpochMilliUtc())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.selectedDateMillis?.toLocalDateUtc()) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state)
    }
}

private fun LocalDate.toEpochMilliUtc(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
