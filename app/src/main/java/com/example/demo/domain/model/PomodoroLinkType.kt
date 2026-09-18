package com.example.demo.domain.model

/**
 * 番茄记录关联的任务类型，对应 pomodoro_session.link_type 列。
 *
 * 这是一个「多态外键」：pomodoro_session 表用 (link_type, link_id) 两个字段
 * 指向 todo / work_log / plan 中的一条记录。SQLite/Room 无法对多态外键建立真正的外键约束，
 * 因此引用完整性必须由应用层（Repository）保证——删除 todo 时要把关联的番茄记录置空。
 *
 * ## 为什么数据库里存 Int 而代码里用 enum
 * 存 Int：省一个 TypeConverter，且统计 SQL 里可以直接写 link_type = 1。
 * 用 enum：调用方拿到的是类型安全的值，`when` 表达式有编译期穷尽检查，
 * 不会写出 link_type = 3 这种不存在的类型。[code] 是两者之间的翻译层。
 *
 * ## 为什么放在 domain 包
 * 它不含任何 Android 依赖，是纯业务字典；Repository、ViewModel、JVM 单元测试都要引用。
 * 放在 data 包会让 domain 反向依赖 data，分层就乱了。
 */
enum class PomodoroLinkType(val code: Int, val label: String) {
    NONE(0, "无关联"),
    TODO(1, "待办"),
    WORK_LOG(2, "工作日志"),
    PLAN(3, "计划");

    companion object {
        /**
         * 从数据库读回的 Int 还原成枚举。
         * 找不到时兜底成 [NONE] 而不是抛异常：脏数据不该让整页列表崩掉，
         * 把一条番茄记录的关联降级为「无关联」是可接受的损失。
         */
        fun fromCode(code: Int): PomodoroLinkType = entries.firstOrNull { it.code == code } ?: NONE
    }
}
