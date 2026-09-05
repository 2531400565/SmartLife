package com.smartlife.app.ui.screen.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartlife.app.ui.components.TimeField
import com.smartlife.app.util.CoursePeriod
import com.smartlife.app.util.DEFAULT_COURSE_PERIODS

/**
 * 节次时刻表设置对话框（v2.2）：
 * 逐节编辑第 1~N 节的上课起止时间，底部可一键恢复默认。
 *
 * 说明文案明确：该时刻表仅用于「新增/编辑课程」的节次快捷填充与 CSV 导入换算，
 * 不会改写已保存课程的时间。
 */
@Composable
fun CoursePeriodSettingsDialog(
    initial: List<CoursePeriod>,
    onDismiss: () -> Unit,
    onSave: (List<CoursePeriod>) -> Unit
) {
    val periods = remember(initial) { initial.toMutableStateList() }
    val valid = periods.all { it.startMinute < it.endMinute }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节次时刻表") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "用于「第1-2节」快捷填充与 CSV 导入换算。" +
                        "修改后仅影响之后录入的课程，不会改写已保存课程的时间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 表头
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "节次",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "开始",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "结束",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                periods.forEachIndexed { index, period ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = "第${index + 1}节",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TimeField(
                            label = "",
                            minute = period.startMinute,
                            onSelected = { minutes ->
                                periods[index] = period.copy(startMinute = minutes)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TimeField(
                            label = "",
                            minute = period.endMinute,
                            onSelected = { minutes ->
                                periods[index] = period.copy(endMinute = minutes)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (index < periods.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
                if (!valid) {
                    Text(
                        text = "存在开始晚于结束的节次，请检查",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(periods.toList()) },
                enabled = valid
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        periods.clear()
                        periods.addAll(DEFAULT_COURSE_PERIODS)
                    }
                ) {
                    Text("恢复默认")
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
