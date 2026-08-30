package com.smartlife.app.ui.screen.timetable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartlife.app.data.local.WeekType
import com.smartlife.app.data.local.entity.CourseEntity
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
    val courses: List<CourseEntity> = emptyList(),     // 当天课程（按开始时间排序）
    val showEditor: Boolean = false,                   // 是否显示 新增/编辑 对话框
    val editingCourse: CourseEntity? = null,           // 编辑中的课程（null 表示新增）
    val pendingDeleteCourse: CourseEntity? = null,     // 待删除确认的课程
    val loading: Boolean = true
)

/**
 * 课程表 ViewModel：CourseRepository 数据 + 星期筛选 + 增删改。
 * 全部数据写入 Room；不修改任何 DAO/Repository 公共接口。
 */
class TimetableViewModel(application: Application) : AndroidViewModel(application) {

    private val courseRepository = ServiceLocator.courseRepository(application)

    private val _uiState = MutableStateFlow(TimetableUiState())

    /**
     * 显示列表 = 全部课程（Room Flow，变更即刷新）
     * 按选中星期（weekdays 集合包含） + 当前单双周过滤，再按开始时间升序。
     */
    val uiState: StateFlow<TimetableUiState> = combine(
        courseRepository.allCourses,
        _uiState
    ) { courses, state ->
        val weekNumber = WeekUtils.weekNumber(System.currentTimeMillis())
        val dayCourses = courses
            .filter { it.weekdays.contains(state.selectedDay) && WeekUtils.isActive(it.weekType, weekNumber) }
            .sortedBy { it.startMinute }
        state.copy(courses = dayCourses, loading = false)
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
        weekType: WeekType
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
                        weekType = weekType
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
                        weekType = weekType
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
