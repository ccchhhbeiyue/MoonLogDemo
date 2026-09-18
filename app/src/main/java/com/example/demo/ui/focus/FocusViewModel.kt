package com.example.demo.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.repository.PomodoroRepository
import com.example.demo.data.repository.SettingRepository
import com.example.demo.data.repository.TodoRepository
import com.example.demo.domain.model.PomodoroLinkType
import com.example.demo.service.PomodoroService
import com.example.demo.service.TimerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * 专注页 ViewModel（P7）。
 *
 * ## 它不持有计时逻辑
 * 计时的真相在 [PomodoroService]（前台服务）里，本 VM 只把服务的 [PomodoroService.uiState]
 * 原样透传给 UI。这样「UI 重组 / 切 Tab / 切后台」都不会影响计时——
 * 状态的真实来源（source of truth）始终是服务，VM 只是投影。
 * 类比后端：VM 像一个只读视图 / DTO 投影，事务与状态在 service 层。
 *
 * ## 关联候选
 * 把「今天未完成的待办」映射成一份可选清单（工作日志已随 P11 下线），
 * 用户开始专注前挑一个，番茄落库时就带上 (link_type, link_id)。
 */
class FocusViewModel(
    private val pomodoroRepository: PomodoroRepository,
    settingRepository: SettingRepository,
    todoRepository: TodoRepository,
) : ViewModel() {

    /** 计时状态直接透传服务单例，不在 VM 里复制一份。 */
    val timerState: StateFlow<TimerUiState> = PomodoroService.uiState

    /** 设置的专注时长（分钟），开始计时时作为 plannedMinutes 传给服务。 */
    val workMinutes: StateFlow<Int> = settingRepository.observe()
        .map { it.pomodoroWorkMin }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = DEFAULT_WORK_MINUTES,
        )

    /** 今天完成的番茄数 / 累计专注分钟（只算完整走完的），SQL 聚合下推。 */
    val todayCount: StateFlow<Int> =
        pomodoroRepository.observeCompletedCountByDate(LocalDate.now())
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = 0,
            )

    val todayMinutes: StateFlow<Int> =
        pomodoroRepository.observeCompletedMinutesByDate(LocalDate.now())
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = 0,
            )

    /** 可关联的任务候选：今日未完成待办。 */
    val linkCandidates: StateFlow<List<LinkCandidate>> =
        todoRepository.observeByDate(LocalDate.now())
            .map { todos ->
                todos.filter { !it.isDone }
                    .map { LinkCandidate(PomodoroLinkType.TODO, it.id, "待办 · ${it.title}") }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = emptyList(),
            )

    private val _selectedLink = MutableStateFlow<LinkCandidate?>(null)
    val selectedLink: StateFlow<LinkCandidate?> = _selectedLink.asStateFlow()

    fun selectLink(candidate: LinkCandidate?) {
        _selectedLink.value = candidate
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_WORK_MINUTES = 25
    }
}

/** 关联选择器里的一行：指向某个待办。 */
data class LinkCandidate(
    val type: PomodoroLinkType,
    val id: Long,
    val label: String,
)
