package com.example.demo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 经期记录——整个应用的核心事实表。
 *
 * 设计要点：
 * 1. [startDate] 加 UNIQUE 索引，从数据库层面保证「一天不可能有两次月经开始」，
 *    这是预测算法正确性的前提（否则相邻周期长度会算出 0 或负数）。
 * 2. [endDate] 可空：经期进行中时用户只记了开始日，结束后再回填。
 * 3. 这张表只存「已发生的事实」，**不存预测结果**。预测一律由 CyclePredictor 实时算出，
 *    避免用户补录/修改记录后产生冗余字段的脏数据。
 */
@Entity(
    tableName = "period_record",
    indices = [Index(value = ["start_date"], unique = true)]
)
data class PeriodRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 月经开始日 */
    @ColumnInfo(name = "start_date")
    val startDate: LocalDate,

    /** 月经结束日；null 表示尚未结束或未回填 */
    @ColumnInfo(name = "end_date")
    val endDate: LocalDate? = null,

    /** 经量：1 轻 / 2 中 / 3 重；null 表示未记录 */
    val flow: Int? = null,

    /** 症状标签，逗号分隔的 code，见 [com.example.demo.domain.model.Symptom] */
    val symptoms: String = "",

    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
