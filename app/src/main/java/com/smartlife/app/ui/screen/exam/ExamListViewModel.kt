package com.smartlife.app.ui.screen.exam

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 一场考试的展示数据（v1.3 P3，v2.0 P1 增加教室）。 */
data class ExamItem(
    val courseId: Long,
    val courseName: String,
    /** 考试日期（当天 00:00 时间戳） */
    val examDate: Long,
    /** 剩余天数（0 = 今天考试） */
    val daysLeft: Int,
    /** 教室（可选，复用 CourseEntity.location） */
    val location: String? = null
)

/**
 * 紧急程度（v1.3 P3）：
 * - [RED]    ≤7 天
 * - [ORANGE] 8~30 天
 * - [BLUE]   30 天以上
 */
enum class ExamUrgency { RED, ORANGE, BLUE }

/** 剩余天数 → 紧急程度。 */
fun examUrgency(daysLeft: Int): ExamUrgency = when {
    daysLeft <= 7 -> ExamUrgency.RED
    daysLeft <= 30 -> ExamUrgency.ORANGE
    else -> ExamUrgency.BLUE
}

/**
 * 考试中心顶部统计（v2.0 P1）：
 * - [totalCount]       本学期考试数量（设置了考试日期的课程总数，含已结束）
 * - [nearestName]      最近一场未结束考试的课程名（无则空串）
 * - [nearestDaysLeft]  最近一场未结束考试的剩余天数（无未结束考试时为 -1）
 * - [nearestExamDate]  最近一场未结束考试的日期（当天 00:00 时间戳，无则为 0）
 * - [endedCount]       已结束考试数量
 */
data class ExamStats(
    val totalCount: Int = 0,
    val nearestName: String = "",
    val nearestDaysLeft: Int = -1,
    val nearestExamDate: Long = 0L,
    val endedCount: Int = 0
)

/** 考试中心筛选 Tab（v2.0 体验补充）：全部 / 未结束 / 已结束，默认未结束。 */
enum class ExamFilter(val label: String) {
    ALL("全部"),
    UPCOMING("未结束"),
    ENDED("已结束")
}

/** 考试中心 UI 状态（v2.0 P1 + 筛选补充）。 */
data class ExamListUiState(
    val loading: Boolean = true,
    /** 课程名称搜索关键字 */
    val query: String = "",
    /** 当前筛选 Tab（默认未结束） */
    val filter: ExamFilter = ExamFilter.UPCOMING,
    /** 筛选后的考试列表（未结束在前按剩余天数升序，已结束在后） */
    val exams: List<ExamItem> = emptyList(),
    val stats: ExamStats = ExamStats()
)

/**
 * 考试中心 ViewModel（v1.3 P3 列表页增强，v2.0 P1 加入统计与搜索）。
 *
 * 数据来源：[CourseEntity.examDate]（已有字段），经 CourseRepository 的 allCourses 流派生；
 * 不新增 Entity / DAO / Migration，Room version 保持不变。
 */
class ExamListViewModel(application: Application) : AndroidViewModel(application) {

    private val courseRepository = ServiceLocator.courseRepository(application)

    /** 搜索关键字（实时）。 */
    private val query = MutableStateFlow("")

    /** 筛选 Tab（默认未结束）。 */
    private val filter = MutableStateFlow(ExamFilter.UPCOMING)

    val uiState: StateFlow<ExamListUiState> = combine(
        courseRepository.allCourses,
        query,
        filter
    ) { courses, q, f ->
        val now = System.currentTimeMillis()
        ExamListUiState(
            loading = false,
            query = q,
            filter = f,
            exams = buildExamList(courses, now, q, f),
            stats = buildExamStats(courses, now)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExamListUiState()
    )

    /** 更新搜索关键字。 */
    fun setQuery(value: String) {
        query.value = value
    }

    /** 切换筛选 Tab（全部 / 未结束 / 已结束）。 */
    fun setFilter(value: ExamFilter) {
        filter.value = value
    }

    /**
     * 取考试列表（按当前筛选），排序：未结束在前按剩余天数升序，
     * 已结束在后按结束日期倒序（最近结束的排最前）。
     * 支持按课程名实时过滤。
     */
    private fun buildExamList(
        courses: List<CourseEntity>,
        now: Long,
        rawQuery: String,
        filter: ExamFilter
    ): List<ExamItem> {
        val keyword = rawQuery.trim()
        val todayStart = DateUtils.startOfDay(now)
        return courses
            .mapNotNull { course ->
                val exam = course.examDate ?: return@mapNotNull null
                val examStart = DateUtils.startOfDay(exam)
                val daysLeft = ((examStart - todayStart) / DAY_MILLIS).toInt()
                when (filter) {
                    ExamFilter.UPCOMING -> if (daysLeft < 0) return@mapNotNull null
                    ExamFilter.ENDED -> if (daysLeft >= 0) return@mapNotNull null
                    ExamFilter.ALL -> Unit
                }
                if (keyword.isNotEmpty() &&
                    !course.name.contains(keyword, ignoreCase = true)
                ) return@mapNotNull null
                ExamItem(
                    courseId = course.id,
                    courseName = course.name,
                    examDate = examStart,
                    daysLeft = daysLeft,
                    location = course.location
                )
            }
            .sortedWith(
                compareBy(
                    { if (it.daysLeft < 0) 1 else 0 },
                    { if (it.daysLeft < 0) -it.daysLeft else it.daysLeft }
                )
            )
    }

    /** 顶部统计：总数（含已结束）/ 最近考试名 / 已结束数量。统计不受搜索影响。 */
    private fun buildExamStats(courses: List<CourseEntity>, now: Long): ExamStats {
        val todayStart = DateUtils.startOfDay(now)
        var total = 0
        var ended = 0
        var nearest: CourseEntity? = null
        var nearestDays = Int.MAX_VALUE

        courses.forEach { course ->
            val exam = course.examDate ?: return@forEach
            total++
            val examStart = DateUtils.startOfDay(exam)
            if (examStart < todayStart) {
                ended++
            } else {
                val daysLeft = ((examStart - todayStart) / DAY_MILLIS).toInt()
                if (daysLeft < nearestDays) {
                    nearestDays = daysLeft
                    nearest = course
                }
            }
        }

        return ExamStats(
            totalCount = total,
            nearestName = nearest?.name ?: "",
            nearestDaysLeft = if (nearest != null) nearestDays else -1,
            nearestExamDate = nearest?.examDate?.let { DateUtils.startOfDay(it) } ?: 0L,
            endedCount = ended
        )
    }

    companion object {
        private const val DAY_MILLIS = 86_400_000L

        /** ViewModel 工厂（AndroidViewModel 需要 Application）。 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ExamListViewModel(app)
            }
        }
    }
}
