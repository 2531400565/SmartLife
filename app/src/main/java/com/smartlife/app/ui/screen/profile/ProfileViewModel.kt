package com.smartlife.app.ui.screen.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.withTransaction
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.ui.theme.ThemeMode
import com.smartlife.app.util.JsonBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * "我的"页 UI 状态：五类数据统计。
 */
data class ProfileUiState(
    val totalTasks: Int = 0,             // 总待办数量
    val completedTasks: Int = 0,         // 已完成待办数量
    val totalFocusSeconds: Long = 0L,    // 总专注时长（秒）
    val completedFocusSessions: Int = 0, // 已完成专注轮数
    val totalCourses: Int = 0,           // 课程总数
    val loading: Boolean = true
)

/**
 * "我的"页 ViewModel：
 * - 统计：聚合 Task / FocusSession / Course 三个 Repository 的现有 Flow（内存计算，不改 DAO）
 * - 主题：DataStore 持久化读写
 * - 导入/导出：JsonBackup + Room 事务（失败整体回滚，不破坏现有数据）
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val taskRepository = ServiceLocator.taskRepository(application)
    private val focusRepository = ServiceLocator.focusSessionRepository(application)
    private val courseRepository = ServiceLocator.courseRepository(application)
    private val quoteRepository = ServiceLocator.quoteRepository(application)

    /** 统计：全部基于现有 Repository 的 Flow，数据库变更自动刷新。 */
    val uiState: StateFlow<ProfileUiState> = combine(
        taskRepository.allTasks,
        focusRepository.allSessions,
        courseRepository.allCourses
    ) { tasks, sessions, courses ->
        ProfileUiState(
            totalTasks = tasks.size,
            completedTasks = tasks.count { it.isCompleted },
            totalFocusSeconds = sessions.sumOf { it.actualSeconds },
            completedFocusSessions = sessions.count { it.completed },
            totalCourses = courses.size,
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    // ===== 主题模式（DataStore 持久化）=====

    val themeMode: StateFlow<ThemeMode> = SettingsRepository.themeMode(appContext)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            SettingsRepository.setThemeMode(appContext, mode)
        }
    }

    // ===== 导出 =====

    /** 收集当前全部数据并生成 JSON（供系统分享）。 */
    suspend fun buildExportJson(): String {
        val tasks = taskRepository.allTasks.first()
        val courses = courseRepository.allCourses.first()
        val sessions = focusRepository.allSessions.first()
        val quotes = quoteRepository.allQuotes.first()
        return JsonBackup.export(tasks, courses, sessions, quotes)
    }

    // ===== 导入（事务保证原子性）=====

    /**
     * 导入 JSON 备份。
     * 安全保证：
     * 1) 先完整解析校验（JsonBackup.parse），非法格式直接失败，不动数据库；
     * 2) 校验通过后在【单个 Room 事务】内「清空 + 批量插入」，任一步失败整体回滚；
     * 3) 不使用 destructive migration，也不清库文件。
     * 成功回调 (true, 提示)；失败回调 (false, 原因)。
     */
    fun importJson(json: String, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val data = JsonBackup.parse(json)
                val db = ServiceLocator.appDatabase(appContext)
                db.withTransaction {
                    val backup = db.backupDao()
                    backup.clearTasks()
                    backup.clearCourses()
                    backup.clearFocusSessions()
                    backup.clearQuotes()
                    backup.insertTasks(data.tasks)
                    backup.insertCourses(data.courses)
                    backup.insertFocusSessions(data.sessions)
                    backup.insertQuotes(data.quotes)
                }
            }
            result.onSuccess {
                onResult(true, "导入成功，数据已恢复")
            }.onFailure { e ->
                onResult(false, "导入失败：${e.message ?: "未知错误"}")
            }
        }
    }

    companion object {
        /** ViewModel 工厂（AndroidViewModel 需要 Application）。 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ProfileViewModel(app)
            }
        }
    }
}
