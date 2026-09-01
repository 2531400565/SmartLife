package com.smartlife.app.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartlife.app.ui.components.CountUpText
import com.smartlife.app.ui.theme.AnimSpec
import com.smartlife.app.ui.theme.Success
import com.smartlife.app.util.DateUtils

/**
 * 首页（v1.2.1 UI 优化版）：
 * 1. Hero Card —— 下一节课程（点击直达课表）
 * 2. 今日寄语 —— 压缩为 96dp 高的小卡
 * 3. 三张统计卡 —— 图标置于浅色圆形背景，数字更突出
 * 4. 最近考试 —— 使用 tertiaryContainer（M3 中对应 warning 语义），剩余天数分级配色
 * 5. 今日目标 —— 完成百分比 + 进度条（复用已有待办数据，不新增字段）
 *
 * 设计规范：全部颜色取自 MaterialTheme.colorScheme（无硬编码），
 * 圆角统一 16dp（shapes.medium），页面边距 20dp，卡片间距 12dp，深浅色自动适配。
 */
@Composable
fun DashboardScreen(
    onNavigateTodo: () -> Unit,
    onNavigateTimetable: () -> Unit,
    onNavigateFocus: () -> Unit,
    onNavigateExamList: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== 顶部：今日日期 =====
        Text(
            text = "今天",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = uiState.dateText,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (uiState.loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        } else {
            // ===== 1. Hero Card：下一节课程（v1.3 P2 淡入 300ms）=====
            var heroVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { heroVisible = true }
            val heroAlpha by animateFloatAsState(
                targetValue = if (heroVisible) 1f else 0f,
                animationSpec = tween(durationMillis = AnimSpec.LongMs, easing = AnimSpec.standard),
                label = "heroFadeIn"
            )
            NextCourseCard(
                nextCourse = uiState.nextCourse,
                onClick = onNavigateTimetable,
                modifier = Modifier.graphicsLayer { alpha = heroAlpha }
            )

            // ===== 2. 今日寄语（小卡）=====
            QuoteCard(
                quote = uiState.quoteText,
                period = uiState.quotePeriod,
                onClick = viewModel::refreshQuote
            )

            // ===== 3. 三张统计卡片 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "待办",
                    value = uiState.todayTodoCount.toString(),
                    icon = Icons.Outlined.Checklist,
                    onClick = onNavigateTodo,
                    modifier = Modifier.weight(1f),
                    countUpValue = uiState.todayTodoCount
                )
                StatCard(
                    label = "课程",
                    value = uiState.todayCourseCount.toString(),
                    icon = Icons.Outlined.School,
                    onClick = onNavigateTimetable,
                    modifier = Modifier.weight(1f),
                    countUpValue = uiState.todayCourseCount
                )
                StatCard(
                    label = "专注",
                    value = uiState.todayFocusText,
                    icon = Icons.Outlined.Timer,
                    onClick = onNavigateFocus,
                    modifier = Modifier.weight(1f)
                )
            }

            // ===== 4. 最近考试（点击进入考试倒计时列表页）=====
            ExamCard(nextExam = uiState.nextExam, onClick = onNavigateExamList)

            // ===== 5. 今日目标 =====
            TodayGoalCard(goal = uiState.todayGoal)
        }
    }
}

/**
 * Hero Card：下一节课程（v1.2.1）。
 * 有课时显示 课程名 / 上课时间 / 教室 / 右侧 Badge「X 分钟后」；无课显示「今天没有课程 🎉」。
 * 整卡点击跳转课表。
 */
@Composable
private fun NextCourseCard(
    nextCourse: NextCourse?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        if (nextCourse == null) {
            // ===== 今天没有课程 =====
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "今天没有课程 🎉",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = heroSubtitle(nextCourse),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "下一节",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = nextCourse.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )

                    // 动态副标题（v1.3 P1）：根据距离上课的剩余时间变化
                    Text(
                        text = heroSubtitle(nextCourse),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )

                    // 上课时间
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${
                                DateUtils.formatMinute(nextCourse.startMinute)
                            }–${DateUtils.formatMinute(nextCourse.endMinute)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }

                    // 教室（有则显示）
                    if (!nextCourse.location.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = nextCourse.location,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }

                // 右侧圆角 Badge：距离开始
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = untilStartText(nextCourse.minutesUntilStart),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Hero Card 副标题（v1.3 P1）：根据「有无课」与「距离上课的剩余时间」动态变化。
 *
 * 无课时固定为「好好享受今天的自由时间」；有课时按紧迫程度给出不同提示。
 */
private fun heroSubtitle(nextCourse: NextCourse?): String = when {
    nextCourse == null -> "好好享受今天的自由时间"
    nextCourse.minutesUntilStart <= 15 -> "马上就要上课了，快出发吧 🏃"
    nextCourse.minutesUntilStart <= 60 -> "提前几分钟到教室，状态会更好"
    else -> "今天还有课，保持自己的节奏 ✨"
}

/** 距离上课的文案：不足 1 小时按分钟，超过按小时。 */
private fun untilStartText(minutes: Int): String = when {
    minutes < 60 -> "$minutes 分钟后"
    else -> {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "$h 小时后" else "$h 小时${m}分后"
    }
}

/**
 * 今日寄语卡（v1.2.1 缩小版）：固定 96dp 高，"点按换一条"功能保留。
 */
@Composable
private fun QuoteCard(quote: String, period: QuotePeriod, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 时段标签（☀️ 晨间励志 / ⚡ 午后效率 / 🌙 晚间放松）+ 换一条提示
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FormatQuote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${period.emoji} ${period.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "点按换一条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
            }

            // 寄语正文：数据源仍为数据库内置寄语，未改动任何表结构
            Text(
                text = "“$quote”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2
            )
        }
    }
}

/**
 * 统计卡片（v1.2.1 升级）：图标置于浅色圆形背景，数字更突出、标签更小。
 */
@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 传入整数则启用数字滚动（v1.3 P2）；为 null 时按原样显示文本（如专注时长）。 */
    countUpValue: Int? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 图标：浅色圆形背景
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (countUpValue != null) {
                CountUpText(
                    value = countUpValue,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    durationMillis = 600
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 最近考试卡片（v1.2.1）：使用 tertiaryContainer（M3 中承载 warning 语义的容器色，非纯白）。
 *
 * 剩余天数分级配色（全部取自 colorScheme，无硬编码）：
 * - ≥30 天 → primary（本应用主色为蓝）
 * - 7~29 天 → tertiary
 * - ≤6 天  → error（红）
 */
@Composable
private fun ExamCard(nextExam: NextExam?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "最近考试",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(start = 6.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                // 入口提示：点击进入考试倒计时列表页（v1.3 P3）
                Text(
                    text = "查看全部",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }

            if (nextExam == null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "暂无考试 🎉",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "继续保持，加油学习！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nextExam.courseName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1
                        )
                        Text(
                            text = "考试日期 ${DateUtils.formatDateDash(nextExam.examDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = when (nextExam.daysLeft) {
                                0 -> "今天"
                                1 -> "明天"
                                else -> nextExam.daysLeft.toString()
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = examDaysColor(nextExam.daysLeft),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = if (nextExam.daysLeft <= 1) "考试" else "天后",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 剩余天数 → 配色（全部取自 colorScheme，无硬编码）。
 *
 * 阈值与「考试倒计时列表页」保持一致（v1.3 P3）：
 * ≤7 天红色（error）、8~30 天橙色（tertiary）、30 天以上蓝色（primary）。
 */
@Composable
private fun examDaysColor(daysLeft: Int): androidx.compose.ui.graphics.Color = when {
    daysLeft > 30 -> MaterialTheme.colorScheme.primary
    daysLeft > 7 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

/**
 * 今日目标卡片（v1.3.1 重构）：根据今日待办状态自动切换三种体验。
 *
 * - EMPTY（无待办）：「🌿 今天没有待办」+ 灰色占位条，不显示百分比；
 * - PROGRESS（进行中）：大数字百分比（CountUp）+「已完成 X / Y 项，还剩 N 项」+ 进度条动画填充；
 * - COMPLETED（全部完成）：100%（CountUp）+ 庆祝文案 + 绿色满格进度条。
 *
 * 数据复用已有待办（[TodayGoal]），不新增数据库字段、不改 Repository；
 * 状态切换使用 AnimatedContent（250ms），全部颜色取自 colorScheme / 主题语义色。
 */
@Composable
private fun TodayGoalCard(goal: TodayGoal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "今日目标",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            // 内容区：三种状态淡入淡出切换（v1.3.1，250ms）
            AnimatedContent(
                targetState = goal.state,
                transitionSpec = {
                    fadeIn(animationSpec = tween(AnimSpec.MediumMs, easing = AnimSpec.standard)) togetherWith
                        fadeOut(animationSpec = tween(AnimSpec.MediumMs, easing = AnimSpec.standard))
                },
                label = "goalStateSwitch"
            ) { state ->
                when (state) {
                    GoalState.EMPTY -> EmptyGoalBody()
                    GoalState.PROGRESS -> ProgressGoalBody(goal)
                    GoalState.COMPLETED -> CompletedGoalBody(goal)
                }
            }

            // 进度条：Empty 灰色占位 / Progress 品牌蓝 / Completed 绿色语义色
            GoalProgressBar(goal)
        }
    }
}

/** 状态一 · 无待办（v1.3.1）：🌿 今天没有待办 + 副文案，不显示百分比。 */
@Composable
private fun EmptyGoalBody() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "🌿 今天没有待办",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "难得轻松一天，好好享受今天。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/** 状态二 · 进行中（v1.3.1）：大数字百分比（CountUp）+ 已完成 X / Y 项，还剩 N 项。 */
@Composable
private fun ProgressGoalBody(goal: TodayGoal) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        CountUpText(
            value = goal.percent,
            suffix = "%",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "已完成 ${goal.completed} / ${goal.total} 项，还剩 ${(goal.total - goal.completed).coerceAtLeast(0)} 项",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 状态三 · 全部完成（v1.3.1）：100%（CountUp）+ 庆祝文案 + 副文案。 */
@Composable
private fun CompletedGoalBody(goal: TodayGoal) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CountUpText(
            value = goal.percent,
            suffix = "%",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "🎉 今日任务全部完成！",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "今天做得不错，继续保持！",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/** 进度条（v1.3.1）：按状态着色并动画填充（250ms）。 */
@Composable
private fun GoalProgressBar(goal: TodayGoal) {
    val barColor = when (goal.state) {
        GoalState.EMPTY -> MaterialTheme.colorScheme.surfaceVariant
        GoalState.PROGRESS -> MaterialTheme.colorScheme.primary
        GoalState.COMPLETED -> Success
    }
    val trackColor = when (goal.state) {
        GoalState.EMPTY -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    var progressReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { progressReady = true }
    val animatedProgress by animateFloatAsState(
        targetValue = if (progressReady) goal.percent / 100f else 0f,
        animationSpec = tween(durationMillis = AnimSpec.MediumMs, easing = AnimSpec.standard),
        label = "goalProgress"
    )
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(MaterialTheme.shapes.medium),
        color = barColor,
        trackColor = trackColor
    )
}
