package com.smartlife.app.ui.screen.focus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 专注页：时长切换 + 圆形倒计时动画 + 开始/暂停/继续/结束 + 今日统计。
 * 计时由 ViewModel 的绝对时间戳驱动，退出页面不中断；结束提醒由 WorkManager 保证。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel = viewModel(factory = FocusViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 结果无需处理：未授权仅影响通知展示 */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "专注",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ===== 今日统计 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatMini(
                label = "今日专注",
                value = formatDuration(uiState.todaySeconds),
                modifier = Modifier.weight(1f)
            )
            StatMini(
                label = "完成轮数",
                value = "${uiState.todaySessions} 轮",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ===== 时长选择（仅空闲可切换）=====
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val durations = listOf(25, 45, 60)
            durations.forEachIndexed { index, minutes ->
                SegmentedButton(
                    selected = uiState.plannedMinutes == minutes,
                    onClick = { viewModel.selectDuration(minutes) },
                    enabled = uiState.phase == FocusPhase.IDLE,
                    shape = SegmentedButtonDefaults.itemShape(index, durations.size)
                ) {
                    Text("$minutes 分")
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // ===== 圆形倒计时 =====
        TimerCircle(
            remainingSeconds = uiState.remainingSeconds,
            totalSeconds = uiState.plannedMinutes * 60,
            phase = uiState.phase
        )

        Spacer(modifier = Modifier.height(36.dp))

        // ===== 控制按钮 =====
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (uiState.phase) {
                FocusPhase.IDLE, FocusPhase.FINISHED -> {
                    Button(onClick = {
                        requestNotificationPermission(context, permissionLauncher)
                        viewModel.start()
                    }) {
                        Text(if (uiState.phase == FocusPhase.FINISHED) "开始新一轮" else "开始专注")
                    }
                }
                FocusPhase.RUNNING -> {
                    Button(onClick = viewModel::pause) { Text("暂停") }
                    OutlinedButton(onClick = viewModel::finish) { Text("结束") }
                }
                FocusPhase.PAUSED -> {
                    Button(onClick = viewModel::resume) { Text("继续") }
                    OutlinedButton(onClick = viewModel::finish) { Text("结束") }
                }
            }
        }
    }
}

/** 圆形倒计时：底环 + 剩余进度弧（平滑动画）+ 中心 MM:SS 与状态文字。 */
@Composable
private fun TimerCircle(
    remainingSeconds: Int,
    totalSeconds: Int,
    phase: FocusPhase
) {
    val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 1f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
        label = "countdownProgress"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val arcColor = when (phase) {
        FocusPhase.RUNNING -> MaterialTheme.colorScheme.primary
        FocusPhase.PAUSED -> MaterialTheme.colorScheme.tertiary
        FocusPhase.FINISHED -> MaterialTheme.colorScheme.primary
        FocusPhase.IDLE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    }

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(250.dp)) {
            val strokeWidth = 14.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatTime(remainingSeconds),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = phaseText(phase),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 今日统计小卡片。 */
@Composable
private fun StatMini(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 秒数 → "X小时Y分" / "X分Y秒" / "X秒"。 */
private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}小时${m}分"
        m > 0 -> "${m}分${s}秒"
        else -> "${s}秒"
    }
}

/** 秒数 → "MM:SS"。 */
private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}

/** 阶段文案。 */
private fun phaseText(phase: FocusPhase): String = when (phase) {
    FocusPhase.RUNNING -> "专注中…"
    FocusPhase.PAUSED -> "已暂停"
    FocusPhase.FINISHED -> "本轮完成！"
    FocusPhase.IDLE -> "准备开始"
}

/** Android 13+ 首次开始时请求通知权限（仅影响通知展示，不影响计时）。 */
private fun requestNotificationPermission(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
