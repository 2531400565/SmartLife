package com.smartlife.app.ui.screen.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.util.DateUtils

/** 星期选项：周一(1) ~ 周日(7)。 */
private val WEEKDAYS = listOf(
    "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4,
    "周五" to 5, "周六" to 6, "周日" to 7
)

/**
 * 新增 / 编辑课程对话框。
 * 含：课程名称（必填）、教室（可选）、任课老师（可选）、星期选择、
 * 开始/结束时间（Material3 TimePicker）、考试日期（可选）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseAddEditDialog(
    editingCourse: CourseEntity?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        location: String?,
        teacher: String?,
        dayOfWeek: Int,
        startMinute: Int,
        endMinute: Int,
        examDate: Long?
    ) -> Unit
) {
    var name by remember(editingCourse) { mutableStateOf(editingCourse?.name ?: "") }
    var location by remember(editingCourse) { mutableStateOf(editingCourse?.location ?: "") }
    var teacher by remember(editingCourse) { mutableStateOf(editingCourse?.teacher ?: "") }
    var dayOfWeek by remember(editingCourse) { mutableStateOf(editingCourse?.dayOfWeek ?: 1) }
    var startMinute by remember(editingCourse) { mutableStateOf(editingCourse?.startMinute ?: 8 * 60) }
    var endMinute by remember(editingCourse) { mutableStateOf(editingCourse?.endMinute ?: 9 * 60 + 35) }
    var examDate by remember(editingCourse) { mutableStateOf(editingCourse?.examDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingCourse == null) "新增课程" else "编辑课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 课程名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称") },
                    placeholder = { Text("如：高等数学") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 教室
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("教室（可选）") },
                    placeholder = { Text("如：A栋 301") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 任课老师
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("任课老师（可选）") },
                    placeholder = { Text("如：张老师") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 星期选择
                Text("星期", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WEEKDAYS.forEach { (label, day) ->
                        FilterChip(
                            selected = dayOfWeek == day,
                            onClick = { dayOfWeek = day },
                            label = { Text(label) }
                        )
                    }
                }
                // 开始 / 结束时间
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeField(
                        label = "开始时间",
                        minute = startMinute,
                        onSelected = { startMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                    TimeField(
                        label = "结束时间",
                        minute = endMinute,
                        onSelected = { endMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (endMinute <= startMinute) {
                    Text(
                        text = "结束时间需晚于开始时间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // 考试日期（可选）
                OutlinedTextField(
                    value = examDate?.let { DateUtils.dueDateText(it) } ?: "未设置",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("考试日期（可选）") },
                    trailingIcon = {
                        if (examDate != null) {
                            IconButton(onClick = { examDate = null }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "清除考试日期")
                            }
                        } else {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        location.trim().ifBlank { null },
                        teacher.trim().ifBlank { null },
                        dayOfWeek,
                        startMinute,
                        endMinute,
                        examDate
                    )
                },
                enabled = name.isNotBlank() && endMinute > startMinute
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    // 考试日期选择器
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = examDate?.let { DateUtils.toUtcStartOfDay(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        examDate = DateUtils.fromUtcToLocalEndOfDay(it)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** 时间输入框：只读展示，点击弹出 Material3 TimePicker（24 小时制）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    minute: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = DateUtils.formatMinute(minute),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            Icon(Icons.Outlined.Schedule, contentDescription = null)
        },
        modifier = modifier.clickable { showPicker = true }
    )

    if (showPicker) {
        val timeState = rememberTimePickerState(
            initialHour = minute / 60,
            initialMinute = minute % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择$label") },
            text = {
                TimePicker(state = timeState)
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelected(timeState.hour * 60 + timeState.minute)
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            }
        )
    }
}
