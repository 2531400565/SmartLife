package com.smartlife.app.ui.screen.todo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartlife.app.data.local.Priority
import com.smartlife.app.data.local.entity.TaskEntity
import com.smartlife.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 待办页 UI 状态。
 */
data class TodoUiState(
    val tasks: List<TaskEntity> = emptyList(),   // 当前显示的任务（已按搜索关键字过滤）
    val searchQuery: String = "",                 // 搜索关键字
    val showEditor: Boolean = false,              // 是否显示 新增/编辑 对话框
    val editingTask: TaskEntity? = null,          // 编辑中的任务（null 表示新增）
    val pendingDeleteTask: TaskEntity? = null,    // 待删除确认的任务（长按触发）
    val loading: Boolean = true                   // 首次加载中
)

/**
 * 待办 ViewModel：TaskRepository 数据 + 搜索过滤 + 增删改查操作。
 * 数据流：tasks 表变更 → Repository Flow → combine(搜索词) → StateFlow。
 */
class TodoViewModel(application: Application) : AndroidViewModel(application) {

    private val taskRepository = ServiceLocator.taskRepository(application)

    private val _uiState = MutableStateFlow(TodoUiState())

    /**
     * 显示列表 = 全量任务（Repository 已按 未完成置顶 + 截止时间升序 排序）
     * × 搜索关键字（标题/描述，忽略大小写）实时过滤。
     */
    val uiState: StateFlow<TodoUiState> = combine(
        taskRepository.allTasks,
        _uiState
    ) { tasks, state ->
        val query = state.searchQuery.trim()
        val filtered = if (query.isEmpty()) {
            tasks
        } else {
            tasks.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.description?.contains(query, ignoreCase = true) == true
            }
        }
        state.copy(tasks = filtered, loading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodoUiState()
    )

    // ===== 搜索 =====

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // ===== 新增 / 编辑对话框 =====

    fun showAddDialog() = _uiState.update { it.copy(showEditor = true, editingTask = null) }

    fun showEditDialog(task: TaskEntity) = _uiState.update { it.copy(showEditor = true, editingTask = task) }

    fun dismissEditor() = _uiState.update { it.copy(showEditor = false, editingTask = null) }

    /** 保存任务：编辑模式下更新原任务，否则新增。 */
    fun saveTask(title: String, description: String?, priority: Priority, dueDateTime: Long?) {
        val editing = _uiState.value.editingTask
        viewModelScope.launch {
            if (editing == null) {
                taskRepository.addTask(
                    TaskEntity(
                        title = title,
                        description = description,
                        priority = priority,
                        dueDateTime = dueDateTime
                    )
                )
            } else {
                taskRepository.updateTask(
                    editing.copy(
                        title = title,
                        description = description,
                        priority = priority,
                        dueDateTime = dueDateTime
                    )
                )
            }
            dismissEditor()
        }
    }

    // ===== 完成状态 =====

    /** 勾选 / 取消勾选：切换完成状态并记录完成时间。 */
    fun toggleComplete(task: TaskEntity) {
        viewModelScope.launch { taskRepository.toggleComplete(task) }
    }

    // ===== 删除（长按 → 确认对话框 → 删除）=====

    fun requestDelete(task: TaskEntity) = _uiState.update { it.copy(pendingDeleteTask = task) }

    fun dismissDelete() = _uiState.update { it.copy(pendingDeleteTask = null) }

    fun confirmDelete() {
        val task = _uiState.value.pendingDeleteTask ?: return
        viewModelScope.launch {
            taskRepository.deleteTask(task)
            dismissDelete()
        }
    }

    companion object {
        /** ViewModel 工厂（AndroidViewModel 需要 Application）。 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                TodoViewModel(app)
            }
        }
    }
}
