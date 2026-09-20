package com.example.demo.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.demo.domain.model.TimerMode
import com.example.demo.service.PomodoroService
import com.example.demo.service.TimerPhase

/**
 * 番茄钟圆盘倒计时（P7）。Canvas 自绘，不引第三方图表/进度库。
 *
 * ## 画法
 * 先画一整圈浅色「轨道」，再按剩余比例画一段主色「进度弧」。
 * 进度弧从 -90°（正上方）起笔、顺时针扫过 `360° × 剩余/总长`，
 * 于是剩余越多弧越长，随时间流逝弧逐渐「吃掉」自己，直观表达倒计时。
 *
 * ## 为什么进度用 remaining/total 而不是已走比例
 * 倒计时关心的是「还剩多少」，用剩余比例让弧长与剩余时间正相关，
 * 用户扫一眼弧的长短就知道还剩多久，无需读数字。
 */
@Composable
fun PomodoroDial(
    remainingMillis: Long,
    totalMillis: Long,
    phase: TimerPhase,
    modifier: Modifier = Modifier,
    mode: TimerMode = TimerMode.POMODORO,
    elapsedMillis: Long = 0L,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = when (phase) {
        TimerPhase.PAUSED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    // 正计时没有总时长，环无法表达「剩余比例」，只画轨道、中心显示已走时长；
    // 倒计时/番茄钟才按 remaining/total 画进度弧；完成态画满环表示「走完了」。
    val isCountUp = mode == TimerMode.COUNTUP
    val isCompleted = phase == TimerPhase.COMPLETED
    val fraction = when {
        isCompleted -> 1f
        !isCountUp && totalMillis > 0L -> (remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
        else -> 0f
    }
    val centerText = when {
        isCompleted -> "完成"
        isCountUp -> "+" + PomodoroService.formatClock(elapsedMillis)
        else -> PomodoroService.formatClock(remainingMillis)
    }

    Box(modifier = modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val stroke = 18.dp.toPx()
            // 内缩半个线宽，避免圆弧贴边被裁掉
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // 轨道：整圈
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = topLeft,
                size = arcSize,
            )
            // 进度弧：从正上方起、按剩余比例扫过
            if (fraction > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = topLeft,
                    size = arcSize,
                )
            }
        }
        Text(
            text = centerText,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
