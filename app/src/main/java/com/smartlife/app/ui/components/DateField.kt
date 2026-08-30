package com.smartlife.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartlife.app.util.DateUtils

/**
 * 通用日期选择字段（Material3 DatePicker）。
 *
 * 全应用统一使用本组件，避免各处重复实现导致行为不一致（Todo 截止日期、Course 考试日期）。
 *
 * 实现要点：
 * - 内部的 OutlinedTextField 设为 **enabled = false**：这样它不会拦截指针事件，
 *   从而解决"readOnly 输入框外层 .clickable 被吞掉、日期选择器打不开"的问题。
 * - 同时通过 colors 把 disabled 态配色还原为正常态，视觉上与可编辑输入框一致。
 * - 上方覆盖一层无涟漪的透明点击层负责打开日期选择器；
 *   清除按钮单独叠在右侧，保证"清除"与"打开选择器"互不干扰。
 *
 * @param label 字段标题
 * @param timestamp 当前日期（本地时区毫秒；null 表示未设置）
 * @param emptyText 未设置时的占位文本
 * @param onDateChange 选择结果回调，null 表示清除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    timestamp: Long?,
    onDateChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "未设置"
) {
    var showPicker by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = timestamp?.let { DateUtils.dueDateText(it) } ?: emptyText,
            onValueChange = {},
            enabled = false,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            singleLine = true,
            // 还原 disabled 态配色，使其看起来与正常输入框一致（深色模式同样适配）
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 透明点击层：覆盖整个字段，点击打开日期选择器
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showPicker = true }
                )
        )

        // 清除按钮：仅在已设置日期时显示在右侧
        if (timestamp != null) {
            IconButton(
                onClick = { onDateChange(null) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = "清除$label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showPicker) {
        // DatePicker 使用 UTC 零点表示"某一天"，需与本地时间戳互转，避免日期偏移一天
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = timestamp?.let { DateUtils.toUtcStartOfDay(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // 未选择日期时保持原值不变
                    datePickerState.selectedDateMillis?.let {
                        onDateChange(DateUtils.fromUtcToLocalEndOfDay(it))
                    }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
