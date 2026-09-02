package com.smartlife.app.ui.screen.semester

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartlife.app.data.local.CourseType
import com.smartlife.app.data.local.WeekType
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 当前学期课程页 UI 状态。
 */
data class SemesterCoursesUiState(
    val courses: List<CourseEntity> = emptyList(), // 全部课程（按开始时间排序）
    val showEditor: Boolean = false,               // 是否显示 新增/编辑 对话框
    val editingCourse: CourseEntity? = null,       // 编辑中的课程
    val pendingDeleteCourse: CourseEntity? = null, // 待删除确认的课程
    val loading: Boolean = true
)

/**
 * 当前学期课程 ViewModel：复用 CourseRepository，列出全部课程（按开始时间排序），
 * 支持编辑（复用 CourseAddEditDialog）与长按删除。
 * 不新增数据库字段；统计与列表保持一致（全部课程即当前学期课程）。
 */
class SemesterCoursesViewModel(application: Application) : AndroidViewModel(application) {

    private val courseRepository = ServiceLocator.courseRepository(application)

    private val _uiState = MutableStateFlow(SemesterCoursesUiState())

    val uiState: StateFlow<SemesterCoursesUiState> = combine(
        courseRepository.allCourses,
        _uiState
    ) { courses, state ->
        state.copy(courses = courses.sortedBy { it.startMinute }, loading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SemesterCoursesUiState()
    )

    // ===== 编辑（复用 CourseAddEditDialog）=====

    fun showEditDialog(course: CourseEntity) =
        _uiState.update { it.copy(showEditor = true, editingCourse = course) }

    fun dismissEditor() = _uiState.update { it.copy(showEditor = false, editingCourse = null) }

    /** 保存课程：更新当前编辑中的课程（本页仅编辑，不新增）。 */
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
        val editing = _uiState.value.editingCourse ?: return
        viewModelScope.launch {
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
                SemesterCoursesViewModel(app)
            }
        }
    }
}
