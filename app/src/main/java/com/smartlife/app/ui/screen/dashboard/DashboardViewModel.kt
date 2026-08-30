package com.smartlife.app.ui.screen.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 首页 UI 状态（由 ViewModel 聚合四个 Repository 产出）。
 */
data class DashboardUiState(
    val dateText: String = "",            // 今日日期文本
    val todayTodoCount: Int = 0,          // 今日待办数量
    val todayCourseCount: Int = 0,        // 今日课程数量
    val todayFocusSeconds: Long = 0L,     // 今日专注秒数
    val todayFocusText: String = "0秒",   // 今日专注时长（已格式化，供 UI 直接展示）
    val quoteText: String = "",           // 随机励志语
    val loading: Boolean = true           // 首次加载中
)

/**
 * 首页 ViewModel：以 StateFlow 聚合 Task / Course / FocusSession / Quote 四个 Repository。
 * 数据流：Room 表变更 → DAO Flow → Repository Flow → combine → StateFlow（UI 订阅）。
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val taskRepository = ServiceLocator.taskRepository(application)
    private val courseRepository = ServiceLocator.courseRepository(application)
    private val focusRepository = ServiceLocator.focusSessionRepository(application)
    private val quoteRepository = ServiceLocator.quoteRepository(application)

    /** 随机励志语（单独维护，进入页面与点击刷新时更新）。 */
    private val quote = MutableStateFlow<String?>(null)

    /** 聚合后的首页状态。 */
    val uiState: StateFlow<DashboardUiState> = combine(
        taskRepository.todayTodoCount(),
        courseRepository.todayCourseCount(),
        focusRepository.todayFocusSeconds(),
        quote
    ) { todo, course, seconds, q ->
        DashboardUiState(
            dateText = DateUtils.todayText(),
            todayTodoCount = todo,
            todayCourseCount = course,
            todayFocusSeconds = seconds,
            todayFocusText = formatDuration(seconds),
            quoteText = q ?: "",
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    init {
        loadQuote()
    }

    /** 刷新随机励志语（点击励志语卡片时调用）。 */
    fun refreshQuote() = loadQuote()

    /** 从数据库随机取一条励志语。 */
    private fun loadQuote() {
        viewModelScope.launch {
            quote.value = quoteRepository.getRandomQuote()?.text
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
        /** ViewModel 工厂（AndroidViewModel 需要 Application）。 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                DashboardViewModel(app)
            }
        }
    }
}
