@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.smartlife.app.ui.screen.semester

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.ui.screen.timetable.CourseAddEditDialog
import com.smartlife.app.util.DateUtils

/** 星期名称（1=周一 ... 7=周日）。 */
private val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 星期集合 → "周一、周三"（升序）。 */
private fun weekdaysText(weekdays: Set<Int>): String =
    weekdays.sorted().joinToString("、") { WEEKDAY_NAMES.getOrElse(it - 1) { "" } }

/**
 * 当前学期课程页：
 * 按开始时间排序列出全部课程，卡片显示名称/星期/周次/时间/教室；
 * 点击复用 CourseAddEditDialog 编辑，长按弹出删除确认。
 */
@Composable
fun SemesterCoursesScreen(
    onBack: () -> Unit,
    viewModel: SemesterCoursesViewModel = viewModel(factory = SemesterCoursesViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("当前学期课程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // ===== 副标题：共 X 门课程 =====
            Text(
                text = "共 ${uiState.courses.size} 门课程",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )

            // ===== 列表 =====
            when {
                uiState.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.courses.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(uiState.courses, key = { it.id }) { course ->
                        CourseListItem(
                            course = course,
                            onClick = { viewModel.showEditDialog(course) },
                            onLongClick = { viewModel.requestDelete(course) }
                        )
                    }
                }
            }
        }
    }

    // ===== 编辑对话框（复用 CourseAddEditDialog）=====
    if (uiState.showEditor) {
        CourseAddEditDialog(
            editingCourse = uiState.editingCourse,
            onDismiss = viewModel::dismissEditor,
            onSave = { name, location, teacher, weekdays, start, end, examDate, weekType, startWeek, endWeek, courseType ->
                viewModel.saveCourse(
                    name, location, teacher, weekdays, start, end, examDate,
                    weekType, startWeek, endWeek, courseType
                )
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

/** 课程条目卡片：名称 + 时间 + 星期 + 周次 + 教室；点击编辑、长按删除。 */
@Composable
private fun CourseListItem(
    course: CourseEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${DateUtils.formatMinute(course.startMinute)} - " +
                        DateUtils.formatMinute(course.endMinute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 星期 + 周次（每周 / 单周 / 双周）
            Text(
                text = "${weekdaysText(course.weekdays)} · ${course.weekType.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 教室（可选）
            if (!course.location.isNullOrBlank()) {
                Text(
                    text = "教室：${course.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 空状态。 */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.School,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 12.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(text = "当前学期还没有课程", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "去课表添加课程吧",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
