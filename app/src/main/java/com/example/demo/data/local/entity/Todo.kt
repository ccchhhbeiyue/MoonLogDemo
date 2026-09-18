package com.example.demo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 待办——「要做的事」，有明确的完成态。
 *
 * 与 [WorkLog] 的分工：Todo 面向未来（有 due_date、有 is_done），
 * WorkLog 面向过去（记录某天实际做了什么、花了多久）。两者语义不同，故独立建表。
 */
@Entity(
    tableName = "todo",
    indices = [Index(value = ["due_date"]), Index(value = ["is_done"])]
)
data class Todo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val description: String? = null,

    /** 截止日期；null 表示无期限待办 */
    @ColumnInfo(name = "due_date")
    val dueDate: LocalDate? = null,

    /** 优先级 code，见 [com.example.demo.domain.model.TodoPriority] */
    val priority: Int = 0,

    @ColumnInfo(name = "is_done")
    val isDone: Boolean = false,

    /** 完成时刻；未完成时为 null */
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    /** 分类：生活 / 工作 / 学习… */
    val category: String = "",

    /** 手动排序权重，值小的排前面 */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
