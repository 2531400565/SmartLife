package com.smartlife.app.ui.screen.timetable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartlife.app.data.local.CourseType
import com.smartlife.app.data.local.WeekType
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.util.DateUtils
import com.smartlife.app.util.WeekUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 课程表 UI 状态。
 */
data class TimetableUiState(
    val selectedDay: Int = DateUtils.todayDayOfWeek(), // 当前选中的星期（默认今天）
    val courses: List<CourseEntity> = emptyList(),     // 展示周内选中当天的课程（按开始时间排序）
    val weekCourses: List<CourseEntity> = emptyList(), // 展示周全部课程（周网格视图，v2.2）
    val showEditor: Boolean = false,                   // 是否显示 新增/编辑 对话框
    val editingCourse: CourseEntity? = null,           // 编辑中的课程（null 表示新增）
    val pendingDeleteCourse: CourseEntity? = null,     // 待删除确认的课程
    val loading: Boolean = true,
    // 学期周信息（由 semesterStartDate 动态计算；weekOffset 为 0 时即"本周"）
    val semesterStartDate: Long? = null,   // 学期开始日期
    val isNotStarted: Boolean = true,      // 是否未开学
    val weekOffset: Int = 0,               // 展示周相对本周的偏移（v2.2：周导航）
    val weekNumber: Int? = null,           // 展示周数（未开学为 null）
    val weekType: WeekType? = null,        // 展示周单双周（未开学为 null）
    val weekStart: Long? = null,           // 展示周开始日期
    val weekEnd: Long? = null              // 展示周结束日期
)

/** 学期展示周允许的范围（与课程起始/结束周一致）。 */
private const val WEEK_MIN = 1
private const val WEEK_MAX = 30
private const val DAY_MILLIS = 86_400_000L

/**
 * 课程表 ViewModel：CourseRepository 数据 + 星期筛选 + 周导航 + 增删改。
 * 全部数据写入 Room；不修改任何 DAO/Repository 公共接口。
 */
class TimetableViewModel(application: Application) : AndroidViewModel(application) {

    private val courseRepository = ServiceLocator.courseRepository(application)
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(TimetableUiState())

    /**
     * 显示列表 = 全部课程（Room Flow，变更即刷新）
     * × 学期开始日期（DataStore，变更即刷新）
     * 过滤条件：展示周 = 本周 + weekOffset（未开学不偏移）；
     * - dayCourses：选中星期（weekdays 集合包含）+ 周次判定；
     * - weekCourses：仅周次判定（周网格用全部星期课程）。
     * 均按开始时间升序。
     */
    val uiState: StateFlow<TimetableUiState> = combine(
        courseRepository.allCourses,
        SettingsRepository.semesterStartDate(context),
        _uiState
    ) { courses, semesterStart, state ->
        val now = System.currentTimeMillis()
        val notStarted = WeekUtils.isNotStarted(now, semesterStart)
        val baseWeek = if (notStarted) null else WeekUtils.weekNumber(now, semesterStart)
        val displayWeek = if (baseWeek == null) {
            null
        } else {
            (baseWeek + state.weekOffset).coerceIn(WEEK_MIN, WEEK_MAX)
        }
        val displayWeekStart = if (semesterStart != null && displayWeek != null) {
            DateUtils.startOfDay(semesterStart) + (displayWeek - 1) * 7 * DAY_MILLIS
        } else {
            null
        }
        val dayCourses = courses
            .filter {
                it.weekdays.contains(state.selectedDay) &&
                    WeekUtils.isActive(it.weekType, displayWeek, it.startWeek, it.endWeek)
            }
            .sortedBy { it.startMinute }
        val weekCourses = courses
            .filter { WeekUtils.isActive(it.weekType, displayWeek, it.startWeek, it.endWeek) }
            .sortedBy { it.startMinute }
        state.copy(
            courses = dayCourses,
            weekCourses = weekCourses,
            loading = false,
            semesterStartDate = semesterStart,
            isNotStarted = notStarted,
            weekNumber = displayWeek,
            weekType = if (displayWeek == null) null else WeekUtils.weekType(displayWeek),
            weekStart = displayWeekStart,
            weekEnd = displayWeekStart?.plus(6 * DAY_MILLIS)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TimetableUiState()
    )

    // ===== 星期切换 =====

    fun selectDay(day: Int) {
        if (day in 1..7) {
            _uiState.update { it.copy(selectedDay = day) }
        }
    }

    // ===== 周导航（v2.2：上一周 / 下一周 / 回到本周）=====

    /** 上一周（展示周最低到第 1 周）。 */
    fun previousWeek() = shiftWeek(-1)

    /** 下一周（展示周最高到第 30 周）。 */
    fun nextWeek() = shiftWeek(1)

    /** 回到本周。 */
    fun backToCurrentWeek() = _uiState.update { it.copy(weekOffset = 0) }

    /** 依据当前真实周次 + 偏移移动展示周，并在 [WEEK_MIN, WEEK_MAX] 内收敛。 */
    private fun shiftWeek(delta: Int) {
        val state = _uiState.value
        val semesterStart = state.semesterStartDate ?: return
        val now = System.currentTimeMillis()
        if (WeekUtils.isNotStarted(now, semesterStart)) return
        val base = WeekUtils.weekNumber(now, semesterStart)
        val target = (base + state.weekOffset + delta).coerceIn(WEEK_MIN, WEEK_MAX)
        _uiState.update { it.copy(weekOffset = target - base) }
    }

    // ===== 新增 / 编辑对话框 =====

    fun showAddDialog() = _uiState.update { it.copy(showEditor = true, editingCourse = null) }

    fun showEditDialog(course: CourseEntity) =
        _uiState.update { it.copy(showEditor = true, editingCourse = course) }

    fun dismissEditor() = _uiState.update { it.copy(showEditor = false, editingCourse = null) }

    /** 保存课程：编辑模式更新原课程，否则新增。 */
    fun saveCourse(
        name: String,
        location: String?,
        teacher: String?,
        weekdays: Set<Int>,
        startMinute: Int,
        endMinute: Int,
        examDate: Long?,
        weekType: WeekType,
        startWeek: Int,
        endWeek: Int,
        courseType: CourseType
    ) {
        val editing = _uiState.value.editingCourse
        viewModelScope.launch {
            if (editing == null) {
                courseRepository.addCourse(
                    CourseEntity(
                        name = name,
                        location = location,
                        teacher = teacher,
                        weekdays = weekdays,
                        startMinute = startMinute,
                        endMinute = endMinute,
                        examDate = examDate,
                        weekType = weekType,
                        startWeek = startWeek,
                        endWeek = endWeek,
                        courseType = courseType
                    )
                )
            } else {
                courseRepository.updateCourse(
                    editing.copy(
                        name = name,
                        location = location,
                        teacher = teacher,
                        weekdays = weekdays,
                        startMinute = startMinute,
                        endMinute = endMinute,
                        examDate = examDate,
                        weekType = weekType,
                        startWeek = startWeek,
                        endWeek = endWeek,
                        courseType = courseType
                    )
                )
            }
            dismissEditor()
        }
    }

    // ===== 删除（长按 → 确认对话框 → 删除）=====

    fun requestDelete(course: CourseEntity) =
        _uiState.update { it.copy(pendingDeleteCourse = course) }

    fun dismissDelete() = _uiState.update { it.copy(pendingDeleteCourse = null) }

    fun confirmDelete() {
        val course = _uiState.value.pendingDeleteCourse ?: return
        viewModelScope.launch {
            courseRepository.deleteCourse(course)
            dismissDelete()
        }
    }

    companion object {
        /** ViewModel 工厂（AndroidViewModel 需要 Application）。 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                TimetableViewModel(app)
            }
        }
    }
}
