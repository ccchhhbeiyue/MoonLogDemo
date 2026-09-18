package com.example.demo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户配置——单行表（singleton row）。
 *
 * 用固定主键 [SINGLETON_ID] 保证全表只有一行，等价于后端把配置存进
 * 一张只有 id=1 的记录表，而不是散落在 SharedPreferences 里。
 * 好处是配置和数据在同一个事务边界内，且便于统一导出备份。
 *
 * 这里的 [defaultCycleLength] / [defaultPeriodLength] 只在「冷启动」
 * （用户还没有任何经期记录）时用于粗略预测，一旦积累了记录就以真实数据为准。
 */
@Entity(tableName = "user_setting")
data class UserSetting(
    @PrimaryKey
    val id: Int = SINGLETON_ID,

    /** 默认周期长度（天），用于无记录时的冷启动预测 */
    @ColumnInfo(name = "default_cycle_length")
    val defaultCycleLength: Int = 28,

    /** 默认经期长度（天），用于预测经期的结束日 */
    @ColumnInfo(name = "default_period_length")
    val defaultPeriodLength: Int = 5,

    /** 番茄钟：专注时长 */
    @ColumnInfo(name = "pomodoro_work_min")
    val pomodoroWorkMin: Int = 25,

    /** 番茄钟：短休息时长 */
    @ColumnInfo(name = "pomodoro_short_break_min")
    val pomodoroShortBreakMin: Int = 5,

    /** 番茄钟：长休息时长 */
    @ColumnInfo(name = "pomodoro_long_break_min")
    val pomodoroLongBreakMin: Int = 15,

    /** 番茄钟：几个番茄后进入长休息 */
    @ColumnInfo(name = "pomodoro_rounds_before_long_break")
    val pomodoroRoundsBeforeLongBreak: Int = 4,

    /** 是否开启通知（番茄结束提醒） */
    @ColumnInfo(name = "notification_enabled")
    val notificationEnabled: Boolean = true,

    /**
     * 是否在日历上显示易孕窗口与排卵日标记。
     * 默认 false：生育力信息隐私敏感，不主动展示，由用户在设置页显式开启。
     * 只控制月格渲染，不影响预测计算本身（算法照常跑，UI 选择性展示）。
     */
    @ColumnInfo(name = "show_fertile_window")
    val showFertileWindow: Boolean = false
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
