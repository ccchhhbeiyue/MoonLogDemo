package com.example.demo.ui.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.demo.DemoApplication
import com.example.demo.data.local.entity.Todo
import com.example.demo.di.DemoViewModelFactory
import com.example.demo.domain.model.TodoPriority
import com.example.demo.ui.theme.IconAdd

/**
 * 待办页（P5）。
 *
 * ## 结构：筛选 Chip 行 + 列表 + FAB
 * - 顶部四个 [TodoFilter] Chip 切换筛选，实际过滤在 [TodoViewModel.todos] 的 combine 里做；
 * - 列表项左侧一条优先级色条（红/橙/蓝/灰），中间勾选框切换完成态，点整项进编辑；
 * - FAB 新建，编辑态对话框里带删除与「开始专注」跳转。
 *
 * ## 为什么筛选态放 ViewModel 而不是 remember 在 Composable 里
 * 切 Tab 再切回来，页面会重组；若筛选态只活在 Composable 的 remember 里，回来就丢了。
 * 放 ViewModel（navigation-scoped）能随回退栈的 saveState/restoreState 一起保留。
 * 类比后端：会话级状态存 Session，而不是存某次请求的局部变量。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    onNavigateToFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val factory = remember(context) {
        DemoViewModelFactory((context.applicationContext as DemoApplication).container)
    }
    val viewModel: TodoViewModel = viewModel(factory = factory)

    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    // 两个对话框状态互斥：creating=新建，editing=编辑某条（携带原记录回填）
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Todo?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("待办") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(IconAdd, contentDescription = "新建待办")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 筛选 Chip 行：横向可滚，避免小屏四个 Chip 挤不下
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TodoFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(f.label) }
                    )
                }
            }
            HorizontalDivider()

            if (todos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "这个筛选下暂无待办，点右下角 + 新建一条。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // key = id：增删时让 Compose 复用对应项而非整体重排，动画与状态才稳
                    items(todos, key = { it.id }) { todo ->
                        TodoItem(
                            todo = todo,
                            onToggleDone = { viewModel.toggleDone(todo) },
                            onClick = { editing = todo },
                            // animateItem 是 LazyItemScope 的成员函数，只能在此 lambda 内调用
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        TodoEditDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { title, description, dueDate, priority, category ->
                viewModel.upsert(title, description, dueDate, priority, category)
                creating = false
            },
        )
    }

    editing?.let { todo ->
        TodoEditDialog(
            initial = todo,
            onDismiss = { editing = null },
            onSave = { title, description, dueDate, priority, category ->
                viewModel.upsert(title, description, dueDate, priority, category, existingId = todo.id)
                editing = null
            },
            onDelete = {
                viewModel.delete(todo)
                editing = null
            },
            onStartFocus = {
                editing = null
                onNavigateToFocus()
            },
        )
    }
}

/**
 * 单条待办的列表项。
 *
 * ## 优先级色条为什么用 fillMaxHeight 却要父 Row 设 IntrinsicSize.Min
 * 色条要贯穿整项高度，但 Row 的高度由最高的子项（文字列）决定——子项反过来问父项要多高会形成
 * 「循环测量」。给 Row 设 `height(IntrinsicSize.Min)` 后，Compose 先用一遍固有高度测量算出行高，
 * 再让色条 `fillMaxHeight` 填满这个确定值，打破循环。这是 Compose 里「一列色条撑满卡片」的标准解法。
 */
@Composable
private fun TodoItem(
    todo: Todo,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 勾选态切换时标题色平滑过渡，避免「啪」一下变灰的生硬感
    val titleColor by animateColorAsState(
        targetValue = if (todo.isDone) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "todoTitleColor"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(priorityColor(todo.priority))
            )
            Checkbox(
                checked = todo.isDone,
                onCheckedChange = { onToggleDone() },
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    // 已完成：加删除线 + 降为次要色，视觉上"退到后面"
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                    color = titleColor
                )
                todo.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                // 元信息行：截止日 + 分类
                val meta = buildString {
                    todo.dueDate?.let { append("${it.monthValue}月${it.dayOfMonth}日到期") }
                    if (todo.category.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(todo.category)
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 优先级 → 色条颜色。高=红、中=橙、低=蓝、无=灰，符合通用直觉。 */
private fun priorityColor(priorityCode: Int): Color = when (TodoPriority.fromCode(priorityCode)) {
    TodoPriority.HIGH -> Color(0xFFE53935)
    TodoPriority.MEDIUM -> Color(0xFFFB8C00)
    TodoPriority.LOW -> Color(0xFF1E88E5)
    TodoPriority.NONE -> Color(0xFFBDBDBD)
}
