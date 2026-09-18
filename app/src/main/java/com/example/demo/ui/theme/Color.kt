package com.example.demo.ui.theme

import androidx.compose.ui.graphics.Color

// ===== Material3 亮色方案 =====
val Rose40 = Color(0xFFB03052)
val RoseContainerLight = Color(0xFFFFD9E0)
val OnRoseContainerLight = Color(0xFF3F0017)
val Violet40 = Color(0xFF7B5AA6)
val VioletContainerLight = Color(0xFFEBDDFF)
val Teal40 = Color(0xFF00695C)

// ===== Material3 暗色方案 =====
val Rose80 = Color(0xFFFFB2C4)
val RoseContainerDark = Color(0xFF8F1739)
val Violet80 = Color(0xFFD0BBFF)
val Teal80 = Color(0xFF4DB6AC)

// ===== 领域语义色（日历标记专用）=====
// 这几组颜色不随 Material 主题走，因为它们承载固定的业务含义：
// 用户看到深玫瑰红就必须知道是"真实经期"，看到浅玫瑰红就必须知道是"预测"。
// 暗色模式下单独提供一套，保证对比度。

/** 已确认的经期（实色，粉红色） */
val PeriodConfirmedLight = Color(0xFFF06292)
val PeriodConfirmedDark = Color(0xFFFF8AAB)

/**
 * 已确认经期色带之上的前景色（日期数字、选中环）。
 * 浅色带 F06292 偏深 → 用白字；深色带 FF8AAB 偏浅 → 必须换深李紫，否则白字压浅粉看不清。
 * 这正是「领域色暗色单独给一套保证对比度」的延伸：带子变色，带上的字也得跟着变。
 */
val OnPeriodConfirmedLight = Color.White
val OnPeriodConfirmedDark = Color(0xFF3E0018)

/** 预测的经期（浅色，与已确认形成明确区分） */
val PeriodPredictedLight = Color(0xFFF8BBD0)
val PeriodPredictedDark = Color(0xFF7A3B52)

/** 排卵日（单点标记） */
val OvulationLight = Color(0xFFFFF59D)
val OvulationDark = Color(0xFF827717)

/** 易孕窗口（极浅色背景带） */
val FertileLight = Color(0xFFEDE7F6)
val FertileDark = Color(0xFF3A3352)

/** 今天（描边色） */
val TodayRingLight = Color(0xFF37474F)
val TodayRingDark = Color(0xFFB0BEC5)

/** 日期小点：当天有待办（蓝）/ 当天有每日计划（棕）；中饱和色，亮暗底上都看得清 */
val TodoDotBlue = Color(0xFF1E88E5)
val PlanDotBrown = Color(0xFF8D6E63)
