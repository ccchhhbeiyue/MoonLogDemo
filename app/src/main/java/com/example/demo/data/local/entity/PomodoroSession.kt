package com.example.demo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 番茄钟会话记录。
 *
 * 注意 [actualMinutes] 与 [plannedMinutes] 分开存：中途放弃的番茄 actual < planned，
 * 这样"完成率"这个统计指标才算得出来（完成数 / 总启动数）。
 *
 * [workDate] 是从 [startTime] 冗余出来的日期字段。这属于有意的反规范化：
 * 统计页要频繁按天聚合，若每次都从毫秒时间戳转换，SQL 里就得写
 * datetime(start_time/1000,'unixepoch','localtime') 这种既慢又难读的表达式。
 * 代价是写入时要保证两者一致——由 Repository 统一构造，不暴露给 UI 层。
 */
@Entity(
    tableName = "pomodoro_session",
    indices = [
        Index(value = ["work_date"]),
        Index(value = ["link_type", "link_id"])
    ]
)
data class PomodoroSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 开始时刻，epochMilli */
    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "planned_minutes")
    val plannedMinutes: Int,

    @ColumnInfo(name = "actual_minutes")
    val actualMinutes: Int,

    /** 是否完整走完（未中途放弃） */
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,

    /** 关联类型 code，见 [com.example.demo.domain.model.PomodoroLinkType] */
    @ColumnInfo(name = "link_type")
    val linkType: Int = 0,

    /** 关联的 todo.id / work_log.id / plan.id；linkType 为 NONE 时必为 null */
    @ColumnInfo(name = "link_id")
    val linkId: Long? = null,

    /**
     * 计时模式 code，见 [com.example.demo.domain.model.TimerMode]。
     * 默认 0=番茄钟：P7 的历史行没有模式概念、语义上全是番茄钟，
     * 配合迁移里的 DEFAULT 0 免回填即归入正确口径。
     */
    @ColumnInfo(name = "mode")
    val mode: Int = 0,

    /** 冗余的归属日期，便于按天聚合统计 */
    @ColumnInfo(name = "work_date")
    val workDate: LocalDate
)
