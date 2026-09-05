package com.smartlife.app.ui.screen.profile

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartlife.app.ui.theme.ThemeMode
import com.smartlife.app.util.DateUtils
import kotlinx.coroutines.launch

/**
 * "我的"页。
 *
 * 结构（v1.2 UI 优化后，数据管理类入口收进折叠分组，页面更简洁）：
 * - 顶部：深色模式 / 学期设置 / 当前学期 / 专注统计 / 课程提醒
 * - 折叠分组：数据统计、数据管理（导入导出）、关于
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateSemester: () -> Unit,
    onNavigateSemesterCourses: () -> Unit,
    onNavigateCourseReminder: () -> Unit,
    onNavigateExamList: () -> Unit,
    onNavigateAnalytics: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val versionName = rememberVersionName()

    // 折叠分组的展开状态（默认收起）
    var statsExpanded by remember { mutableStateOf(false) }
    var dataExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    // 系统文件选择器（导入 JSON）
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                scope.launch { snackbarHostState.showSnackbar("读取文件失败或文件为空") }
            } else {
                viewModel.importJson(text) { ok, msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            }
        }
    }

    // 系统文件选择器（导入 CSV，P3；统一按 UTF-8 读取）
    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                scope.launch { snackbarHostState.showSnackbar("读取文件失败或文件为空") }
            } else {
                viewModel.importCsv(text) { _, msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            }
        }
    }

    // 系统分享（导出 JSON，不写死路径）
    fun shareJson(json: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "SmartLife 数据备份")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        context.startActivity(Intent.createChooser(sendIntent, "导出 SmartLife 数据"))
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "我的", style = MaterialTheme.typography.headlineSmall)

            // ===== 头部 =====
            HeaderCard(versionName = versionName)

            // ===== 深色模式 =====
            SectionTitle("外观")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "主题模式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }

            // ===== 学期设置 + 当前学期 =====
            SectionTitle("学期")
            EntryCard(
                title = "学期设置",
                subtitle = "设置开学日期，自动计算周数与单双周",
                icon = Icons.Outlined.CalendarMonth,
                onClick = onNavigateSemester
            )
            if (!uiState.loading) {
                SemesterSummaryCard(
                    count = uiState.totalCourses,
                    onClick = onNavigateSemesterCourses
                )
            }

            // ===== 考试中心（v2.0 P1）=====
            SectionTitle("考试")
            EntryCard(
                title = "考试中心",
                subtitle = "查看全部考试倒计时与考试统计",
                icon = Icons.Outlined.Event,
                onClick = onNavigateExamList
            )

            // ===== 专注统计（P4）=====
            if (!uiState.loading) {
                SectionTitle("专注统计")
                FocusStatsCard(
                    today = uiState.focusToday,
                    week = uiState.focusWeek,
                    month = uiState.focusMonth
                )
                WeekFocusChartCard(stats = uiState.focusWeekDays)
            }

            // ===== 数据分析（v2.0 P3）=====
            SectionTitle("分析")
            EntryCard(
                title = "数据分析",
                subtitle = "专注趋势 · 待办效率 · 课程分布",
                icon = Icons.Outlined.Insights,
                onClick = onNavigateAnalytics
            )

            // ===== 课程提醒（P1）=====
            SectionTitle("提醒")
            EntryCard(
                title = "课程提醒",
                subtitle = "上课前自动提醒，可设置提前时间",
                icon = Icons.Outlined.Notifications,
                onClick = onNavigateCourseReminder
            )

            // ===== 数据统计（折叠，默认收起）=====
            CollapsibleGroup(
                title = "数据统计",
                expanded = statsExpanded,
                onToggle = { statsExpanded = !statsExpanded }
            ) {
                if (uiState.loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("总待办", uiState.totalTasks.toString(), Modifier.weight(1f))
                        StatCard("已完成", uiState.completedTasks.toString(), Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            "总专注时长",
                            formatDuration(uiState.totalFocusSeconds),
                            Modifier.weight(1f)
                        )
                        StatCard("完成轮数", "${uiState.completedFocusSessions} 轮", Modifier.weight(1f))
                    }
                }
            }

            // ===== 数据管理（折叠，默认收起）=====
            CollapsibleGroup(
                title = "数据管理",
                expanded = dataExpanded,
                onToggle = { dataExpanded = !dataExpanded }
            ) {
                Text(
                    text = "将任务 / 课程 / 专注记录 / 励志语导出为 JSON，或从 JSON 备份恢复；" +
                        "也可按 CSV 批量导入课程（追加，不覆盖已有课程）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val json = runCatching { viewModel.buildExportJson() }.getOrNull()
                                if (json != null) shareJson(json)
                                else snackbarHostState.showSnackbar("导出失败，请重试")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("导出 JSON")
                    }
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("导入 JSON")
                    }
                }

                // CSV 导入课程（P3 / v2.2 增强：支持周次范围、课程性质、老师）
                OutlinedButton(
                    onClick = {
                        csvImportLauncher.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("导入 CSV（课程）")
                }
                Text(
                    text = "CSV 完整版（推荐）：课程名,教室,老师,星期,周次,周次范围,课程性质,开始时间,结束时间,考试日期\n" +
                        "例：软件体系结构,3-505,周老师,1,每周,1-16,考试课,08:00,09:35,2026-12-20\n" +
                        "星期多值用 |（如 1|3）；周次为 每周/单周/双周；范围如 1-16；性质为 考试课/考查课。\n" +
                        "简版（兼容旧文件）：课程名,教室,星期,周次,开始时间,结束时间,考试日期",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ===== 关于（折叠，默认收起）=====
            CollapsibleGroup(
                title = "关于",
                expanded = aboutExpanded,
                onToggle = { aboutExpanded = !aboutExpanded }
            ) {
                Text(
                    text = "SmartLife 是一款为大学生设计的本地生活助手：待办管理、番茄专注、课程表与考试倒计时，全部数据离线存储在你的设备上。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "当前版本 v$versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 可折叠分组：标题行点击展开 / 收起，内容区仅在展开时渲染。
 * 用于收纳「数据统计 / 数据管理 / 关于」等低频入口，保持首页简洁。
 */
@Composable
private fun CollapsibleGroup(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/** 通用入口卡片：图标 + 标题 + 副标题 + 右侧 Chevron，整卡可点击。 */
@Composable
private fun EntryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 关于头部：头像圆形 + App 名 + 版本。 */
@Composable
private fun HeaderCard(versionName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
            Column {
                Text(
                    text = "SmartLife",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "大学生活助手 · v$versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** 区块小标题。 */
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

/** 统计卡片：数值 + 标签。 */
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 当前学期统计卡：标题 + 大数字 + 右侧 Chevron，整卡可点击（Material Ripple）。 */
@Composable
private fun SemesterSummaryCard(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前学期",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$count 门课程",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 专注统计卡片（P4）：今日 / 本周 / 本月。
 *
 * 展示三个维度的总时长（柱状图）、完成次数与平均时长；
 * 颜色全部取自 MaterialTheme，深浅色模式自动适配。
 * 数据来自 FocusSession，不新增任何数据库字段。
 */
@Composable
private fun FocusStatsCard(
    today: FocusStats,
    week: FocusStats,
    month: FocusStats
) {
    val stats = listOf(today, week, month)
    val labels = listOf("今日", "本周", "本月")
    val trackHeight = 96.dp
    // 全部为 0 时给一个最小基准，避免除零导致柱高为 NaN
    val maxSeconds = stats.maxOf { it.totalSeconds }.coerceAtLeast(60L)

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
            // ===== 总时长柱状图 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                stats.forEachIndexed { index, stat ->
                    val fraction = (stat.totalSeconds.toFloat() / maxSeconds.toFloat())
                        .coerceIn(0f, 1f)
                    val barHeight = (96f * fraction).coerceAtLeast(4f).dp
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatDuration(stat.totalSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        // 固定高度轨道，柱体自底部向上生长
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
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Text(
                            text = labels[index],
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            // ===== 完成次数 / 平均时长 =====
            MetricRow(
                label = "完成次数",
                values = stats.map { "${it.completedCount} 轮" }
            )
            MetricRow(
                label = "平均时长",
                values = stats.map { formatDuration(it.averageSeconds) }
            )
        }
    }
}

/** 统计明细行：上方指标名，下方三个周期的数值（与柱状图列宽对齐）。 */
@Composable
private fun MetricRow(label: String, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            values.forEach { value ->
                Text(
                    text = value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 本周专注柱状图卡片（P4）：周一~周日逐日专注分钟数。
 *
 * - 柱高按当日分钟数等比缩放（全为 0 时给最小基准，避免除零）；
 * - 今日的柱子与星期标签用 tertiary（强调色）高亮；
 * - 底部展示本周总时长 / 日均专注 / 最长专注日。
 * 颜色全部取自 MaterialTheme，深浅色模式自动适配。
 */
@Composable
private fun WeekFocusChartCard(stats: WeekFocusStats) {
    val weekShort = listOf("一", "二", "三", "四", "五", "六", "日")
    val trackHeight = 88.dp
    val maxMinutes = stats.minutes.max().coerceAtLeast(1L)   // 全 0 时给 1 防除零
    val todayIdx = DateUtils.todayDayOfWeek() - 1             // 0=周一

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
            Text(
                text = "本周专注（周一 ~ 周日）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ===== 周一~周日柱状图 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stats.minutes.forEachIndexed { index, minutes ->
                    val fraction = (minutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
                    val barHeight = (88f * fraction).coerceAtLeast(if (minutes > 0) 4f else 0f).dp
                    val isToday = index == todayIdx
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (minutes > 0) "$minutes" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        // 固定高度轨道，柱体自底部向上生长
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
                                        if (isToday) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                        }
                        Text(
                            text = weekShort[index],
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // ===== 本周总时长 / 日均专注 / 最长专注日 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WeekMiniStat("本周总时长", formatDuration(stats.totalSeconds), Modifier.weight(1f))
                WeekMiniStat("日均专注", formatDuration(stats.avgSeconds), Modifier.weight(1f))
                WeekMiniStat("最长专注日", stats.bestDayLabel ?: "—", Modifier.weight(1f))
            }
        }
    }
}

/** 周统计小指标：数值 + 标签（与柱状图列同宽，居中）。 */
@Composable
private fun WeekMiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 秒数 → "X小时Y分" / "X分Y秒" / "X秒"。 */
private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}小时${m}分"
        m > 0 -> "${m}分${s}秒"
        else -> "${s}秒"
    }
}

/** 读取应用版本号（PackageManager，避免依赖 BuildConfig 开关）。 */
@Composable
private fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")
    }
}
