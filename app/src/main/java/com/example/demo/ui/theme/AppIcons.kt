package com.example.demo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 项目自绘图标集。
 *
 * ## 为什么不用 Icons.Filled.Xxx
 * `androidx.compose.material:material-icons-core` / `-extended` 这两个制品**已停止维护**，
 * 最后版本停在 1.7.8（2025-02）。而 Material3 从 1.4.0 起也不再传递依赖它们
 * （本项目实测：material3 1.4.0 只依赖 material-ripple，icons 完全不在 classpath 上）。
 * 强行引入 1.7.8 会把 Compose 1.7.x 的传递依赖拖进来，与 BOM 2026.02.01 解析出的
 * 1.10.5 / 1.11.0 混在一起，属于典型的"能跑但埋雷"。
 *
 * ## 自绘的代价与收益
 * 代价：图标不是像素级还原 Material 官方造型。
 * 收益：零依赖、零版本冲突、体积可控（官方 core 集有约 49 个图标全量打包）。
 * 对本项目这种只需要 6 个图标的场景，收益远大于代价。
 *
 * ## 实现要点
 * 1. 全部基于 24x24 viewport，与 Material 规范一致，塞进 NavigationBarItem 不会尺寸失调；
 * 2. 除播放键外一律用 **stroke 描边**而非 fill 填充——描边只要给出中心线坐标，
 *    比手工推算填充轮廓的外扩路径简单得多，也不容易画歪；
 * 3. 描边颜色写死 Color.Black 不影响着色：`Icon` 组件通过 `ColorFilter.tint`（SrcIn 混合）
 *    对**渲染结果**重新上色，描边和填充一样会被染色；
 * 4. 用 `by lazy` 而不是顶层 val 直接初始化：ImageVector 构建要跑一段 PathBuilder DSL，
 *    放在类加载时执行会白白拖慢冷启动，懒加载则只在实际用到的页面上付出成本。
 */
private const val VIEWPORT = 24f

/** 描边宽度。Material 图标在 24dp 下的视觉线宽约 2dp */
private const val STROKE_WIDTH = 2f

/**
 * 日历（底部导航·首页）。
 * 造型：矩形外框 + 表头分隔线 + 顶部两个挂环。
 */
val IconCalendar: ImageVector by lazy {
    ImageVector.Builder(
        name = "IconCalendar",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // 外框：注意 stroke 会向两侧各外扩 1，所以坐标留了 4~20 的边距
            moveTo(4f, 6f)
            lineTo(20f, 6f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            // 表头分隔线（月份标题与日期网格的分界）
            moveTo(4f, 10.5f)
            lineTo(20f, 10.5f)
            // 两个挂环
            moveTo(8.5f, 3.5f)
            lineTo(8.5f, 7.5f)
            moveTo(15.5f, 3.5f)
            lineTo(15.5f, 7.5f)
        }
    }.build()
}

/**
 * 待办（底部导航）。
 * 造型：圆圈 + 勾。圆圈用两段半圆弧拼成——PathBuilder 没有 drawCircle，
 * arcTo 的两个半圆（isMoreThanHalf=false，同向）是最简写法。
 */
val IconCheckCircle: ImageVector by lazy {
    ImageVector.Builder(
        name = "IconCheckCircle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 3.5f)
            arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 20.5f)
            arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 3.5f)
            close()
            // 勾
            moveTo(8.2f, 12.3f)
            lineTo(11f, 15.1f)
            lineTo(15.8f, 9.4f)
        }
    }.build()
}

/**
 * 工作日志（底部导航）。
 * 造型：三行"圆点 + 横线"的列表。
 * 圆点用长度仅 0.01 的线段实现——配合 StrokeCap.Round 会渲染成一个直径等于线宽的实心点。
 * 这比再画一个小圆更省事，且不会因为填充/描边模式不同而出现粗细不一致。
 */
val IconWorkLog: ImageVector by lazy {
    ImageVector.Builder(
        name = "IconWorkLog",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4f, 6.5f)
            lineTo(4.01f, 6.5f)
            moveTo(8.5f, 6.5f)
            lineTo(20f, 6.5f)

            moveTo(4f, 12f)
            lineTo(4.01f, 12f)
            moveTo(8.5f, 12f)
            lineTo(20f, 12f)

            moveTo(4f, 17.5f)
            lineTo(4.01f, 17.5f)
            moveTo(8.5f, 17.5f)
            lineTo(20f, 17.5f)
        }
    }.build()
}

/**
 * 专注/播放（底部导航）。
 * 唯一用 fill 的图标：三角形是实心造型，用描边会显得单薄，且播放键按 Material 规范本就是实心的。
 * pathFillType 保持默认 NonZero 即可，单一闭合轮廓不存在镂空问题。
 */
val IconPlay: ImageVector by lazy {
    ImageVector.Builder(
        name = "IconPlay",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(8f, 5.2f)
            lineTo(19f, 12f)
            lineTo(8f, 18.8f)
            close()
        }
    }.build()
}

/**
 * 设置（日历页右上角）。
 * 造型：三条滑轨 + 三个滑块（"调节器"样式）。
 *
 * 没有画齿轮——齿轮的齿廓需要十几段贝塞尔曲线，手工推算极易画歪。
 * 滑轨式设置图标同样是被广泛认可的语义，且每条线只需 4 个数字。
 *
 * 关键细节：滑轨必须在滑块两侧**断开**，否则一条实线直接穿过圆圈，视觉上就成了"靶心"。
 */
val IconSettings: ImageVector by lazy {
    ImageVector.Builder(
        name = "IconSettings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round
        ) {
            // 第一行：滑块在 x=8，半径 2 + 半个线宽 1 = 断口留到 6 / 10
            moveTo(3.5f, 7f)
            lineTo(6f, 7f)
            moveTo(10f, 7f)
            lineTo(20.5f, 7f)
            // 第二行：滑块在 x=16
            moveTo(3.5f, 12f)
            lineTo(14f, 12f)
            moveTo(18f, 12f)
            lineTo(20.5f, 12f)
            // 第三行：滑块在 x=11
            moveTo(3.5f, 17f)
            lineTo(9f, 17f)
            moveTo(13f, 17f)
            lineTo(20.5f, 17f)

            // 三个滑块圆环（半径 2）
            moveTo(8f, 5f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 9f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 5f)
            close()

            moveTo(16f, 10f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 14f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 10f)
            close()

            moveTo(11f, 15f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 11f, 19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 11f, 15f)
            close()
        }
    }.build()
}

/**
 * 返回箭头（二级页面左上角）。
 * 对应官方的 Icons.AutoMirrored.Filled.ArrowBack —— 自绘版本同样要遵守 RTL 镜像语义，
 * 但 ImageVector 本身不带 autoMirror 标记，需要镜像时由调用方加 Modifier.layoutDirection 处理。
 * 本项目未启用阿拉伯语等 RTL 语言，暂不处理。
 */
val IconArrowBack: ImageVector by lazy {
    ImageVector.Builder(
        name = "IconArrowBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // 箭杆
            moveTo(20f, 12f)
            lineTo(4.5f, 12f)
            // 箭羽
            moveTo(10.5f, 6f)
            lineTo(4.5f, 12f)
            lineTo(10.5f, 18f)
        }
    }.build()
}

/**
 * 前进箭头（日历页切换到下个月）。
 * 造型是 [IconArrowBack] 的水平镜像：箭杆从左到右，箭羽在右端张开。
 * 与返回箭头成对出现，构成日历顶部的「上个月 / 下个月」翻页控件。
 */
val IconArrowForward: ImageVector by lazy {
    ImageVector.Builder(
        name = "IconArrowForward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // 箭杆（方向与返回箭头相反）
            moveTo(4f, 12f)
            lineTo(19.5f, 12f)
            // 箭羽
            moveTo(13.5f, 6f)
            lineTo(19.5f, 12f)
            lineTo(13.5f, 18f)
        }
    }.build()
}

/**
 * 加号（待办页 FAB 新建）。
 * 造型：一横一竖两条等长线段交于中心。
 */
val IconAdd: ImageVector by lazy {
    ImageVector.Builder(
        name = "IconAdd",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }.build()
}

/**
 * 删除（待办列表项 / 编辑对话框）。
 * 造型：桶盖横线 + 提手 + 桶身 + 桶内两条竖纹。
 */
val IconDelete: ImageVector by lazy {
    ImageVector.Builder(
        name = "IconDelete",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // 桶盖
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            // 提手
            moveTo(9.5f, 7f)
            lineTo(9.5f, 4.5f)
            lineTo(14.5f, 4.5f)
            lineTo(14.5f, 7f)
            // 桶身
            moveTo(6.5f, 7f)
            lineTo(7.5f, 20f)
            lineTo(16.5f, 20f)
            lineTo(17.5f, 7f)
            // 桶内竖纹
            moveTo(10.5f, 11f)
            lineTo(10.5f, 16.5f)
            moveTo(13.5f, 11f)
            lineTo(13.5f, 16.5f)
        }
    }.build()
}

/**
 * 统计（计划卡片的详情入口，P10-7）。
 * 造型：一条基线 + 三根高矮不一的竖柱，即最直白的柱状图语义。
 *
 * 竖柱的底端直接落在基线的 y 上（而不是留间隙）：配合 StrokeCap.Round，
 * 两端各外扩半个线宽后恰好形成 T 字连接，看上去柱子是"坐"在基线上的。
 * 三根柱子高度取 7 / 13.5 / 10，故意不做成等差——等高的柱子读起来像栅栏而非数据。
 */
val IconStats: ImageVector by lazy {
    ImageVector.Builder(
        name = "IconStats",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round
        ) {
            // 基线
            moveTo(3.5f, 20f)
            lineTo(20.5f, 20f)
            // 三根竖柱：左矮、中高、右中
            moveTo(7f, 20f)
            lineTo(7f, 13f)
            moveTo(12f, 20f)
            lineTo(12f, 6.5f)
            moveTo(17f, 20f)
            lineTo(17f, 10f)
        }
    }.build()
}
