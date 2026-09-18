package com.example.demo.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.demo.ui.calendar.CalendarScreen
import com.example.demo.ui.calendar.PeriodHistoryScreen
import com.example.demo.ui.focus.FocusScreen
import com.example.demo.ui.plan.PlanScreen
import com.example.demo.ui.settings.SettingsScreen
import com.example.demo.ui.stats.StatsScreen
import com.example.demo.ui.todo.TodoScreen

/**
 * 应用外壳：底部导航栏 + NavHost。
 *
 * 对照后端理解——NavHost 就是 @RequestMapping 的路由表，
 * 每个 composable("route") { ... } 是一个 handler，把 URL 映射到一个"视图"。
 * 区别是这里的"URL"不会出现在地址栏，只存在于回退栈（back stack）里。
 *
 * 底部栏为什么放在外层 Scaffold 而不是每个页面里各写一份：
 * 二级页面（统计、设置）需要**盖住**底部栏，只有把底部栏提到路由之外，
 * 才能用"当前路由是否属于四个 Tab"这一个条件统一控制它的显隐。
 */
@Composable
fun DemoApp(navController: NavHostController = rememberNavController()) {
    // 订阅当前回退栈栈顶。用 asState 把 Navigation 的回调式 API 转成 Compose 的状态，
    // 路由一变这里就会重组，底部栏的选中态自动跟上。
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val isTopLevel = currentRoute in TopLevelDestination.routes

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                Icon(destination.icon, contentDescription = destination.label)
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        DemoNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/**
 * Tab 切换的标准写法。三个参数缺一不可，否则会出现"返回键把四个 Tab 各退一遍"的糟糕体验：
 *
 * - popUpTo(起始目的地) + saveState：切 Tab 时把栈清到只剩首页，并把被清掉的页面状态存起来；
 * - launchSingleTop：重复点同一个 Tab 不再新建实例（等价于后端的幂等）；
 * - restoreState：回到之前访问过的 Tab 时恢复其滚动位置等状态。
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/** 页面切换淡入淡出时长（ms）：短到不拖沓、长到能感知，纯观感打磨。 */
private const val NAV_ANIM_MS = 220

/** 路由表。新增页面时在这里加一行 composable，并在需要时补进 TopLevelDestination。 */
@Composable
private fun DemoNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.CALENDAR.route,
        modifier = modifier,
        // 全局过渡：切 Tab / 进出二级页面统一淡入淡出，避免硬切的生硬感。
        enterTransition = { fadeIn(tween(NAV_ANIM_MS)) },
        exitTransition = { fadeOut(tween(NAV_ANIM_MS)) },
        popEnterTransition = { fadeIn(tween(NAV_ANIM_MS)) },
        popExitTransition = { fadeOut(tween(NAV_ANIM_MS)) },
    ) {
        composable(TopLevelDestination.CALENDAR.route) {
            CalendarScreen(
                onNavigateToStats = { navController.navigate(SecondaryDestination.STATS) },
                onNavigateToSettings = { navController.navigate(SecondaryDestination.SETTINGS) },
                onNavigateToHistory = { navController.navigate(SecondaryDestination.PERIOD_HISTORY) }
            )
        }
        composable(TopLevelDestination.TODO.route) {
            TodoScreen(
                // 从待办的「开始专注」跳到专注 Tab。走 navigateToTopLevel 而非 navigate，
                // 复用底部栏切 Tab 的 popUpTo+saveState 语义，回退栈才干净。
                onNavigateToFocus = { navController.navigateToTopLevel(TopLevelDestination.FOCUS) }
            )
        }
        composable(TopLevelDestination.PLAN.route) {
            PlanScreen(
                // 从计划「开始」跳到专注 Tab，复用底部栏切 Tab 的 popUpTo+saveState 语义
                onNavigateToFocus = { navController.navigateToTopLevel(TopLevelDestination.FOCUS) }
            )
        }
        composable(TopLevelDestination.FOCUS.route) { FocusScreen() }

        // 二级页面：不在底部栏中，各自带返回箭头
        // 这里用 lambda 而不是 navController::popBackStack —— 后者返回 Boolean 且存在重载，
        // 方法引用会产生歧义；lambda 的返回值会被自动强转成 Unit。
        composable(SecondaryDestination.PERIOD_HISTORY) {
            PeriodHistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(SecondaryDestination.STATS) {
            StatsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(SecondaryDestination.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
