@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.smartlife.app.ui.screen.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.ui.theme.AnimSpec
import com.smartlife.app.data.local.CourseType
import com.smartlife.app.data.local.WeekType
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.util.CoursePeriod
import com.smartlife.app.util.CoursePeriods
import com.smartlife.app.util.DEFAULT_COURSE_PERIODS
import com.smartlife.app.util.DateUtils
import kotlinx.coroutines.launch

/** 课程视觉色板（按课程 id 取色，稳定且不入库）。 */
private val COURSE_COLORS = listOf(
    Color(0xFF4F7FFF), Color(0xFF34C759), Color(0xFFFF9F0A), Color(0xFFFF3B30),
    Color(0xFFAF52DE), Color(0xFF00C7BE), Color(0xFFFF2D55), Color(0xFF5AC8FA)
)

/**
 * 课程表页：横向星期选择（默认今天）+ 当天课程列表 + 考试倒计时 + 增删改。
 * 数据全部来自 CourseRepository（Room 持久化）。
 */
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel = viewModel(factory = TimetableViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 视图模式（v1.3 P5）：列表 / 时间轴 / 周网格（v2.2）
    var viewMode by rememberSaveable { mutableStateOf(TimetableViewMode.LIST) }

    // 节次时刻表设置对话框（v2.2）
    var showPeriodSettings by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuredPeriods by SettingsRepository.coursePeriods(context)
        .collectAsStateWithLifecycle(initialValue = null)
    val coursePeriods = configuredPeriods ?: DEFAULT_COURSE_PERIODS

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showAddDialog) {
                Icon(Icons.Filled.Add, contentDescription = "新增课程")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // ===== 标题行：课表 + 节次时刻表设置（v2.2）=====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "课表",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showPeriodSettings = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "节次时刻表设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== 周信息区（未开学提示 / 周导航，v2.2 支持上一周·下一周·回本周）=====
            if (uiState.isNotStarted) {
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(
                        text = "未开学",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val startDate = uiState.semesterStartDate
                    Text(
                        text = if (startDate == null) {
                            "请在「我的 → 学期设置」设置开学日期"
                        } else {
                            "学期将于 ${DateUtils.formatDateDash(startDate)} 开始"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val week = uiState.weekNumber ?: 1
                val typeLabel = uiState.weekType?.label ?: ""
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = viewModel::previousWeek,
                        enabled = week > GRID_WEEK_MIN
                    ) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一周")
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "第${week}周 · $typeLabel",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val weekStart = uiState.weekStart
                        val weekEnd = uiState.weekEnd
                        if (weekStart != null && weekEnd != null) {
                            Text(
                                text = "${DateUtils.formatDateSlash(weekStart)} - " +
                                    DateUtils.formatDateSlash(weekEnd),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 非本周时显示「回到本周」
                        if (uiState.weekOffset != 0) {
                            TextButton(
                                onClick = viewModel::backToCurrentWeek,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("回到本周", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    IconButton(
                        onClick = viewModel::nextWeek,
                        enabled = week < GRID_WEEK_MAX
                    ) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "下一周")
                    }
                }
            }

            // ===== 横向星期选择栏（默认选中今天；周网格视图不显示）=====
            if (viewMode != TimetableViewMode.WEEK_GRID) {
                DaySelector(
                    selectedDay = uiState.selectedDay,
                    onSelect = viewModel::selectDay
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ===== 视图模式切换（v1.3 P5：列表 / 时间轴）=====
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TimetableViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewMode == mode,
                        onClick = { viewMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index, TimetableViewMode.entries.size)
                    ) {
                        Text(mode.label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 内容区 =====
            // 外层：视图模式切换淡入淡出（v1.3 P5，200ms）
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    fadeIn(tween(AnimSpec.ShortMs, easing = AnimSpec.standard)) togetherWith fadeOut(tween(AnimSpec.ShortMs, easing = AnimSpec.standard))
                },
                label = "viewModeSwitch"
            ) { mode ->
                when (mode) {
                    TimetableViewMode.LIST -> {
                        // 内层：列表模式，切换星期时左右滑动（v1.3 P2，250ms）
                        AnimatedContent(
                            targetState = uiState.selectedDay,
                            transitionSpec = {
                                // 星期数变大 → 新内容自右侧滑入；变小 → 自左侧滑入
                                val direction = if (targetState > initialState) 1 else -1
                                (slideInHorizontally(tween(AnimSpec.MediumMs, easing = AnimSpec.standard)) { it * direction } +
                                    fadeIn(tween(AnimSpec.MediumMs, easing = AnimSpec.standard))) togetherWith
                                    (slideOutHorizontally(tween(AnimSpec.MediumMs, easing = AnimSpec.standard)) { -it * direction } +
                                        fadeOut(tween(AnimSpec.MediumMs, easing = AnimSpec.standard)))
                            },
                            label = "daySwitchSlide"
                        ) { _ ->
                            when {
                                uiState.loading -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                                uiState.courses.isEmpty() -> EmptyDayState()
                                else -> LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 96.dp)
                                ) {
                                    items(uiState.courses, key = { it.id }) { course ->
                                        // 课程卡出现淡入（v1.3 P2，250ms）
                                        AnimatedVisibility(
                                            visible = true,
                                            enter = fadeIn(animationSpec = tween(durationMillis = AnimSpec.MediumMs, easing = AnimSpec.standard))
                                        ) {
                                            CourseCard(
                                                course = course,
                                                color = COURSE_COLORS[((course.id - 1) % COURSE_COLORS.size).toInt().coerceAtLeast(0)],
                                                onClick = { viewModel.showEditDialog(course) },
                                                onLongClick = { viewModel.requestDelete(course) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    TimetableViewMode.TIMELINE -> {
                        when {
                            uiState.loading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            uiState.courses.isEmpty() -> EmptyDayState()
                            else -> TimelineView(
                                courses = uiState.courses,
                                onCourseClick = viewModel::showEditDialog,
                                onCourseLongClick = viewModel::requestDelete
                            )
                        }
                    }
                    // ===== 周网格视图（v2.2）：教务式 星期 × 节次段 总览 =====
                    TimetableViewMode.WEEK_GRID -> {
                        WeekGridView(
                            courses = uiState.weekCourses,
                            weekStart = uiState.weekStart,
                            // 仅「展示本周」时高亮今天，避免跨周浏览时误标
                            today = if (uiState.weekOffset == 0) DateUtils.todayDayOfWeek() else null,
                            periods = coursePeriods,
                            onCourseClick = viewModel::showEditDialog,
                            onCourseLongClick = viewModel::requestDelete
                        )
                    }
                }
            }
        }
    }

    // ===== 新增 / 编辑对话框 =====
    if (uiState.showEditor) {
        CourseAddEditDialog(
            editingCourse = uiState.editingCourse,
            onDismiss = viewModel::dismissEditor,
            periods = coursePeriods,
            onSave = { name, location, teacher, weekdays, start, end, examDate, weekType, startWeek, endWeek, courseType ->
                viewModel.saveCourse(
                    name, location, teacher, weekdays, start, end, examDate,
                    weekType, startWeek, endWeek, courseType
                )
            }
        )
    }

    // ===== 节次时刻表设置对话框（v2.2）=====
    if (showPeriodSettings) {
        CoursePeriodSettingsDialog(
            initial = coursePeriods,
            onDismiss = { showPeriodSettings = false },
            onSave = { list ->
                scope.launch { SettingsRepository.setCoursePeriods(context, list) }
                showPeriodSettings = false
            }
        )
    }

    // ===== 删除确认对话框（长按触发）=====
    uiState.pendingDeleteCourse?.let { course ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("删除课程") },
            text = { Text("确定要删除「${course.name}」吗？") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("取消") }
            }
        )
    }
}

/** 横向星期选择栏：7 等分、不溢出；选中高亮，今天带小圆点。 */
@Composable
private fun DaySelector(
    selectedDay: Int,
    onSelect: (Int) -> Unit
) {
    val today = DateUtils.todayDayOfWeek()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (1..7).forEach { day ->
            val selected = selectedDay == day
            val isToday = day == today
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(day) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "周${"一二三四五六日"[day - 1]}",
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    // 今天标记小圆点
                    if (isToday) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

/** 课程卡片：左侧色条标识 + 名称/时间/教室 + 考试倒计时；点击编辑、长按删除。 */
@Composable
private fun CourseCard(
    course: CourseEntity,
    color: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val countdown = course.examDate?.let { DateUtils.examCountdownText(it) }
    val countdownColor = if (countdown == "今天考试") {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            // 左侧课程色条
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(color)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${DateUtils.formatMinute(course.startMinute)} - " +
                            DateUtils.formatMinute(course.endMinute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CourseTypeBadge(courseType = course.courseType)
                }
                // 单双周徽标（每周不显示）
                if (course.weekType != WeekType.EVERY) {
                    Text(
                        text = course.weekType.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (!course.location.isNullOrBlank()) {
                    Text(
                        text = "教室：${course.location}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!course.teacher.isNullOrBlank()) {
                    Text(
                        text = "老师：${course.teacher}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                countdown?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = countdownColor,
                        fontWeight = if (it == "今天考试") FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** 课表视图模式（v1.3 P5）：列表（默认）/ 时间轴；周网格（v2.2）。 */
private enum class TimetableViewMode(val label: String) {
    LIST("列表"),
    TIMELINE("时间轴"),
    WEEK_GRID("周网格")
}

/** 展示周范围（与课程 startWeek/endWeek 允许区间一致）。 */
private const val GRID_WEEK_MIN = 1
private const val GRID_WEEK_MAX = 30

/** 每天毫秒数。 */
private const val DAY_MILLIS = 86_400_000L

/**
 * 课程性质小 Badge（v2.1）：考试课 primaryContainer、考查课 secondaryContainer、未知不显示。
 * 使用 Material3 语义色，深浅色模式自动适配；只占名称行尾部，不改变现有卡片布局。
 */
@Composable
private fun CourseTypeBadge(courseType: CourseType) {
    val (container, content) = when (courseType) {
        CourseType.EXAM -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        CourseType.ASSESSMENT -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        CourseType.UNKNOWN -> return
    }
    Text(
        text = courseType.label,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * 时间轴视图（v1.3 P5，新增视图，不替换列表）：
 * - 左侧显示第 1~12 节刻度（时间轴总跨度均分 12 段，仅作定位参考）；
 * - 课程块按真实开始/结束分钟数绝对定位，高度 ∝ 时长（每分钟 1.5dp）；
 * - 同时段课程自动分道并排显示，不重叠；
 * - 点击课程编辑、长按删除。
 * 不修改课程数据结构，完全使用已有 startMinute / endMinute。
 */
@Composable
private fun TimelineView(
    courses: List<CourseEntity>,
    onCourseClick: (CourseEntity) -> Unit,
    onCourseLongClick: (CourseEntity) -> Unit
) {
    val sorted = courses.sortedBy { it.startMinute }
    if (sorted.isEmpty()) return

    val ppm = 1.5.dp                       // 每分钟像素密度
    val dayStart = (sorted.minOf { it.startMinute } / 60) * 60      // 向下取整到小时
    val dayEnd = ((sorted.maxOf { it.endMinute } + 59) / 60) * 60   // 向上取整到小时
    val totalMinutes = (dayEnd - dayStart).coerceAtLeast(60)
    val totalHeight = ppm * totalMinutes
    val lessonHeight = ppm * (totalMinutes / 12f)
    val lanes = assignLanes(sorted)
    val maxLanes = (lanes.values.maxOrNull() ?: 0) + 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .verticalScroll(rememberScrollState())
    ) {
        // ===== 左侧刻度：第 1~12 节 =====
        Column(
            modifier = Modifier
                .width(58.dp)
                .height(totalHeight)
        ) {
            (0 until 12).forEach { i ->
                val rowStart = dayStart + (i * totalMinutes / 12f).toInt()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lessonHeight)
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "第${i + 1}节",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = DateUtils.formatMinute(rowStart),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (i < 11) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // ===== 右侧画布：课程块按分钟绝对定位 =====
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(totalHeight)
        ) {
            // 节次分隔参考线（弱化）
            (0..12).forEach { i ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = lessonHeight * i)
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                )
            }

            val laneWidth = maxWidth / maxLanes
            sorted.forEach { course ->
                val lane = lanes[course.id] ?: 0
                val top = ppm * (course.startMinute - dayStart)
                val height = (ppm * (course.endMinute - course.startMinute)).coerceAtLeast(28.dp)
                TimelineCourseBlock(
                    course = course,
                    color = COURSE_COLORS[((course.id - 1) % COURSE_COLORS.size).toInt().coerceAtLeast(0)],
                    modifier = Modifier
                        .offset(x = laneWidth * lane + 2.dp, y = top)
                        .width((laneWidth - 4.dp).coerceAtLeast(40.dp))
                        .height(height),
                    onClick = { onCourseClick(course) },
                    onLongClick = { onCourseLongClick(course) }
                )
            }
        }
    }
}

/** 时间轴课程块：课程色点 + 名称 + 时间 + 教室，点击编辑、长按删除。 */
@Composable
private fun TimelineCourseBlock(
    course: CourseEntity,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = course.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${DateUtils.formatMinute(course.startMinute)} - " +
                DateUtils.formatMinute(course.endMinute),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        if (!course.location.isNullOrBlank()) {
            Text(
                text = course.location,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * 重叠课程分道：返回 课程id → 道号。
 * 按开始时间依次放入「最后结束时间 ≤ 当前开始」的最早空闲道，否则新开一道。
 */
private fun assignLanes(courses: List<CourseEntity>): Map<Long, Int> {
    val result = mutableMapOf<Long, Int>()
    val laneEnds = mutableListOf<Int>()
    for (c in courses.sortedBy { it.startMinute }) {
        var lane = laneEnds.indexOfFirst { it <= c.startMinute }
        if (lane == -1) {
            lane = laneEnds.size
            laneEnds.add(c.endMinute)
        } else {
            laneEnds[lane] = c.endMinute
        }
        result[c.id] = lane
    }
    return result
}

/**
 * 周网格视图（v2.2）：教务式「星期 × 节次段」整周总览。
 *
 * - 行 = 常用节次段（1-2 / 3-4 / 5-6 / 7-8 / 9-10，起止取自节次时刻表）；
 * - 课程按展示周过滤（周次范围 + 单双周，ViewModel 已处理），落格到包含其开始时间的节次段；
 * - 同段同天多门课垂直均分格子；点击编辑、长按删除（与列表行为一致）；
 * - 顶部行头显示星期与日期（来自展示周），仅"本周"高亮今天；
 * - 内容超高时可上下滚动。
 */
@Composable
private fun WeekGridView(
    courses: List<CourseEntity>,
    weekStart: Long?,
    today: Int?,
    periods: List<CoursePeriod>,
    onCourseClick: (CourseEntity) -> Unit,
    onCourseLongClick: (CourseEntity) -> Unit
) {
    val rowH = 92.dp
    val labelW = 46.dp
    // 可用节次段（时刻表能覆盖的段）
    val slots = CoursePeriods.DEFAULT_SLOTS.filter { (from, to) ->
        CoursePeriods.slotTime(from, to, periods) != null
    }
    // grid[星期-1][段下标]
    val grid = Array(7) { Array(slots.size) { mutableListOf<CourseEntity>() } }
    courses.forEach { course ->
        val slotIdx = CoursePeriods.slotIndexOf(course.startMinute, course.endMinute, periods)
        val col = if (slotIdx < slots.size) slotIdx else slots.lastIndex
        course.weekdays.filter { it in 1..7 }.forEach { day ->
            grid[day - 1][col].add(course)
        }
    }
    for (day in 0..6) {
        for (col in slots.indices) {
            grid[day][col].sortBy { it.startMinute }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // ===== 表头：星期 + 日期 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(modifier = Modifier.width(labelW))
            (1..7).forEach { day ->
                val isToday = today == day
                val dayStart = weekStart?.plus((day - 1) * DAY_MILLIS)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "周${"一二三四五六日"[day - 1]}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                    if (dayStart != null) {
                        Text(
                            text = formatMonthDay(dayStart),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        // ===== 节次段行 =====
        slots.forEachIndexed { col, (from, to) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // 时段列
                val slot = CoursePeriods.slotTime(from, to, periods)
                Column(
                    modifier = Modifier
                        .width(labelW)
                        .height(rowH),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = CoursePeriods.slotLabel(from, to),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (slot != null) {
                        Text(
                            text = CoursePeriods.formatMinute(slot.startMinute),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 星期格子
                (1..7).forEach { day ->
                    GridSlotCell(
                        courses = grid[day - 1][col],
                        isToday = today == day,
                        modifier = Modifier
                            .weight(1f)
                            .height(rowH),
                        onCourseClick = onCourseClick,
                        onCourseLongClick = onCourseLongClick
                    )
                }
            }
        }
    }
}

/** 周网格单个（天 × 段）格子：无课底色 / 有课按数量均分显示课程块。 */
@Composable
private fun GridSlotCell(
    courses: List<CourseEntity>,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onCourseClick: (CourseEntity) -> Unit,
    onCourseLongClick: (CourseEntity) -> Unit
) {
    val bg = when {
        courses.isNotEmpty() -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        courses.forEach { course ->
            val single = courses.size == 1
            val color = COURSE_COLORS[((course.id - 1) % COURSE_COLORS.size).toInt().coerceAtLeast(0)]
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(color.copy(alpha = if (single) 0.20f else 0.16f))
                    .combinedClickable(
                        onClick = { onCourseClick(course) },
                        onLongClick = { onCourseLongClick(course) }
                    )
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Text(
                    text = course.name,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (single) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (single) {
                    if (!course.location.isNullOrBlank()) {
                        Text(
                            text = course.location,
                            fontSize = 8.sp,
                            lineHeight = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${DateUtils.formatMinute(course.startMinute)}-" +
                            DateUtils.formatMinute(course.endMinute),
                        fontSize = 8.sp,
                        lineHeight = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 时间戳 → "M/d"（周网格表头日期）。 */
private fun formatMonthDay(ts: Long): String =
    java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault())
        .format(java.util.Date(ts))

/** 空状态：当天没有课程。 */
@Composable
private fun EmptyDayState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "今天没有课", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点按右下角 + 添加课程，好好享受空闲时光",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
