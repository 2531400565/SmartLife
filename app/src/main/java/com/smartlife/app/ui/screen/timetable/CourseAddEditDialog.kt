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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartlife.app.data.local.CourseType
import com.smartlife.app.data.local.WeekType
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.ui.components.DateField
import com.smartlife.app.ui.components.TimeField

/** 星期选项：周一(1) ~ 周日(7)。 */
private val WEEKDAYS = listOf(
    "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4,
    "周五" to 5, "周六" to 6, "周日" to 7
)

/** 周次范围：允许输入 1~30（覆盖绝大多数学期）。 */
private const val WEEK_MIN = 1
private const val WEEK_MAX = 30

/**
 * 新增 / 编辑课程对话框。
 * 含：课程名称（必填）、教室（可选）、任课老师（可选）、星期（多选）、
 * 周次（每周/单周/双周）、周次范围（起始周/结束周）、课程性质（考试课/考查课/未知）、
 * 开始/结束时间、考试日期（可选）。
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
        weekType: WeekType,
        startWeek: Int,
        endWeek: Int,
        courseType: CourseType
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
    var startWeek by remember(editingCourse) { mutableIntStateOf(editingCourse?.startWeek ?: 1) }
    var endWeek by remember(editingCourse) { mutableIntStateOf(editingCourse?.endWeek ?: 16) }
    var courseType by remember(editingCourse) { mutableStateOf(editingCourse?.courseType ?: CourseType.UNKNOWN) }

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
                // 周次范围：起始周 / 结束周（1~30，默认 1~16，start ≤ end）
                Text("周次范围", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeekNumberField(
                        label = "起始周",
                        value = startWeek,
                        onValueChange = { startWeek = it },
                        modifier = Modifier.weight(1f)
                    )
                    WeekNumberField(
                        label = "结束周",
                        value = endWeek,
                        onValueChange = { endWeek = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (startWeek > endWeek) {
                    Text(
                        text = "起始周不能晚于结束周",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // 课程性质：单选（考试课 / 考查课 / 未知）
                Text("课程性质", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CourseType.entries.forEachIndexed { index, ct ->
                        SegmentedButton(
                            selected = courseType == ct,
                            onClick = { courseType = ct },
                            shape = SegmentedButtonDefaults.itemShape(index, CourseType.entries.size)
                        ) {
                            Text(ct.label)
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
                        weekType,
                        startWeek,
                        endWeek,
                        courseType
                    )
                },
                enabled = name.isNotBlank() && endMinute > startMinute &&
                    weekdays.isNotEmpty() && startWeek <= endWeek
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 周次数字输入框：1~30 整数，非数字/越界输入自动修正。 */
@Composable
private fun WeekNumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(2)
            text = digits
            val parsed = digits.toIntOrNull()
            if (parsed != null) {
                onValueChange(parsed.coerceIn(WEEK_MIN, WEEK_MAX))
            } else if (digits.isEmpty()) {
                onValueChange(WEEK_MIN)
            }
        },
        label = { Text(label) },
        singleLine = true,
        isError = value < WEEK_MIN || value > WEEK_MAX,
        modifier = modifier
    )
}
