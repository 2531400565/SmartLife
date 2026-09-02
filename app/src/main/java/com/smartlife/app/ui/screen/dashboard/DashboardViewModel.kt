package com.smartlife.app.ui.screen.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.data.local.entity.TaskEntity
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.util.DateUtils
import com.smartlife.app.util.WeekUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 下一节课程（首页 Hero Card，v1.2.1）。
 *
 * @property name              课程名称
 * @property startMinute       开始时间（当天分钟数）
 * @property endMinute         结束时间（当天分钟数）
 * @property location          教室（可空）
 * @property minutesUntilStart 距离上课的分钟数
 */
data class NextCourse(
    val name: String,
    val startMinute: Int,
    val endMinute: Int,
    val location: String?,
    val minutesUntilStart: Int
)

/**
 * 今日目标完成进度（v1.2.1）。
 *
 * 完成率 = 已完成 / 今日待办；不新增任何数据库字段，全部基于已有待办数据内存计算。
 */
data class TodayGoal(
    val total: Int = 0,
    val completed: Int = 0
) {
    /** 完成百分比（0~100）；今日无待办时为 0。 */
    val percent: Int
        get() = if (total <= 0) 0 else (completed * 100 / total).coerceIn(0, 100)

    /**
     * 今日目标展示状态（v1.3.1）：
     * 今日无待办 → [GoalState.EMPTY]；全部完成 → [GoalState.COMPLETED]；否则 → [GoalState.PROGRESS]。
     */
    val state: GoalState
        get() = when {
            total <= 0 -> GoalState.EMPTY
            completed >= total -> GoalState.COMPLETED
            else -> GoalState.PROGRESS
        }
}

/**
 * 今日目标三种展示状态（v1.3.1，纯 UI 推导，无数据库字段）：
 * - [EMPTY]：今日无待办，显示空态文案与灰色占位条（不显示百分比）；
 * - [PROGRESS]：有待办但未全部完成，显示完成率大数字 + 进度条动画填充；
 * - [COMPLETED]：全部完成，显示 100% 与绿色满格进度条。
 */
enum class GoalState {
    EMPTY,
    PROGRESS,
    COMPLETED
}

/**
 * 首页 UI 状态（由 ViewModel 聚合四个 Repository 产出）。
 */
data class DashboardUiState(
    val dateText: String = "",            // 今日日期文本
    val todayTodoCount: Int = 0,          // 今日待办数量（未完成且今天到期）
    val todayCourseCount: Int = 0,        // 今日课程数量
    val todayFocusSeconds: Long = 0L,     // 今日专注秒数
    val todayFocusText: String = "0秒",   // 今日专注时长（已格式化，供 UI 直接展示）
    val quoteText: String = "",           // 随机励志语
    val quotePeriod: QuotePeriod = QuotePeriod.MORNING, // 当前寄语所属时段（06-12 励志 / 12-18 效率 / 其他 放松）
    val nextExam: NextExam? = null,       // 最近一场未结束的考试（没有考试为 null）
    val nextCourse: NextCourse? = null,   // 下一节课程（今天没有待上的课为 null）
    val todayGoal: TodayGoal = TodayGoal(),// 今日目标完成情况
    val loading: Boolean = true           // 首次加载中
)

/**
 * 最近考试信息（P2）。
 *
 * @property courseName 课程名称
 * @property daysLeft   剩余天数（0 = 今天考试）
 * @property examDate   考试日期时间戳（当天 00:00 起算）
 */
data class NextExam(
    val courseName: String,
    val daysLeft: Int,
    val examDate: Long
)

/**
 * 寄语时段（v1.3 P1）。
 *
 * 约束：不修改 Entity / DAO / Migration（Room version 保持不变），
 * 因此改为在内存层按寄语主键 `id % 3` 把寄语划分为三组，分别对应三个时段。
 * 数据来源依然是数据库内置寄语，未改动任何表结构与查询。
 */
enum class QuotePeriod(val label: String, val emoji: String) {
    /** 06:00–11:59 励志 */
    MORNING("晨间励志", "☀️"),

    /** 12:00–17:59 效率 */
    AFTERNOON("午后效率", "⚡"),

    /** 18:00–23:59 放松（深夜 00:00–05:59 一并归入放松） */
    EVENING("晚间放松", "🌙");

    /** 该寄语是否属于本时段（按 id % 3 分组）。 */
    fun matches(quoteId: Long): Boolean = (quoteId % 3).toInt() == ordinal

    companion object {
        /** 依据当前小时判定所属时段。 */
        fun current(now: Long = System.currentTimeMillis()): QuotePeriod =
            when (DateUtils.hourOfDay(now)) {
                in 6..11 -> MORNING
                in 12..17 -> AFTERNOON
                else -> EVENING
            }
    }
}

/** 当前展示的寄语文本及其所属时段（供 UI 显示时段标签）。 */
data class QuoteState(val text: String, val period: QuotePeriod)

/**
 * 首页 ViewModel：以 StateFlow 聚合 Task / Course / FocusSession / Quote 四个 Repository。
 * 数据流：Room 表变更 → DAO Flow → Repository Flow → combine → StateFlow（UI 订阅）。
 *
 * v1.2.1：在聚合层补充「下一节课程」与「今日目标」两项 UI 数据，
 * 仅做内存计算，不新增数据库字段、不改动 Repository。
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val taskRepository = ServiceLocator.taskRepository(application)
    private val courseRepository = ServiceLocator.courseRepository(application)
    private val focusRepository = ServiceLocator.focusSessionRepository(application)
    private val quoteRepository = ServiceLocator.quoteRepository(application)
    private val context = application.applicationContext

    /** 当前寄语（含时段，单独维护；进入页面与点击刷新时更新）。 */
    private val quoteState = MutableStateFlow<QuoteState?>(null)

    /** 聚合后的首页状态。 */
    val uiState: StateFlow<DashboardUiState> = combine(
        taskRepository.allTasks,          // 用全部任务推导今日待办数量与今日目标，避免多次查库
        courseRepository.allCourses,
        SettingsRepository.semesterStartDate(context),
        focusRepository.todayFocusSeconds(),
        quoteState
    ) { tasks, courses, semesterStart, seconds, qs ->
        // 今日课程数：当前星期 + 当前单双周（按学期设置动态计算）
        val now = System.currentTimeMillis()
        val day = DateUtils.todayDayOfWeek()
        val weekNumber = if (WeekUtils.isNotStarted(now, semesterStart)) {
            null
        } else {
            WeekUtils.weekNumber(now, semesterStart)
        }
        val todayCourseCount = courses.count {
            it.weekdays.contains(day) && WeekUtils.isActive(it.weekType, weekNumber, it.startWeek, it.endWeek)
        }

        DashboardUiState(
            dateText = DateUtils.todayText(),
            todayTodoCount = computeTodayIncompleteCount(tasks, now),
            todayCourseCount = todayCourseCount,
            todayFocusSeconds = seconds,
            todayFocusText = formatDuration(seconds),
            quoteText = qs?.text ?: "",
            quotePeriod = qs?.period ?: QuotePeriod.current(now),
            nextExam = findNextExam(courses, now),
            nextCourse = findNextCourse(courses, semesterStart, now),
            todayGoal = computeTodayGoal(tasks, now),
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    /**
     * 今日未完成待办数：与 DAO 的 observeTodayCount 语义一致
     * （未完成 且 今天 00:00 <= dueDateTime < 次日 00:00）。
     */
    private fun computeTodayIncompleteCount(tasks: List<TaskEntity>, now: Long): Int {
        val start = DateUtils.startOfDay(now)
        val end = start + DAY_MILLIS
        return tasks.count { task ->
            val due = task.dueDateTime ?: return@count false
            !task.isCompleted && due >= start && due < end
        }
    }

    /**
     * 今日目标：统计今天到期的全部待办（含已完成）与其中已完成的数量。
     */
    private fun computeTodayGoal(tasks: List<TaskEntity>, now: Long): TodayGoal {
        val start = DateUtils.startOfDay(now)
        val end = start + DAY_MILLIS
        val todayTasks = tasks.filter { task ->
            val due = task.dueDateTime ?: return@filter false
            due >= start && due < end
        }
        return TodayGoal(
            total = todayTasks.size,
            completed = todayTasks.count { it.isCompleted }
        )
    }

    /**
     * 找出今天的下一节课（v1.2.1 Hero Card）。
     *
     * 规则：仅统计「今天有课 且 单双周匹配」的课程中，开始时间晚于当前时刻的最近一节；
     * 已经开始或今天已无课则返回 null（UI 显示「今天没有课程」）。
     */
    private fun findNextCourse(
        courses: List<CourseEntity>,
        semesterStart: Long?,
        now: Long
    ): NextCourse? {
        val day = DateUtils.todayDayOfWeek()
        val weekNumber = if (WeekUtils.isNotStarted(now, semesterStart)) {
            null
        } else {
            WeekUtils.weekNumber(now, semesterStart)
        }
        val nowMinute = DateUtils.minutesOfDay(now)

        return courses
            .filter { it.weekdays.contains(day) && WeekUtils.isActive(it.weekType, weekNumber, it.startWeek, it.endWeek) }
            .filter { it.startMinute > nowMinute }
            .minByOrNull { it.startMinute }
            ?.let { course ->
                NextCourse(
                    name = course.name,
                    startMinute = course.startMinute,
                    endMinute = course.endMinute,
                    location = course.location,
                    minutesUntilStart = course.startMinute - nowMinute
                )
            }
    }

    /**
     * 找出距离今天最近的一场「未结束」考试（P2）。
     *
     * 规则：
     * - 数据来源为 [CourseEntity.examDate]；
     * - 考试日期早于今天（已结束）的课程直接排除；
     * - 取剩余天数最少的一场；没有任何考试则返回 null（UI 显示「暂无考试」）。
     */
    private fun findNextExam(courses: List<CourseEntity>, now: Long): NextExam? {
        val todayStart = DateUtils.startOfDay(now)
        return courses
            .mapNotNull { course ->
                val exam = course.examDate ?: return@mapNotNull null
                val examStart = DateUtils.startOfDay(exam)
                if (examStart < todayStart) return@mapNotNull null   // 已结束，不显示
                val daysLeft = ((examStart - todayStart) / DAY_MILLIS).toInt()
                NextExam(courseName = course.name, daysLeft = daysLeft, examDate = examStart)
            }
            .minByOrNull { it.examDate }
    }

    init {
        loadQuote()
    }

    /** 刷新寄语（点击寄语卡片时调用），按当前时段重新抽取。 */
    fun refreshQuote() = loadQuote()

    /**
     * 按当前时段从数据库寄语中抽取一条。
     *
     * 数据源仍为 Room 内置寄语（不新增字段、不改 DAO、不加 Migration）；
     * 仅在内存层用 `id % 3` 分组，若该时段分组为空则退化为全量随机，保证任何数据状态下都有内容。
     */
    private fun loadQuote() {
        viewModelScope.launch {
            val period = QuotePeriod.current()
            val all = quoteRepository.allQuotes.first()
            val picked = all.filter { period.matches(it.id) }.randomOrNull() ?: all.randomOrNull()
            quoteState.value = picked?.let { QuoteState(text = it.text, period = period) }
        }
    }

    /** 秒数 → 中文本地化时长文本（如 1小时5分 / 23分15秒 / 40秒）。 */
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

    companion object {
        private const val DAY_MILLIS = 86_400_000L

        /** ViewModel 工厂（AndroidViewModel 需要 Application）。 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                DashboardViewModel(app)
            }
        }
    }
}
