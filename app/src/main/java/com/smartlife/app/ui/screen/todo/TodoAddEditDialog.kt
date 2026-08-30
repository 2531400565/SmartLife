package com.smartlife.app.ui.screen.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/**
 * 新增 / 编辑任务对话框。
 * 包含：标题（必填）、描述（可选）、优先级（低/中/高 分段选择）、截止日期（统一日期组件）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoAddEditDialog(
    editingTask: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String?, priority: Priority, dueDate: Long?) -> Unit
) {
    var title by remember(editingTask) { mutableStateOf(editingTask?.title ?: "") }
    var description by remember(editingTask) { mutableStateOf(editingTask?.description ?: "") }
    var priority by remember(editingTask) { mutableStateOf(editingTask?.priority ?: Priority.MEDIUM) }
    var dueDate by remember(editingTask) { mutableStateOf(editingTask?.dueDate) }

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
                // 截止日期：与应用内其他日期字段共用 DateField（点击打开 DatePicker，可清除）
                DateField(
                    label = "截止日期",
                    timestamp = dueDate,
                    onDateChange = { dueDate = it },
                    emptyText = "未设置截止日期"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title.trim(), description.trim().ifBlank { null }, priority, dueDate)
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
