package com.smartlife.app.ui.screen.analytics

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartlife.app.ui.components.CountUpText
import com.smartlife.app.ui.theme.AnimSpec
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 数据分析页（v2.0 P3）。
 *
 * 三大板块，全部基于已有数据内存计算（不新增数据库字段）：
 * 1. 专注趋势：近 7 天柱状图 + 近 30 天折线图（Canvas 手绘，零第三方图表依赖）；
 * 2. 待办效率：完成率（进度条）+ 日均完成数 + 总量明细；
 * 3. 课程分布：课程总数 + 第 1 周~当前周每周课程数柱状图（单双周按周次计入）。
 *
 * 颜色全部取自 MaterialTheme，深浅色模式自动适配，无硬编码色值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // v2.0 P4：三张卡依次入场（淡入 + 轻微上移，80ms 错开）
                    item(key = "focus") { EntranceCard(delayMs = 0) { FocusTrendCard(state = uiState) } }
                    item(key = "todo") { EntranceCard(delayMs = 80) { TodoStatsCard(state = uiState) } }
                    item(key = "course") { EntranceCard(delayMs = 160) { CourseStatsCard(state = uiState) } }
                }
            }
        }
    }
}

// ============================================================
// 通用小组件（仅本文件使用，颜色全部取自 colorScheme）
// ============================================================

/**
 * 卡片入场包装（v2.0 P4，RC 修复）：淡入 + 轻微上移；
 * [delayMs] 用于多卡依次入场错开，时长统一取 [AnimSpec.LongMs]。
 *
 * 这里刻意**不用** AnimatedVisibility：它入场前不参与布局，
 * 延迟期间 item 高度为 0，卡片会「先空白、再突然撑开」，下方内容跟着跳一下。
 * 改为始终占位 + 只做透明度/位移，布局位置从第一帧就是稳定的。
 */
@Composable
private fun EntranceCard(delayMs: Int, content: @Composable () -> Unit) {
    var ready by remember { mutableStateOf(delayMs == 0) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        ready = true
    }
    val spec = tween<Float>(durationMillis = AnimSpec.LongMs, easing = AnimSpec.standard)
    val alpha by animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = spec,
        label = "cardAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (ready) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = AnimSpec.LongMs, easing = AnimSpec.standard),
        label = "cardOffsetY"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = offsetY.toPx()
        }
    ) {
        content()
    }
}

/** 卡片标题行：小图标 + 标题。 */
@Composable
private fun CardTitle(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 图表小节标题（如 "近 7 天"）。 */
@Composable
private fun ChartSubTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** 双列大数字指标：大数值 + 标签（图表卡底部汇总用）。 */
@Composable
private fun MetricPair(firstLabel: String, firstValue: String, secondLabel: String, secondValue: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MiniMetric(label = firstLabel, value = firstValue, modifier = Modifier.weight(1f))
        MiniMetric(label = secondLabel, value = secondValue, modifier = Modifier.weight(1f))
    }
}

/** 小指标：数值 + 标签（居中对齐）。 */
@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 秒/分钟时长 → "X小时Y分" / "X分" / "X分钟"。 */
private fun formatMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}小时${m}分"
        h > 0 -> "${h}小时"
        else -> "${m}分钟"
    }
}

// ============================================================
// 一、专注趋势卡：近 7 天柱状 + 近 30 天折线
// ============================================================

@Composable
private fun FocusTrendCard(state: AnalyticsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CardTitle(icon = Icons.Outlined.Timer, title = "专注趋势")

            // ===== 近 7 天柱状图 =====
            ChartSubTitle("近 7 天")
            Focus7DayBarChart(data = state.focus7)
            HorizontalDivider()

            // ===== 近 30 天折线图 =====
            ChartSubTitle("近 30 天")
            Focus30DayLineChart(data = state.focus30)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniMetric(
                    label = "近30天总时长",
                    value = formatMinutes(state.focus30TotalMinutes),
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "日均专注",
                    value = formatMinutes(state.focus30AvgMinutes),
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "单日峰值",
                    value = "${state.focus30BestMinutes}分钟",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 近 7 天柱状图：每日专注分钟数；今天用 tertiary 高亮（与首页本周图一致）；柱体生长动画（v2.0 P4）。 */
@Composable
private fun Focus7DayBarChart(data: List<FocusDayPoint>) {
    val trackHeight = 88.dp
    val maxMinutes = data.maxOfOrNull { it.minutes }?.coerceAtLeast(1L) ?: 1L
    // 柱体生长：进入页面后 0 → 1
    var chartReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chartReady = true }
    val grow by animateFloatAsState(
        targetValue = if (chartReady) 1f else 0f,
        animationSpec = tween(durationMillis = AnimSpec.MediumMs, easing = AnimSpec.standard),
        label = "barGrow7d"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        data.forEachIndexed { index, point ->
            val fraction = (point.minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
            val barHeight = ((88f * fraction).coerceAtLeast(if (point.minutes > 0) 4f else 0f) * grow).dp
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (point.minutes > 0) "${point.minutes}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (point.isToday) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                }
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (point.isToday) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(2.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MiniMetric(
            label = "近7天总时长",
            value = formatMinutes(data.sumOf { it.minutes }),
            modifier = Modifier.weight(1f)
        )
        MiniMetric(
            label = "日均专注",
            value = formatMinutes(if (data.isNotEmpty()) data.sumOf { it.minutes } / data.size else 0L),
            modifier = Modifier.weight(1f)
        )
        MiniMetric(
            label = "峰值日",
            value = data.maxByOrNull { it.minutes }?.takeIf { it.minutes > 0 }?.label ?: "—",
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 近 30 天折线图（Canvas 手绘）：
 * - 3 条水平网格线 + 折线 + 浅色面积填充 + 数据点圆点；
 * - 今天（最右点）用 tertiary 高亮大点；
 * - 底部每约 1/5 显示一个日期标签（末位即今天）；
 * - 折线按进度从左侧渐次绘制（v2.0 P4）。
 * 全部颜色取自 colorScheme，无硬编码。
 */
@Composable
private fun Focus30DayLineChart(data: List<FocusDayPoint>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val todayColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val hasData = data.any { it.minutes > 0 }
    // 折线渐进绘制：进入页面后 0 → 1
    var chartReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chartReady = true }
    val drawProgress by animateFloatAsState(
        targetValue = if (chartReady) 1f else 0f,
        animationSpec = tween(durationMillis = AnimSpec.LongMs, easing = AnimSpec.standard),
        label = "lineGrow30d"
    )

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val maxMinutes =
                data.maxOfOrNull { it.minutes }?.coerceAtLeast(1L)?.toFloat() ?: 1f
            val n = data.size
            val stepX = if (n > 1) size.width / (n - 1) else size.width
            val topPad = 12.dp.toPx()
            val bottomPad = 10.dp.toPx()
            val chartHeight = size.height - topPad - bottomPad
            val baselineY = topPad + chartHeight

            // 水平网格线（4 等分）
            for (i in 0..3) {
                val y = topPad + chartHeight * i / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            if (hasData && n > 0) {
                // 按进度截取已绘制的点数（至少 1 个点，x 仍按全宽分布）
                val visibleCount = (n * drawProgress).toInt().coerceIn(1, n)
                val points = data.take(visibleCount).mapIndexed { index, point ->
                    Offset(
                        x = index * stepX,
                        y = topPad + chartHeight * (1f - (point.minutes / maxMinutes).coerceIn(0f, 1f))
                    )
                }

                // 面积填充（浅色渐变感，透明度模拟）
                val fillPath = Path().apply {
                    moveTo(points.first().x, baselineY)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, baselineY)
                    close()
                }
                drawPath(
                    path = fillPath,
                    color = lineColor.copy(alpha = 0.12f)
                )

                // 折线
                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 数据点：今天用 tertiary 高亮
                points.forEachIndexed { index, p ->
                    val isToday = data[index].isToday
                    drawCircle(
                        color = if (isToday) todayColor else lineColor,
                        radius = if (isToday) 5.dp.toPx() else 3.dp.toPx(),
                        center = p
                    )
                }
            } else {
                // 无记录：画一条基线示意
                drawLine(
                    color = gridColor,
                    start = Offset(0f, baselineY),
                    end = Offset(size.width, baselineY),
                    strokeWidth = 1f
                )
            }
        }

        if (hasData) {
            // 底部日期标签：均分 6 个，末位为今天
            val labelCount = 6
            val labelIndexes = (0 until labelCount).map { idx ->
                (data.size - 1) * idx / (labelCount - 1)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                labelIndexes.forEach { index ->
                    Text(
                        text = data[index].label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (data[index].isToday) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }
        } else {
            Text(
                text = "近 30 天暂无专注记录，完成一次番茄专注后这里会出现趋势图",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================
// 二、待办效率卡：完成率 + 日均完成数
// ============================================================

@Composable
private fun TodoStatsCard(state: AnalyticsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CardTitle(icon = Icons.Outlined.CheckCircle, title = "待办效率")

            // v2.0 P4：进度条填充动画（卡片入场后 0 → 完成率）
            var progressReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { progressReady = true }
            val completionProgress by animateFloatAsState(
                targetValue = if (progressReady) state.completionRate / 100f else 0f,
                animationSpec = tween(durationMillis = AnimSpec.MediumMs, easing = AnimSpec.standard),
                label = "completionRate"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ===== 完成率（大数字 CountUp + 进度条）=====
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CountUpText(
                        value = state.completionRate,
                        suffix = "%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "完成率",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { completionProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // 竖向分隔
                HorizontalDivider(
                    modifier = Modifier
                        .height(72.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )

                // ===== 日均完成数 =====
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f", state.avgDailyCompleted),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "日均完成（项/天）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "按最早创建日至今计算",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // ===== 总量明细 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniMetric(label = "总待办", value = state.todoTotal.toString(), modifier = Modifier.weight(1f))
                MiniMetric(label = "已完成", value = state.todoCompleted.toString(), modifier = Modifier.weight(1f))
                MiniMetric(label = "进行中", value = state.todoIncomplete.toString(), modifier = Modifier.weight(1f))
            }
        }
    }
}

// ============================================================
// 三、课程分布卡：课程总数 + 每周课程数柱状图
// ============================================================

@Composable
private fun CourseStatsCard(state: AnalyticsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CardTitle(icon = Icons.Outlined.CalendarMonth, title = "课程分布")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniMetric(
                    label = "本学期课程",
                    value = "${state.courseTotal} 门",
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = if (state.currentWeek > 0) "当前周次" else "学期状态",
                    value = if (state.currentWeek > 0) "第 ${state.currentWeek} 周" else "未开学",
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            if (state.currentWeek > 0 && state.weekCourses.isNotEmpty()) {
                val fromWeek = state.weekCoursesFrom
                Text(
                    text = if (fromWeek < state.currentWeek) {
                        "每周课程数（第 $fromWeek ~ ${state.currentWeek} 周）"
                    } else {
                        "每周课程数（第 ${state.currentWeek} 周）"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                WeekCourseBarChart(
                    weekCourses = state.weekCourses,
                    fromWeek = fromWeek,
                    best = state.weekCoursesBest
                )
                Text(
                    text = "单双周课程按周次计入对应周；红色为课程最多的周。" +
                        if (fromWeek > 1) "仅展示最近 ${state.weekCourses.size} 周。" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "设置学期开始日期后，这里会展示每周课程分布（单双周按周次计入）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 每周课程数柱状图：柱高按峰值等比缩放；课程最多的周用 error 强调，其余 primary；柱体生长动画（v2.0 P4）。 */
@Composable
private fun WeekCourseBarChart(weekCourses: List<Int>, fromWeek: Int, best: Int) {
    val trackHeight = 72.dp
    val maxCount = best.coerceAtLeast(1)
    // 柱体生长：进入页面后 0 → 1
    var chartReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chartReady = true }
    val grow by animateFloatAsState(
        targetValue = if (chartReady) 1f else 0f,
        animationSpec = tween(durationMillis = AnimSpec.MediumMs, easing = AnimSpec.standard),
        label = "barGrowWeek"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (weekCourses.size > 12) 2.dp else 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        weekCourses.forEachIndexed { index, count ->
            val fraction = (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
            val barHeight = ((72f * fraction).coerceAtLeast(if (count > 0) 4f else 0f) * grow).dp
            val isBest = count == best && best > 0
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = if (count > 0 && weekCourses.size <= 12) "$count" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isBest) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                }
                // 仅首尾显示周次标签，中间省略避免拥挤
                if (index == 0 || index == weekCourses.lastIndex) {
                    Text(
                        text = "第${fromWeek + index}周",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
