package com.smartlife.app.ui.screen.semester

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartlife.app.ui.components.DateField
import com.smartlife.app.util.DateUtils

/**
 * 学期设置页：
 * - 学期开始日期（DatePicker，可清除）
 * - 当前状态（未开学 / 第 X 周）
 * - 当前单双周（单周 / 双周）
 * 数据经 DataStore 持久化，不改动 Room 数据库结构。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterScreen(
    onBack: () -> Unit,
    viewModel: SemesterViewModel = viewModel(factory = SemesterViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学期设置") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "设置本学期开学日期后，课表将自动计算当前周数与单双周，并据此显示单双周课程。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DateField(
                label = "学期开始日期",
                timestamp = uiState.semesterStartDate,
                onDateChange = viewModel::setSemesterStartDate,
                emptyText = "未设置"
            )

            // 当前状态卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusRow(
                        label = "当前状态",
                        value = if (uiState.isNotStarted) "未开学" else "第 ${uiState.weekNumber} 周"
                    )
                    StatusRow(
                        label = "当前单双周",
                        value = uiState.weekType?.label ?: "—"
                    )
                }
            }

            // 未开学提示
            if (uiState.isNotStarted) {
                Text(
                    text = if (uiState.semesterStartDate == null) {
                        "尚未设置开学日期，请在上方选择。"
                    } else {
                        "学期将于 ${DateUtils.formatDateDash(uiState.semesterStartDate!!)} 开始。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 状态行：左侧标签 + 右侧值。 */
@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
