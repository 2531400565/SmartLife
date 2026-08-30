package com.smartlife.app.ui.screen.semester

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartlife.app.data.local.WeekType
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.util.DateUtils
import com.smartlife.app.util.WeekUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 学期设置页 UI 状态。
 */
data class SemesterUiState(
    val semesterStartDate: Long? = null,   // 学期开始日期（null = 未设置）
    val isNotStarted: Boolean = true,      // 是否未开学（未设置也视为未开学）
    val weekNumber: Int? = null,           // 当前周数（未开学为 null）
    val weekType: WeekType? = null         // 当前单双周（未开学为 null）
)

/**
 * 学期设置 ViewModel：读取/保存学期开始日期（DataStore），
 * 并据其计算当前周数与单双周供界面展示。
 */
class SemesterViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    val uiState: StateFlow<SemesterUiState> =
        SettingsRepository.semesterStartDate(context)
            .map { date ->
                val now = System.currentTimeMillis()
                val notStarted = WeekUtils.isNotStarted(now, date)
                SemesterUiState(
                    semesterStartDate = date,
                    isNotStarted = notStarted,
                    weekNumber = if (notStarted) null else WeekUtils.weekNumber(now, date),
                    weekType = if (notStarted) null else WeekUtils.weekType(WeekUtils.weekNumber(now, date))
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SemesterUiState())

    /** 保存学期开始日期；传 null 表示清除设置。内部统一归一到当天 00:00。 */
    fun setSemesterStartDate(date: Long?) {
        viewModelScope.launch {
            SettingsRepository.setSemesterStartDate(context, date?.let { DateUtils.startOfDay(it) })
        }
    }

    companion object {
        /** ViewModel 工厂（AndroidViewModel 需要 Application）。 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                SemesterViewModel(app)
            }
        }
    }
}
