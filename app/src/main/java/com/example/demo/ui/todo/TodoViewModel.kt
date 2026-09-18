package com.example.demo.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.local.entity.Todo
import com.example.demo.data.repository.TodoRepository
import com.example.demo.domain.model.TodoPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 待办页的筛选维度。
 *
 * 四个 Tab 的语义（互不重叠的「完成态」+ 嵌套的「时间窗」）：
 * - [TODAY] / [WEEK] / [ALL] 只看未完成；[DONE] 只看已完成。
 * - 时间窗上 TODAY ⊂ WEEK ⊂ ALL，是嵌套关系而非互斥分区，符合待办类 App 的直觉。
 */
enum class TodoFilter(val label: String) {
    TODAY("今日"),
    WEEK("未来7天"),
    ALL("全部"),
    DONE("已完成"),
}

/**
 * 待办页 ViewModel。
 *
 * ## 为什么过滤放在 ViewModel 而不是 DAO 写四条 SQL
 * [TodoRepository.observeAll] 已经是一条带排序的全量 Flow。四个筛选只是对同一份数据
 * 做不同的谓词过滤，用 [combine] 把「数据流」和「筛选状态流」在内存里 join 即可，
 * 不必为每个 Tab 各写一条 DAO 查询（那会让筛选逻辑散落到 SQL 里、难以单测）。
 * 类比后端：一条全量缓存 + 应用层 predicate，优于四份互相重复的 SQL。
 *
 * ## 完成时间不收口在 UI
 * 勾选/取消勾选只调 [toggleDone]，completedAt 由 Repository 决定（见其类注释），
 * UI 无法传错完成时间。
 */
class TodoViewModel(
    private val todoRepository: TodoRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(TodoFilter.ALL)
    val filter: StateFlow<TodoFilter> = _filter.asStateFlow()

    /** 当前筛选下的待办列表 */
    val todos: StateFlow<List<Todo>> = combine(
        todoRepository.observeAll(),
        _filter,
    ) { all, filter ->
        val today = LocalDate.now()
        when (filter) {
            TodoFilter.TODAY -> all.filter { !it.isDone && it.dueDate == today }
            TodoFilter.WEEK -> all.filter {
                !it.isDone && it.dueDate != null &&
                    !it.dueDate.isBefore(today) && !it.dueDate.isAfter(today.plusDays(WEEK_WINDOW_DAYS))
            }
            TodoFilter.ALL -> all.filter { !it.isDone }
            TodoFilter.DONE -> all.filter { it.isDone }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = emptyList(),
    )

    fun setFilter(filter: TodoFilter) {
        _filter.value = filter
    }

    /**
     * 新建或更新待办。
     * @param existingId 非空表示编辑；为空表示新建。
     *   编辑时先读原记录再 copy，保留 createdAt 与 isDone/completedAt（避免编辑把已完成状态重置）。
     */
    fun upsert(
        title: String,
        description: String?,
        dueDate: LocalDate?,
        priority: TodoPriority,
        category: String,
        existingId: Long? = null,
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (existingId != null) {
                val original = todoRepository.getById(existingId) ?: return@launch
                todoRepository.update(
                    original.copy(
                        title = trimmed,
                        description = description?.trim()?.takeIf { it.isNotEmpty() },
                        dueDate = dueDate,
                        priority = priority.code,
                        category = category,
                    )
                )
            } else {
                todoRepository.insert(
                    Todo(
                        title = trimmed,
                        description = description?.trim()?.takeIf { it.isNotEmpty() },
                        dueDate = dueDate,
                        priority = priority.code,
                        category = category,
                    )
                )
            }
        }
    }

    /** 切换完成态；完成时间由 Repository 收口 */
    fun toggleDone(todo: Todo) {
        viewModelScope.launch { todoRepository.setDone(todo.id, !todo.isDone) }
    }

    fun delete(todo: Todo) {
        viewModelScope.launch { todoRepository.delete(todo) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        /** 「未来7天」窗口长度（含今天共 7 天：today .. today+6） */
        const val WEEK_WINDOW_DAYS = 6L
    }
}
