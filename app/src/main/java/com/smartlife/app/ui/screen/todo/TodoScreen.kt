@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.smartlife.app.ui.screen.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartlife.app.data.local.Priority
import com.smartlife.app.data.local.entity.TaskEntity
import com.smartlife.app.ui.theme.AnimSpec
import com.smartlife.app.ui.theme.Success
import com.smartlife.app.ui.theme.Warning
import com.smartlife.app.util.DateUtils

/**
 * 待办页：任务列表（未完成置顶、按截止时间排序）+ 搜索 + 新增/编辑/删除 + 完成勾选。
 */
@Composable
fun TodoScreen(
    viewModel: TodoViewModel = viewModel(factory = TodoViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 删除动画（v1.3 P2）：先把待删项标记为「离场」并播放缩放消失，动画结束后再执行真正的删除。
    // 只影响表现层时序，删除动作本身仍走 ViewModel.confirmDelete()，不改动任何业务逻辑。
    var deletingId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(deletingId) {
        if (deletingId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(220)   // 略长于 200ms 的离场动画，保证动画完整播放
        viewModel.confirmDelete()
        deletingId = null
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showAddDialog) {
                Icon(Icons.Filled.Add, contentDescription = "新增任务")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // ===== 标题 =====
            Text(
                text = "待办",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 20.dp, bottom = 14.dp)
            )

            // ===== 搜索框（实时过滤）=====
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索任务") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "清除搜索")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 内容区 =====
            when {
                uiState.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.tasks.isEmpty() -> {
                    // 空状态淡入（v1.3 P2，300ms）
                    var emptyVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { emptyVisible = true }
                    AnimatedVisibility(
                        visible = emptyVisible,
                        enter = fadeIn(animationSpec = tween(durationMillis = AnimSpec.LongMs))
                    ) {
                        EmptyState()
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(uiState.tasks, key = { it.id }) { task ->
                        // animateItem：列表重排（勾选后下移到已完成区）平滑过渡
                        // AnimatedVisibility：删除时缩放消失（200ms）
                        AnimatedVisibility(
                            visible = deletingId != task.id,
                            modifier = Modifier.animateItem(
                                placementSpec = tween(durationMillis = AnimSpec.MediumMs)
                            ),
                            exit = fadeOut(animationSpec = tween(durationMillis = AnimSpec.ShortMs)) +
                                scaleOut(targetScale = 0.7f, animationSpec = tween(durationMillis = AnimSpec.ShortMs))
                        ) {
                            TaskItem(
                                task = task,
                                onToggle = { viewModel.toggleComplete(task) },
                                onClick = { viewModel.showEditDialog(task) },
                                onLongClick = { viewModel.requestDelete(task) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ===== 新增 / 编辑对话框 =====
    if (uiState.showEditor) {
        TodoAddEditDialog(
            editingTask = uiState.editingTask,
            onDismiss = viewModel::dismissEditor,
            onSave = { title, description, priority, dueDateTime ->
                viewModel.saveTask(title, description, priority, dueDateTime)
            }
        )
    }

    // ===== 删除确认对话框（长按触发）=====
    uiState.pendingDeleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("删除任务") },
            text = { Text("确定要删除「${task.title}」吗？此操作不可撤销。") },
            confirmButton = {
                // 先触发缩放消失动画，由 LaunchedEffect 在动画结束后调用 confirmDelete()
                TextButton(onClick = { deletingId = task.id }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("取消") }
            }
        )
    }
}

/** 单条任务：勾选框 + 标题/描述 + 优先级/截止日期；点击编辑、长按删除。 */
@Composable
private fun TaskItem(
    task: TaskEntity,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 未完成 + 截止时刻早于当前 → 逾期（精确到时分秒，判定统一收在 DateUtils）
    val isOverdue = DateUtils.isOverdue(task.dueDateTime, task.isCompleted)

    // 勾选完成后「淡出」（v1.3 P2，250ms）：已完成任务降低不透明度，
    // 配合 LazyColumn 的 animateItem 形成「淡出 + 下移」的观感。
    val itemAlpha by animateFloatAsState(
        targetValue = if (task.isCompleted) 0.6f else 1f,
        animationSpec = tween(durationMillis = AnimSpec.MediumMs),
        label = "taskAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = itemAlpha },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                )
                if (!task.description.isNullOrBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriorityLabel(task.priority)
                    task.dueDateTime?.let { ts ->
                        if (isOverdue) {
                            // 逾期：显示 "已逾期 X小时"
                            OverdueChip(DateUtils.overdueDurationText(ts))
                        } else {
                            // 正常：显示 "今天 18:30" / "明天 09:00" / "9月3日 14:20"
                            Text(
                                text = DateUtils.dueDateTimeText(ts),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 优先级：彩色圆点 + 文字（高=红 / 中=橙 / 低=绿）。 */
@Composable
private fun PriorityLabel(priority: Priority) {
    val (color, label) = when (priority) {
        Priority.HIGH -> MaterialTheme.colorScheme.error to "高"
        Priority.MEDIUM -> Warning to "中"
        Priority.LOW -> Success to "低"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * 逾期标识：使用 errorContainer / onErrorContainer 配对，
 * 浅色与深色模式下均由 Material3 保证文字与底色对比度，不会看不清。
 */
@Composable
private fun OverdueChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** 空状态：无任务（或搜索无结果）时的插画式提示。 */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Checklist,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "暂无待办任务", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击右下角 + 新建你的第一个任务",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
