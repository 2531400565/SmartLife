package com.smartlife.app.ui.screen.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartlife.app.data.local.WeekType
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.ui.components.DateField
import com.smartlife.app.ui.components.TimeField

/** 星期选项：周一(1) ~ 周日(7)。 */
private val WEEKDAYS = listOf(
    "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4,
    "周五" to 5, "周六" to 6, "周日" to 7
)

/**
 * 新增 / 编辑课程对话框。
 * 含：课程名称（必填）、教室（可选）、任课老师（可选）、星期（多选）、
 * 周次（每周/单周/双周）、开始/结束时间、考试日期（可选）。
 *
 * 布局要点：内容区可垂直滚动，避免小屏或键盘弹出时底部内容被裁切；
 * 日期/时间字段采用统一的 disabled + 透明点击层方案，保证点击必然生效。
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
        weekdays: Set<Int>,
        startMinute: Int,
        endMinute: Int,
        examDate: Long?,
        weekType: WeekType
    ) -> Unit
) {
    var name by remember(editingCourse) { mutableStateOf(editingCourse?.name ?: "") }
    var location by remember(editingCourse) { mutableStateOf(editingCourse?.location ?: "") }
    var teacher by remember(editingCourse) { mutableStateOf(editingCourse?.teacher ?: "") }
    var weekdays by remember(editingCourse) { mutableStateOf(editingCourse?.weekdays ?: setOf(1)) }
    var startMinute by remember(editingCourse) { mutableStateOf(editingCourse?.startMinute ?: 8 * 60) }
    var endMinute by remember(editingCourse) { mutableStateOf(editingCourse?.endMinute ?: 9 * 60 + 35) }
    var examDate by remember(editingCourse) { mutableStateOf(editingCourse?.examDate) }
    var weekType by remember(editingCourse) { mutableStateOf(editingCourse?.weekType ?: WeekType.EVERY) }

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
                // 星期：多选（点击切换），至少选一个才能保存
                Text("星期", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = 4
                ) {
                    WEEKDAYS.forEach { (label, day) ->
                        FilterChip(
                            selected = day in weekdays,
                            onClick = {
                                weekdays = if (day in weekdays) weekdays - day else weekdays + day
                            },
                            label = { Text(label) }
                        )
                    }
                }
                if (weekdays.isEmpty()) {
                    Text(
                        text = "请至少选择一个星期",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // 周次（每周/单周/双周）
                Text("周次", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WeekType.entries.forEachIndexed { index, wt ->
                        SegmentedButton(
                            selected = weekType == wt,
                            onClick = { weekType = wt },
                            shape = SegmentedButtonDefaults.itemShape(index, WeekType.entries.size)
                        ) {
                            Text(wt.label)
                        }
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
                        weekdays,
                        startMinute,
                        endMinute,
                        examDate,
                        weekType
                    )
                },
                enabled = name.isNotBlank() && endMinute > startMinute && weekdays.isNotEmpty()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
