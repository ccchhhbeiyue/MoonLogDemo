package com.example.demo.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 周期长度折线趋势图（P8），Canvas 自绘，不引第三方图表库（方案 §2.2 已定）。
 *
 * ## 坐标映射
 * 数据域 → 屏幕域就两个线性变换：
 * - x：第 i 个点等距排在 [pad, width-pad] 上；
 * - y：值越大越靠上，故用 `1 - (v - min) / span` 反转（屏幕 y 轴向下）。
 * 上下各留 [CHART_PADDING] 并把 min/max 外扩 [VALUE_MARGIN]，
 * 否则最大/最小的点会正好压在画布边缘被裁掉一半。
 *
 * ## 为什么均值画虚线
 * 折线看「每次的波动」，均值线看「整体水平」：某次周期离虚线越远说明那次越偏离常态，
 * 这是规律性评分的视觉化对照物。
 */
@Composable
fun CycleTrendChart(
    values: List<Int>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val avgColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Canvas 的 lambda 是 DrawScope 而非 composable 上下文，颜色必须在进画布前取好
    val holeColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas

        val pad = CHART_PADDING.toPx()
        val innerWidth = size.width - pad * 2
        val innerHeight = size.height - pad * 2
        val minValue = (values.min() - VALUE_MARGIN).toFloat()
        val maxValue = (values.max() + VALUE_MARGIN).toFloat()
        // 所有值相同时 span 为 0 会除零；外扩后至少保有 VALUE_MARGIN*2 的高度
        val span = (maxValue - minValue).coerceAtLeast(1f)
        // 只有一个点时 lastIndex=0，除以 0 会得到 NaN；coerceAtLeast(1) 让单点落在左端
        val lastIndex = values.lastIndex.coerceAtLeast(1)

        fun px(index: Int): Float = pad + innerWidth * index / lastIndex
        fun py(value: Float): Float = pad + innerHeight * (1f - (value - minValue) / span)

        // 均值虚线
        val avgY = py(values.average().toFloat())
        drawLine(
            color = avgColor,
            start = Offset(pad, avgY),
            end = Offset(pad + innerWidth, avgY),
            strokeWidth = AVG_LINE_WIDTH.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON.toPx(), DASH_OFF.toPx())),
        )

        // 折线
        if (values.size >= 2) {
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = px(index)
                val y = py(value.toFloat())
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = LINE_WIDTH.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // 数据点：外圈主色 + 内圈底色，压住折线拐角让每个周期清晰可数
        values.forEachIndexed { index, value ->
            val center = Offset(px(index), py(value.toFloat()))
            drawCircle(color = lineColor, radius = DOT_RADIUS.toPx(), center = center)
            drawCircle(color = holeColor, radius = DOT_HOLE_RADIUS.toPx(), center = center)
        }
    }
}

private val CHART_PADDING = 16.dp
private val VALUE_MARGIN = 2
private val LINE_WIDTH = 3.dp
private val AVG_LINE_WIDTH = 1.5.dp
private val DASH_ON = 8.dp
private val DASH_OFF = 6.dp
private val DOT_RADIUS = 6.dp
private val DOT_HOLE_RADIUS = 2.5.dp
