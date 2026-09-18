package com.example.demo.domain.model

/**
 * 计时模式，对应 pomodoro_session.mode 列与 plan.mode 列。
 *
 * 三种模式的计时语义完全不同，但共用同一套前台计时服务与落库表：
 * - [POMODORO] 番茄钟：沿用设置页的专注时长做单工作段倒计时（本期不做自动休息轮次）；
 * - [COUNTDOWN] 倒计时：按计划自带的目标分钟倒数到 0，到 0 即完成；
 * - [COUNTUP] 计时：从 0 正计时、无目标，用户手动停止，停止即记一条完成会话。
 *
 * ## 为什么 POMODORO 的 code 是 0
 * pomodoro_session 的历史行（P7 产生）都没有模式概念，语义上全是番茄钟。
 * 让 POMODORO=0 并给列设 DEFAULT 0，迁移时历史行无需回填就自动归入番茄钟，
 * 「番茄完成率」这个统计口径才不会把老数据算丢或算错。
 *
 * ## 为什么放 domain 包
 * 与 [PomodoroLinkType] 同理：纯业务字典、零 Android 依赖，
 * Repository / Service / ViewModel / JVM 单测都要引用，放 data 会造成反向依赖。
 */
enum class TimerMode(val code: Int, val label: String) {
    POMODORO(0, "番茄钟"),
    COUNTDOWN(1, "倒计时"),
    COUNTUP(2, "计时");

    companion object {
        /** 读库还原时兜底成 [POMODORO]：脏数据降级为最保守的语义，不让列表崩 */
        fun fromCode(code: Int): TimerMode = entries.firstOrNull { it.code == code } ?: POMODORO
    }
}
