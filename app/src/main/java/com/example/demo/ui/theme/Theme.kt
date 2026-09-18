package com.example.demo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Rose40,
    onPrimary = Color.White,
    primaryContainer = RoseContainerLight,
    onPrimaryContainer = OnRoseContainerLight,
    secondary = Violet40,
    secondaryContainer = VioletContainerLight,
    onSecondaryContainer = Color(0xFF2A1747),
    tertiary = Teal40,
    // 选中非经期日期的实心圆底色：中性浅灰（不用主色红，避免与经期色混淆）
    surfaceVariant = Color(0xFFE0E0E0)
)

private val DarkColorScheme = darkColorScheme(
    primary = Rose80,
    onPrimary = Color(0xFF66002B),
    primaryContainer = RoseContainerDark,
    onPrimaryContainer = RoseContainerLight,
    secondary = Violet80,
    onSecondary = Color(0xFF3B2A5E),
    tertiary = Teal80,
    surfaceVariant = Color(0xFF424242)
)

/**
 * 领域语义色集合。
 *
 * 之所以不塞进 MaterialTheme.colorScheme，是因为 colorScheme 的槽位（primary/secondary…）
 * 语义是「视觉层级」，而这里需要的是「业务含义」——经期红不能因为主题换色就变成蓝色。
 *
 * 用独立的 CompositionLocal 承载，等价于后端在通用配置之外单独维护一份业务字典：
 * 两者生命周期与变更频率都不同，混在一起会让任何一方改动都波及另一方。
 */
@Immutable
data class PeriodColors(
    val periodConfirmed: Color,
    val periodPredicted: Color,
    val ovulation: Color,
    val fertileWindow: Color,
    val todayRing: Color,
    val onPeriod: Color
)

private val LightPeriodColors = PeriodColors(
    periodConfirmed = PeriodConfirmedLight,
    periodPredicted = PeriodPredictedLight,
    ovulation = OvulationLight,
    fertileWindow = FertileLight,
    todayRing = TodayRingLight,
    onPeriod = OnPeriodConfirmedLight
)

private val DarkPeriodColors = PeriodColors(
    periodConfirmed = PeriodConfirmedDark,
    periodPredicted = PeriodPredictedDark,
    ovulation = OvulationDark,
    fertileWindow = FertileDark,
    todayRing = TodayRingDark,
    onPeriod = OnPeriodConfirmedDark
)

/**
 * 用 staticCompositionLocalOf 而非 compositionLocalOf：
 * 这套颜色只在明暗切换时整体替换，不需要按调用点追踪变化。
 * static 版本变更时会使**所有**读取方重组，但因为它几乎永不变，代价为零；
 * 换来的是每次读取都不用做快照比对，重组时更快。
 */
val LocalPeriodColors = staticCompositionLocalOf { LightPeriodColors }

/**
 * 应用主题。
 *
 * ## 刻意不支持 Android 12+ 的动态取色（Monet）
 * Material3 提供的 dynamicLightColorScheme / dynamicDarkColorScheme 会跟随壁纸取色。
 * 本应用的领域色（经期红、排卵紫、易孕浅紫）必须保持稳定：
 * 若跟随壁纸，用户可能看到一个绿色的「经期」标记，语义直接崩塌。
 *
 * 顺带一个工程收益：Material3 1.4.0 起这套动态取色 API 已不在稳定的 material3 制品里，
 * 不引用它也就少一处版本兼容风险。
 *
 * 深色模式仍然支持——它改变的是明暗对比，不改变色相，与领域语义不冲突。
 */
@Composable
fun DemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val periodColors = if (darkTheme) DarkPeriodColors else LightPeriodColors

    CompositionLocalProvider(LocalPeriodColors provides periodColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
