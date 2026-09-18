package com.example.demo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 每日工作日志——「做过的事」，按天聚合，带耗时。
 *
 * 与 [Todo] 的区别：Todo 是任务清单（关注"要不要做"），
 * WorkLog 是工时/日报记录（关注"做了什么、花了多久"）。
 *
 * [durationMinutes] 可以由关联的番茄钟自动累加，也可以手动填写。
 */
@Entity(
    tableName = "work_log",
    indices = [Index(value = ["work_date"])]
)
data class WorkLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 归属日期。日历页点某天，就按这个字段查当天的工作日志 */
    @ColumnInfo(name = "work_date")
    val workDate: LocalDate,

    val title: String,

    /** 详细内容，可空 */
    val content: String = "",

    /** 工作类型：开发 / 会议 / 学习…，用于分类耗时统计 */
    val category: String = "",

    /** 耗时（分钟）。0 表示未记录 */
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int = 0,

    /** 0 计划中 / 1 已完成 */
    val status: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PLANNED = 0
        const val STATUS_DONE = 1
    }
}
