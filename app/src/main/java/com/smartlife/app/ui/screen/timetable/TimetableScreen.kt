package com.smartlife.app.ui.screen.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.util.DateUtils

/** 课程视觉色板（按课程 id 取色，稳定且不入库）。 */
private val COURSE_COLORS = listOf(
    Color(0xFF4F7FFF), Color(0xFF34C759), Color(0xFFFF9F0A), Color(0xFFFF3B30),
    Color(0xFFAF52DE), Color(0xFF00C7BE), Color(0xFFFF2D55), Color(0xFF5AC8FA)
)

/**
 * 课程表页：横向星期选择（默认今天）+ 当天课程列表 + 考试倒计时 + 增删改。
 * 数据全部来自 CourseRepository（Room 持久化）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel = viewModel(factory = TimetableViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            // ===== 标题 =====
            Text(
                text = "课表",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
            )

            // ===== 横向星期选择栏（默认选中今天）=====
            DaySelector(
                selectedDay = uiState.selectedDay,
                onSelect = viewModel::selectDay
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 内容区 =====
            when {
                uiState.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.courses.isEmpty() -> EmptyDayState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.courses, key = { it.id }) { course ->
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

    // ===== 新增 / 编辑对话框 =====
    if (uiState.showEditor) {
        CourseAddEditDialog(
            editingCourse = uiState.editingCourse,
            onDismiss = viewModel::dismissEditor,
            onSave = { name, location, teacher, day, start, end, examDate ->
                viewModel.saveCourse(name, location, teacher, day, start, end, examDate)
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
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "今天没有课", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "点按右下角 + 添加课程，好好享受空闲时光",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
