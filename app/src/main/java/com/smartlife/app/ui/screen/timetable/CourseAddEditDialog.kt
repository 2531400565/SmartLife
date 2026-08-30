package com.smartlife.app.ui.screen.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.ui.components.DateField
import com.smartlife.app.util.DateUtils

/** 星期选项：周一(1) ~ 周日(7)。 */
private val WEEKDAYS = listOf(
    "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4,
    "周五" to 5, "周六" to 6, "周日" to 7
)

/**
 * 新增 / 编辑课程对话框。
 * 含：课程名称（必填）、教室（可选）、任课老师（可选）、星期选择（FlowRow 两行，全部可见）、
 * 开始/结束时间（Material3 TimePicker）、考试日期（可选，与待办共用 DateField）。
 *
 * 布局要点：内容区可垂直滚动，避免小屏或键盘弹出时底部内容被裁切；
 * 输入框点击采用与 DateField 相同的 disabled + 透明点击层方案，保证点击必然生效。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingCourse == null) "新增课程" else "编辑课程") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                // 星期选择：FlowRow 每行 4 个 → 周一~周四 / 周五~周日 两行全部可见，不会被裁切
                Text("星期", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = 4
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
                // 考试日期（可选）：与待办截止日期共用同一个 DateField 组件
                DateField(
                    label = "考试日期（可选）",
                    timestamp = examDate,
                    onDateChange = { examDate = it },
                    emptyText = "未设置"
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
}

/**
 * 时间输入框：只读展示，点击弹出 Material3 TimePicker（24 小时制）。
 * 与 DateField 同样采用 enabled=false + 透明点击层，避免只读输入框吞掉点击事件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    minute: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = DateUtils.formatMinute(minute),
            onValueChange = {},
            enabled = false,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showPicker = true }
                )
        )
    }

    if (showPicker) {
        val timeState = rememberTimePickerState(
            initialHour = minute / 60,
            initialMinute = minute % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择$label") },
            text = { TimePicker(state = timeState) },
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
