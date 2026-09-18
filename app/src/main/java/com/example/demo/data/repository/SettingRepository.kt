package com.example.demo.data.repository

import com.example.demo.data.local.dao.UserSettingDao
import com.example.demo.data.local.entity.UserSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 用户配置的 Repository。单行表（id 恒为 1）的全部特殊语义都收在这里。
 *
 * ## 1. null 兜底放在本层，不放 UI
 * 首次启动时表是空的，[UserSettingDao.observe] 会发射 null。
 * 如果让每个 UI 调用方各自写 `?: UserSetting()`，漏一处就是一个 NPE 或默认值不一致。
 * 所以 [observe] 用 [map] 操作符在流上统一兜底——注意 map 是**懒的中间操作符**，
 * 它只描述「将来每个元素怎么变换」，不会触发收集，冷流性质保持不变。
 *
 * ## 2. 只给 PATCH 语义，不给 PUT 语义
 * 不暴露 `upsert(entity)`，而是给 [setCycleLength] 这类语义化小方法，
 * 内部「读最新值 → copy 改一个字段 → 整行写回」。
 * 原因：UI 手里那份 entity 可能是旧的，整行写回会把别的字段覆盖成旧值（丢失更新）。
 * 类比后端：配置接口只允许改指定字段，禁止整对象覆盖提交。
 *
 * ## 已知的取舍
 * 「读-改-写」三步不是原子的，理论上两个并发调用会互相覆盖。
 * 本应用的调用方只有单线程的 UI（设置页滑块/开关），并发不存在，故不加锁。
 * 若将来引入后台任务改配置，需要把 [mutate] 升级为 @Transaction + 数据库内更新。
 */
class SettingRepository(private val dao: UserSettingDao) {

    /** 订阅配置；首启空表时兜底成默认值，见类注释 */
    fun observe(): Flow<UserSetting> = dao.observe().map { it ?: UserSetting() }

    /** 一次性读取；算法冷启动分支用 */
    suspend fun get(): UserSetting = dao.get() ?: UserSetting()

    suspend fun setCycleLength(days: Int) = mutate {
        require(days in MIN_CYCLE..MAX_CYCLE) { "周期长度 $days 超出合理范围 $MIN_CYCLE..$MAX_CYCLE" }
        it.copy(defaultCycleLength = days)
    }

    suspend fun setPeriodLength(days: Int) = mutate {
        require(days in MIN_PERIOD..MAX_PERIOD) { "经期长度 $days 超出合理范围 $MIN_PERIOD..$MAX_PERIOD" }
        it.copy(defaultPeriodLength = days)
    }

    /** 番茄四个参数一次设完：设置页是一个表单一起提交，拆开四次写回反而浪费 */
    suspend fun setPomodoroDurations(workMin: Int, shortBreakMin: Int, longBreakMin: Int, roundsBeforeLongBreak: Int) =
        mutate {
            require(workMin in MIN_WORK..MAX_WORK) { "专注时长 $workMin 超出 $MIN_WORK..$MAX_WORK 分钟" }
            require(shortBreakMin in MIN_SHORT_BREAK..MAX_SHORT_BREAK) { "短休息 $shortBreakMin 超出 $MIN_SHORT_BREAK..$MAX_SHORT_BREAK 分钟" }
            require(longBreakMin in MIN_LONG_BREAK..MAX_LONG_BREAK) { "长休息 $longBreakMin 超出 $MIN_LONG_BREAK..$MAX_LONG_BREAK 分钟" }
            require(roundsBeforeLongBreak in MIN_ROUNDS..MAX_ROUNDS) { "长休间隔轮数 $roundsBeforeLongBreak 超出 $MIN_ROUNDS..$MAX_ROUNDS" }
            it.copy(
                pomodoroWorkMin = workMin,
                pomodoroShortBreakMin = shortBreakMin,
                pomodoroLongBreakMin = longBreakMin,
                pomodoroRoundsBeforeLongBreak = roundsBeforeLongBreak,
            )
        }

    suspend fun setNotificationEnabled(enabled: Boolean) = mutate { it.copy(notificationEnabled = enabled) }

    /** 易孕窗口/排卵日标记的显示开关；默认隐藏，开启才在月格上画 */
    suspend fun setShowFertileWindow(enabled: Boolean) = mutate { it.copy(showFertileWindow = enabled) }

    /** 读-改-写的收口点，见类注释的取舍说明 */
    private suspend fun mutate(transform: (UserSetting) -> UserSetting) {
        dao.upsert(transform(get()))
    }

    companion object {
        /** 医学上正常周期 21~35 天，这里放宽到 15..60 以容纳偶发紊乱与录入误差 */
        const val MIN_CYCLE = 15
        const val MAX_CYCLE = 60
        const val MIN_PERIOD = 1
        const val MAX_PERIOD = 10

        /** 番茄参数范围，设置页步进器的禁用边界与 [setPomodoroDurations] 的 require 共用 */
        const val MIN_WORK = 1
        const val MAX_WORK = 120
        const val MIN_SHORT_BREAK = 1
        const val MAX_SHORT_BREAK = 60
        const val MIN_LONG_BREAK = 1
        const val MAX_LONG_BREAK = 120
        const val MIN_ROUNDS = 2
        const val MAX_ROUNDS = 8
    }
}
