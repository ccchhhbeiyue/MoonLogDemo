package com.example.demo.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.demo.ui.theme.IconCalendar
import com.example.demo.ui.theme.IconCheckCircle
import com.example.demo.ui.theme.IconPlay
import com.example.demo.ui.theme.IconWorkLog

/**
 * 底部导航的四个顶层目的地。
 *
 * 用枚举而不是散落的字符串常量，好处是：路由名、显示文案、图标三者绑定在一处，
 * 新增 Tab 时不可能漏掉其中一项。
 *
 * ⚠️ 图标来自项目自绘的 [com.example.demo.ui.theme.AppIcons]，不是 `Icons.Filled.Xxx`。
 * 原因见 AppIcons.kt 顶部说明——material-icons 系列制品已停止维护，且 Material3 1.4.0
 * 起不再传递依赖它们，引入会造成 Compose 版本混杂。
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    CALENDAR("calendar", "日历", IconCalendar),
    TODO("todo", "待办", IconCheckCircle),
    PLAN("plan", "计划", IconWorkLog),
    FOCUS("focus", "专注", IconPlay);

    companion object {
        /** 供外壳判断"当前路由是否该显示底部栏"，用 Set 而不是 List 是为了 O(1) 查找 */
        val routes: Set<String> = entries.map { it.route }.toSet()
    }
}

/**
 * 非顶层目的地：从日历页右上角进入，会覆盖底部导航栏。
 * 用 object + const 而不是枚举，因为它们没有统一的图标与文案，凑不成枚举的三项约束。
 */
object SecondaryDestination {
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val PERIOD_HISTORY = "period_history"
}
