package com.example.demo.data.local.converter

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * LocalDate 与 epochDay(Long) 的互转。
 *
 * 为什么存 epochDay 而不是字符串：
 * 1. epochDay 是「自 1970-01-01 起的天数」，不含时间、不含时区，正好对应"日历上的某一天"这个语义；
 * 2. 8 字节整数比 "2026-09-16" 这种 10 字符文本更省空间，且范围比较（BETWEEN）直接走整数比较，比字符串快；
 * 3. 不受设备时区、系统语言格式影响，不会出现 "09/16/2026" 与 "16/09/2026" 的歧义。
 *
 * 作用等同 JPA 的 AttributeConverter。
 *
 * 注意：纯时间戳字段（created_at / start_time 等）不走转换器，直接用 Long 存 epochMilli，
 * 因为那些值需要做时长加减运算，用 Long 最直接。
 */
class DateConverters {

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun toLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
}
