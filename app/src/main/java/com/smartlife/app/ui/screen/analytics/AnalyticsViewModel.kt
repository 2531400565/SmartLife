package com.smartlife.app.ui.screen.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartlife.app.data.local.entity.FocusSessionEntity
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.util.DateUtils
import com.smartlife.app.util.WeekUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/**
 * 单日专注数据点（P3 数据分析）。
 *
 * @property label    日期标签（如 "8/25"）
 * @property minutes  当日专注分钟数
 * @property isToday  是否为今天（图表高亮用）
 */
data class FocusDayPoint(
    val label: String,
    val minutes: Long,
    val isToday: Boolean = false
)

/**
 * 数据分析页 UI 状态（v2.0 P3）。
 *
 * 三大板块，全部基于已有 Task / FocusSession / Course 数据内存计算，
 * 不新增任何数据库字段、不改动 Room 结构：
 * - 专注趋势：近 7 天柱状 + 近 30 天折线；
 * - 待办效率：完成率 + 日均完成数；
 * - 课程分布：课程总数 + 第 1 周~当前周每周课程数（单双周按周次计入）。
 */
data class AnalyticsUiState(
    // ===== 专注趋势 =====
    val focus7: List<FocusDayPoint> = emptyList(),     // 近 7 天（索引 0 = 最早，末位 = 今天）
    val focus30: List<FocusDayPoint> = emptyList(),    // 近 30 天
    val focus7TotalMinutes: Long = 0L,                 // 近 7 天总分钟
    val focus7AvgMinutes: Long = 0L,                   // 近 7 天日均分钟
    val focus7BestLabel: String? = null,               // 近 7 天峰值日标签（无记录为 null）
    val focus30TotalMinutes: Long = 0L,                // 近 30 天总分钟
    val focus30AvgMinutes: Long = 0L,                  // 近 30 天日均分钟
    val focus30BestMinutes: Long = 0L,                 // 近 30 天单日峰值（分钟）

    // ===== 待办效率 =====
    val todoTotal: Int = 0,                            // 总待办
    val todoCompleted: Int = 0,                        // 已完成
    val todoIncomplete: Int = 0,                       // 进行中
    val completionRate: Int = 0,                       // 完成率 0~100
    val avgDailyCompleted: Double = 0.0,               // 日均完成数（保留 1 位小数）

    // ===== 课程分布 =====
    val courseTotal: Int = 0,                          // 课程总数
    val currentWeek: Int = 0,                          // 当前周次（未开学为 0）
    val weekCourses: List<Int> = emptyList(),          // 每周课程数（最近若干周，见 MAX_WEEK_BARS）
    val weekCoursesFrom: Int = 0,                      // weekCourses 起始周次（0 = 无数据）
    val weekCoursesBest: Int = 0,                      // 每周课程数峰值（柱状图基准）

    val loading: Boolean = true
)

/**
 * 数据分析页 ViewModel（v2.0 P3）。
 *
 * 数据流：Room 表变更 → DAO Flow → Repository Flow → combine → StateFlow（UI 订阅）。
 * 全部聚合为内存计算，不修改任何 Entity / DAO / Migration。
 */
class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val taskRepository = ServiceLocator.taskRepository(application)
    private val focusRepository = ServiceLocator.focusSessionRepository(application)
    private val courseRepository = ServiceLocator.courseRepository(application)

    /** 聚合后的分析页状态。 */
    val uiState: StateFlow<AnalyticsUiState> = combine(
        taskRepository.allTasks,
        focusRepository.allSessions,
        courseRepository.allCourses,
        SettingsRepository.semesterStartDate(context)
    ) { tasks, sessions, courses, semesterStart ->
        val now = System.currentTimeMillis()

        // ===== 专注趋势：近 7 天 / 近 30 天逐日分钟数 =====
        val focus7 = computeDailyMinutes(sessions, 7)
        val focus30 = computeDailyMinutes(sessions, 30)
        val focus7Total = focus7.sumOf { it.minutes }
        val focus30Total = focus30.sumOf { it.minutes }
        val focus30Best = focus30.maxOfOrNull { it.minutes } ?: 0L

        // ===== 待办效率 =====
        val todoTotal = tasks.size
        val todoCompleted = tasks.count { it.isCompleted }
        val completionRate =
            if (todoTotal > 0) (todoCompleted * 100 / todoTotal).coerceIn(0, 100) else 0

        // 日均完成数：已完成数 ÷ 使用天数（最早创建日至今的自然天数，最少记 1 天）
        val firstCreated = tasks.minOfOrNull { it.createdAt }
        val usedDays = if (firstCreated == null) {
            0
        } else {
            (((DateUtils.startOfToday() - DateUtils.startOfDay(firstCreated)) / DAY_MILLIS) + 1)
                .toInt()
                .coerceAtLeast(1)
        }
        val avgDailyCompleted =
            if (usedDays > 0) (todoCompleted.toDouble() / usedDays * 10).toLong() / 10.0 else 0.0

        // ===== 课程分布 =====
        // 周次由「学期开始日期」推算，学期结束后仍会继续累加（第 30、50 周…）。
        // 柱状图只取最近 MAX_WEEK_BARS 周，否则几十根柱子会挤成一片无法阅读。
        val inSession = !WeekUtils.isNotStarted(now, semesterStart)
        val currentWeek = if (inSession) WeekUtils.weekNumber(now, semesterStart) else 0
        val fromWeek = if (currentWeek > MAX_WEEK_BARS) currentWeek - MAX_WEEK_BARS + 1 else 1
        val weekCourses = if (inSession && currentWeek > 0) {
            (fromWeek..currentWeek).map { week ->
                courses.count {
                    WeekUtils.isActive(it.weekType, week, it.startWeek, it.endWeek)
                }
            }
        } else {
            emptyList()
        }

        AnalyticsUiState(
            focus7 = focus7,
            focus30 = focus30,
            focus7TotalMinutes = focus7Total,
            focus7AvgMinutes = if (focus7.isNotEmpty()) focus7Total / focus7.size else 0L,
            focus7BestLabel = focus7.maxByOrNull { it.minutes }?.takeIf { it.minutes > 0 }?.label,
            focus30TotalMinutes = focus30Total,
            focus30AvgMinutes = if (focus30.isNotEmpty()) focus30Total / focus30.size else 0L,
            focus30BestMinutes = focus30Best,
            todoTotal = todoTotal,
            todoCompleted = todoCompleted,
            todoIncomplete = todoTotal - todoCompleted,
            completionRate = completionRate,
            avgDailyCompleted = avgDailyCompleted,
            courseTotal = courses.size,
            currentWeek = currentWeek,
            weekCourses = weekCourses,
            weekCoursesFrom = if (weekCourses.isEmpty()) 0 else fromWeek,
            weekCoursesBest = weekCourses.maxOrNull() ?: 0,
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalyticsUiState()
    )

    /**
     * 按自然日聚合最近 [days] 天的专注分钟数。
     * 返回列表长度恒为 [days]（含今天，索引 0 为最早一天），无记录的天为 0。
     *
     * 注意：先按天累加**秒数**、最后再一次性换算为分钟。
     * 若对每条记录单独整除（actualSeconds / 60），不足 1 分钟的记录会被各自截断为 0，
     * 多条短会话合计的时长就凭空丢失了。
     */
    private fun computeDailyMinutes(
        sessions: List<FocusSessionEntity>,
        days: Int
    ): List<FocusDayPoint> {
        if (days <= 0) return emptyList()
        val todayStart = DateUtils.startOfToday()
        val rangeStart = todayStart - (days - 1) * DAY_MILLIS
        val seconds = LongArray(days)
        for (s in sessions) {
            val dayStart = DateUtils.startOfDay(s.startedAt)
            val index = ((dayStart - rangeStart) / DAY_MILLIS).toInt()
            if (index in 0 until days) seconds[index] += s.actualSeconds
        }
        return seconds.mapIndexed { index, value ->
            FocusDayPoint(
                label = dayLabel(rangeStart + index * DAY_MILLIS),
                minutes = value / 60,
                isToday = index == days - 1
            )
        }
    }

    /** 时间戳 → "M/d" 日期标签（如 "8/25"）。 */
    private fun dayLabel(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    companion object {
        private const val DAY_MILLIS = 86_400_000L

        /** 每周课程数柱状图最多展示的周数（避免学期结束后柱子无限增多）。 */
        private const val MAX_WEEK_BARS = 16

        /** ViewModel 工厂（AndroidViewModel 需要 Application）。 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                AnalyticsViewModel(app)
            }
        }
    }
}
