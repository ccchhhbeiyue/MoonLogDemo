package com.example.demo.domain.model

/**
 * 待办优先级。
 *
 * 数据库里存 [code]（Int），UI 与业务逻辑里用枚举——这样既有类型安全，
 * 又不必让 Room 去处理枚举映射。转换走 [fromCode]。
 */
enum class TodoPriority(val code: Int, val label: String) {
    NONE(0, "无"),
    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高");

    companion object {
        /** 找不到时回落到 NONE，避免脏数据导致崩溃（后端做枚举反序列化同理） */
        fun fromCode(code: Int): TodoPriority = entries.firstOrNull { it.code == code } ?: NONE
    }
}
