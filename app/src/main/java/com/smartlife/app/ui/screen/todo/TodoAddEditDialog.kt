package com.smartlife.app.ui.screen.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.smartlife.app.data.local.Priority
import com.smartlife.app.data.local.entity.TaskEntity
import com.smartlife.app.ui.components.DateField
import com.smartlife.app.ui.components.TimeField
import com.smartlife.app.util.DateUtils

/**
 * 新增 / 编辑任务对话框。
 * 含：标题（必填）、描述（可选）、优先级（低/中/高 分段选择）、
 * 截止时间（日期 DateField + 时间 TimeField，共同组成 dueDateTime）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoAddEditDialog(
    editingTask: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String?, priority: Priority, dueDateTime: Long?) -> Unit
) {
    var title by remember(editingTask) { mutableStateOf(editingTask?.title ?: "") }
    var description by remember(editingTask) { mutableStateOf(editingTask?.description ?: "") }
    var priority by remember(editingTask) { mutableStateOf(editingTask?.priority ?: Priority.MEDIUM) }
    var dueDateTime by remember(editingTask) { mutableStateOf(editingTask?.dueDateTime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingTask == null) "新增任务" else "编辑任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 标题
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    placeholder = { Text("要做什么？") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 描述
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                // 优先级
                Text("优先级", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Priority.entries.forEachIndexed { index, p ->
                        SegmentedButton(
                            selected = priority == p,
                            onClick = { priority = p },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = Priority.entries.size
                            )
                        ) {
                            Text(p.label)
                        }
                    }
                }
                // 截止时间（日期 + 时间）
                Text("截止时间", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateField(
                        label = "日期",
                        timestamp = dueDateTime,
                        onDateChange = { newDate ->
                            dueDateTime = if (newDate == null) {
                                null // 清除日期即清除整个截止时间
                            } else {
                                // 保留已选时间；若尚未选时间则默认 23:59（与旧版仅日期语义一致）
                                val time = dueDateTime?.let { DateUtils.minutesOfDay(it) } ?: (23 * 60 + 59)
                                DateUtils.combineDateAndTime(newDate, time)
                            }
                        },
                        emptyText = "选择日期",
                        modifier = Modifier.weight(1f)
                    )
                    TimeField(
                        label = "时间",
                        minute = dueDateTime?.let { DateUtils.minutesOfDay(it) } ?: (23 * 60 + 59),
                        onSelected = { minutes ->
                            // 未选日期时先按今天处理
                            val date = dueDateTime ?: System.currentTimeMillis()
                            dueDateTime = DateUtils.combineDateAndTime(date, minutes)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title.trim(), description.trim().ifBlank { null }, priority, dueDateTime)
                },
                enabled = title.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
