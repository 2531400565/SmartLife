package com.smartlife.app.ui.screen.exam

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartlife.app.ui.components.CountUpText
import com.smartlife.app.ui.theme.AnimSpec
import com.smartlife.app.util.DateUtils

/**
 * 考试中心（v1.3 P3 列表页，v2.0 P1 增强）：
 *
 * - 顶部统计：本学期考试数量 / 最近考试 / 已结束考试；
 * - 课程名称实时搜索；
 * - 考试列表按剩余天数升序，按紧急程度分色（≤7 天红 / 8~30 天橙 / 30 天以上蓝）；
 * - 每张卡显示课程名、考试日期、剩余天数、教室（可选）；
 * - 数据来源仍为 CourseEntity.examDate，不新增任何数据库字段。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamListScreen(
    onBack: () -> Unit,
    viewModel: ExamListViewModel = viewModel(factory = ExamListViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("考试中心") },
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
                    // ===== 顶部统计 =====
                    item(key = "stats") { ExamStatsCard(stats = uiState.stats) }

                    // ===== 搜索 =====
                    item(key = "search") {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索课程名称") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null)
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )
                    }

                    // ===== 筛选 Tab：全部 / 未结束 / 已结束（默认未结束）=====
                    item(key = "filter") {
                        PrimaryTabRow(
                            selectedTabIndex = uiState.filter.ordinal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ExamFilter.entries.forEach { f ->
                                Tab(
                                    selected = uiState.filter == f,
                                    onClick = { viewModel.setFilter(f) },
                                    text = { Text(f.label) }
                                )
                            }
                        }
                    }

                    if (uiState.exams.isEmpty()) {
                        item(key = "empty") {
                            EmptyHintState(
                                hasQuery = uiState.query.isNotBlank(),
                                filter = uiState.filter
                            )
                        }
                    } else {
                        items(uiState.exams, key = { it.courseId }) { exam ->
                            // v2.0 P4：列表项统一入场（淡入 + 轻微上移）；
                            // 新增项滑入、已有项复用状态保持不变
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { visible = true }
                            AnimatedVisibility(
                                visible = visible,
                                enter = AnimSpec.enterFadeSlide()
                            ) {
                                ExamCardItem(exam = exam)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶部统计卡：本学期考试数量 / 最近考试 / 已结束考试（v2.0 P1）。
 */
@Composable
private fun ExamStatsCard(stats: ExamStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "本学期考试",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            CountUpText(
                value = stats.totalCount,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NearestExamColumn(
                    stats = stats,
                    modifier = Modifier.weight(1.3f)
                )
                HorizontalDivider(
                    modifier = Modifier
                        .height(48.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                StatsColumn(
                    label = "已结束",
                    value = stats.endedCount,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 统计列：上方小标签，下方大数值（数字滚动 CountUp）。 */
@Composable
private fun StatsColumn(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        CountUpText(
            value = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 「最近考试」统计列（v2.0 体验补充）：
 * D-Day 大数字（按紧急程度分色）+ 课程名称 + 考试日期；
 * 没有未结束考试时显示「无」。
 */
@Composable
private fun NearestExamColumn(stats: ExamStats, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "最近考试",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (stats.nearestName.isBlank()) {
            Text(
                text = "无",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            if (stats.nearestDaysLeft <= 0) {
                Text(
                    text = "D-DAY",
                    style = MaterialTheme.typography.headlineSmall,
                    color = urgencyAccent(examUrgency(stats.nearestDaysLeft))
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "D-",
                        style = MaterialTheme.typography.headlineSmall,
                        color = urgencyAccent(examUrgency(stats.nearestDaysLeft))
                    )
                    CountUpText(
                        value = stats.nearestDaysLeft,
                        style = MaterialTheme.typography.headlineSmall,
                        color = urgencyAccent(examUrgency(stats.nearestDaysLeft))
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stats.nearestName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text(
                text = DateUtils.formatDateDash(stats.nearestExamDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 紧急程度 → 强调色（取自 colorScheme，无硬编码）。 */
@Composable
private fun urgencyAccent(urgency: ExamUrgency): Color = when (urgency) {
    ExamUrgency.RED -> MaterialTheme.colorScheme.error
    ExamUrgency.ORANGE -> MaterialTheme.colorScheme.tertiary
    ExamUrgency.BLUE -> MaterialTheme.colorScheme.primary
}

/**
 * 单张考试卡：容器色 / 内容色 / 强调色均取自 colorScheme，按紧急程度分级，无硬编码。
 * v2.0 P1 增加教室（可选）展示。
 */
@Composable
private fun ExamCardItem(exam: ExamItem) {
    val (containerColor, contentColor, accentColor) = when (examUrgency(exam.daysLeft)) {
        ExamUrgency.RED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
        ExamUrgency.ORANGE -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiary
        )
        ExamUrgency.BLUE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primary
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Event,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = exam.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1
                )
                Text(
                    text = "考试日期 ${DateUtils.formatDateDash(exam.examDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f)
                )
                if (!exam.location.isNullOrBlank()) {
                    Text(
                        text = "教室 ${exam.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.75f)
                    )
                }
            }
            Text(
                text = daysLeftText(exam.daysLeft),
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
                textAlign = TextAlign.End
            )
        }
    }
}

/** 剩余天数文案：负数为「已结束」，0 天显示「今天考试」，其余显示「剩余 X 天」。 */
private fun daysLeftText(daysLeft: Int): String = when {
    daysLeft < 0 -> "已结束"
    daysLeft == 0 -> "今天考试"
    else -> "剩余 ${daysLeft} 天"
}

/** 空态提示：区分「搜索无结果」与「当前筛选下无考试」。 */
@Composable
private fun EmptyHintState(hasQuery: Boolean, filter: ExamFilter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (hasQuery) Icons.Outlined.Search else Icons.Outlined.Event,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = when {
                hasQuery -> "未找到相关考试"
                filter == ExamFilter.UPCOMING -> "没有未结束的考试 🎉"
                filter == ExamFilter.ENDED -> "还没有已结束的考试"
                else -> "暂无考试 🎉"
            },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasQuery) {
                "换个关键词试试吧"
            } else {
                "在课表里为课程设置考试日期后，这里会自动倒计时"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
