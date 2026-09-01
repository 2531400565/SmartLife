package com.smartlife.app.ui.screen.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.withTransaction
import com.smartlife.app.data.local.entity.FocusSessionEntity
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.ui.theme.ThemeMode
import com.smartlife.app.util.CsvCourseParser
import com.smartlife.app.util.DateUtils
import com.smartlife.app.util.JsonBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 专注统计（P4）：某时间范围内的专注汇总。
 *
 * @property totalSeconds    总专注时长（秒）
 * @property completedCount  完整完成的轮数
 * @property averageSeconds  平均每轮时长（秒；无完成轮数时为 0）
 */
data class FocusStats(
    val totalSeconds: Long = 0L,
    val completedCount: Int = 0,
    val averageSeconds: Long = 0L
)

/**
 * 本周专注（P4）：周一~周日逐日分钟数 + 派生统计。
 *
 * @property minutes      周一~周日（索引 0=周一）每天的专注分钟数
 * @property totalSeconds 本周总专注秒数
 * @property avgSeconds   日均专注秒数（按本周已过天数 1..今天 计算）
 * @property bestDayLabel 专注最多的星期标签（如 "周三"；本周无专注时为 null）
 */
data class WeekFocusStats(
    val minutes: List<Long> = List(7) { 0L },
    val totalSeconds: Long = 0L,
    val avgSeconds: Long = 0L,
    val bestDayLabel: String? = null
)

/**
 * "我的"页 UI 状态：五类数据统计。
 */
data class ProfileUiState(
    val totalTasks: Int = 0,             // 总待办数量
    val completedTasks: Int = 0,         // 已完成待办数量
    val totalFocusSeconds: Long = 0L,    // 总专注时长（秒）
    val completedFocusSessions: Int = 0, // 已完成专注轮数
    val totalCourses: Int = 0,           // 课程总数
    val focusToday: FocusStats = FocusStats(),  // 今日专注统计
    val focusWeek: FocusStats = FocusStats(),   // 本周专注统计
    val focusMonth: FocusStats = FocusStats(),  // 本月专注统计
    val focusWeekDays: WeekFocusStats = WeekFocusStats(), // 本周逐日（周一~周日）专注统计（P4）
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
        // 专注统计（P4）：今日 / 本周 / 本月，全部基于已有 FocusSession 数据内存计算
        val now = System.currentTimeMillis()
        val todayStart = DateUtils.startOfDay(now)
        val weekStart = DateUtils.startOfWeek(now)
        val monthStart = DateUtils.startOfMonth(now)

        ProfileUiState(
            totalTasks = tasks.size,
            completedTasks = tasks.count { it.isCompleted },
            totalFocusSeconds = sessions.sumOf { it.actualSeconds },
            completedFocusSessions = sessions.count { it.completed },
            totalCourses = courses.size,
            focusToday = computeFocusStats(sessions.filter { it.startedAt >= todayStart }),
            focusWeek = computeFocusStats(sessions.filter { it.startedAt >= weekStart }),
            focusMonth = computeFocusStats(sessions.filter { it.startedAt >= monthStart }),
            focusWeekDays = computeWeekFocusStats(sessions, weekStart, now),
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    /**
     * 汇总某时间范围内的专注数据（P4）。
     * 平均时长 = 总时长 / 完成轮数；没有完成轮数时记为 0，避免除零。
     */
    private fun computeFocusStats(sessions: List<FocusSessionEntity>): FocusStats {
        val total = sessions.sumOf { it.actualSeconds }
        val completed = sessions.count { it.completed }
        val average = if (completed > 0) total / completed else 0L
        return FocusStats(
            totalSeconds = total,
            completedCount = completed,
            averageSeconds = average
        )
    }

    /**
     * 按周一~周日汇总本周每日专注分钟数（P4）。
     * 仅统计 [weekStart, 当前] 范围内的记录；日均按本周已过天数（1..今天）计算。
     */
    private fun computeWeekFocusStats(
        sessions: List<FocusSessionEntity>,
        weekStart: Long,
        now: Long
    ): WeekFocusStats {
        val minutes = LongArray(7)
        var totalSeconds = 0L
        for (s in sessions) {
            if (s.startedAt < weekStart) continue
            val idx = DateUtils.dayOfWeek(s.startedAt) - 1   // 1=周一 → 索引 0
            if (idx !in 0..6) continue
            minutes[idx] += s.actualSeconds / 60
            totalSeconds += s.actualSeconds
        }
        val elapsedDays = DateUtils.dayOfWeek(now).coerceIn(1, 7)
        val avgSeconds = if (elapsedDays > 0) totalSeconds / elapsedDays else 0L
        val weekLabels = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val bestIdx = minutes.indices.maxByOrNull { minutes[it] }
        return WeekFocusStats(
            minutes = minutes.toList(),
            totalSeconds = totalSeconds,
            avgSeconds = avgSeconds,
            bestDayLabel = if (bestIdx != null && minutes[bestIdx] > 0) weekLabels[bestIdx] else null
        )
    }

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

    // ===== CSV 导入课程（P3）=====

    /**
     * 导入课程 CSV（**追加导入，不删除已有课程**）。
     *
     * - 解析失败的行会被跳过，并在提示中附上错误明细（含行号）；
     * - 写入走 CourseRepository.addCourses（内部会自动重排课程提醒）。
     */
    fun importCsv(csv: String, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            val parsed = runCatching { CsvCourseParser.parse(csv) }.getOrNull()
            if (parsed == null) {
                onResult(false, "导入失败：CSV 解析异常")
                return@launch
            }

            if (parsed.courses.isEmpty()) {
                val detail = parsed.errors.take(3).joinToString("\n")
                val message = if (detail.isBlank()) {
                    "导入失败：没有可导入的课程"
                } else {
                    "导入失败：没有可导入的课程\n$detail"
                }
                onResult(false, message)
                return@launch
            }

            val count = runCatching { courseRepository.addCourses(parsed.courses) }.getOrNull()
            if (count == null) {
                onResult(false, "导入失败：写入数据库出错")
                return@launch
            }

            val message = buildString {
                append("成功导入 $count 门课程（追加，未删除已有课程）")
                if (parsed.errors.isNotEmpty()) {
                    append("\n跳过 ${parsed.errors.size} 行：")
                    append("\n" + parsed.errors.take(3).joinToString("\n"))
                    if (parsed.errors.size > 3) {
                        append("\n…等共 ${parsed.errors.size} 行")
                    }
                }
            }
            onResult(parsed.errors.isEmpty(), message)
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
