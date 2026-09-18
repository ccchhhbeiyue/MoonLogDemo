package com.example.demo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.local.entity.UserSetting
import com.example.demo.data.repository.SettingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel（P8）。
 *
 * ## 只透传 + 转发，不做本地副本
 * [setting] 直接是 [SettingRepository.observe] 的流：设置项的真相在数据库，
 * 每次写入后 Flow 自动重发，UI 立刻刷新——「改完即时生效」不需要任何手动同步代码。
 * 类比后端：配置接口写完立刻 GET 到最新值，因为读的是同一个存储源。
 *
 * ## 番茄四个参数为什么合成一个方法
 * Repository 只给 [SettingRepository.setPomodoroDurations]（一次读-改-写整组），
 * 这里用「当前值 + 单个覆盖」的方式转发，避免设置页自己拼整组参数时漏字段。
 */
class SettingsViewModel(private val settingRepository: SettingRepository) : ViewModel() {

    val setting: StateFlow<UserSetting> = settingRepository.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = UserSetting(),
        )

    fun setCycleLength(days: Int) = launch { settingRepository.setCycleLength(days) }

    fun setPeriodLength(days: Int) = launch { settingRepository.setPeriodLength(days) }

    fun setWorkMinutes(minutes: Int) = pomodoro { it.copy(pomodoroWorkMin = minutes) }

    fun setShortBreak(minutes: Int) = pomodoro { it.copy(pomodoroShortBreakMin = minutes) }

    fun setLongBreak(minutes: Int) = pomodoro { it.copy(pomodoroLongBreakMin = minutes) }

    fun setRoundsBeforeLongBreak(rounds: Int) = pomodoro { it.copy(pomodoroRoundsBeforeLongBreak = rounds) }

    fun setNotificationEnabled(enabled: Boolean) = launch {
        settingRepository.setNotificationEnabled(enabled)
    }

    fun setShowFertileWindow(enabled: Boolean) = launch {
        settingRepository.setShowFertileWindow(enabled)
    }

    /** 以流里的最新值为基座改一个番茄参数，再整组提交（见类注释） */
    private fun pomodoro(transform: (UserSetting) -> UserSetting) = launch {
        val current = setting.value
        val next = transform(current)
        settingRepository.setPomodoroDurations(
            workMin = next.pomodoroWorkMin,
            shortBreakMin = next.pomodoroShortBreakMin,
            longBreakMin = next.pomodoroLongBreakMin,
            roundsBeforeLongBreak = next.pomodoroRoundsBeforeLongBreak,
        )
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
