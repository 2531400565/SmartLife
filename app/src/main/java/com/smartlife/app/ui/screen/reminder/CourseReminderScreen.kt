package com.smartlife.app.ui.screen.reminder

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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartlife.app.data.repository.SettingsRepository

/**
 * 课程提醒设置页（P1）：
 * - 总开关（关闭时取消全部已排程提醒）
 * - 提前时间：10 / 15 / 20 / 30 分钟
 *
 * 设置均经 DataStore 持久化，变更后立即重排 WorkManager 提醒。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseReminderScreen(
    onBack: () -> Unit,
    viewModel: CourseReminderViewModel = viewModel()
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val leadMinutes by viewModel.leadMinutes.collectAsStateWithLifecycle()
    val options = SettingsRepository.LEAD_MINUTE_OPTIONS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课程提醒") },
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
                text = "开启后，应用会在每节课开始前按设定时间发送本地通知；" +
                    "课程的新增、修改、删除会自动同步更新提醒，点击通知可直接跳转课表。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ===== 总开关 =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "课程提醒",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (enabled) "已开启" else "已关闭",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = viewModel::setEnabled)
                }
            }

            // ===== 提前时间 =====
            Text(
                text = "提前提醒时间",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, minutes ->
                            SegmentedButton(
                                selected = leadMinutes == minutes,
                                onClick = { viewModel.setLeadMinutes(minutes) },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                                enabled = enabled
                            ) {
                                Text("$minutes 分钟")
                            }
                        }
                    }
                    Text(
                        text = "在上课前 ${leadMinutes} 分钟发送提醒通知。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 关闭时的说明
            if (!enabled) {
                Text(
                    text = "提醒已关闭，不会发送任何课程通知。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
