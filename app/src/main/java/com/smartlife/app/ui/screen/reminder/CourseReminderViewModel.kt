package com.smartlife.app.ui.screen.reminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.worker.CourseReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 课程提醒设置 ViewModel（P1）。
 *
 * - 开关与提前时间经 DataStore 持久化（[SettingsRepository]）；
 * - 任一设置变更后立即重排 WorkManager 提醒，无需重启应用；
 * - 使用 AndroidViewModel 获取 Application Context，避免持有 Activity。
 */
class CourseReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>().applicationContext

    /** 课程提醒是否开启（默认开启）。 */
    val enabled: StateFlow<Boolean> = SettingsRepository.courseReminderEnabled(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 提前提醒分钟数（默认 [SettingsRepository.DEFAULT_LEAD_MINUTES]）。 */
    val leadMinutes: StateFlow<Int> = SettingsRepository.courseReminderLeadMinutes(context)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsRepository.DEFAULT_LEAD_MINUTES
        )

    /** 切换总开关；关闭时取消所有已排程提醒。 */
    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            SettingsRepository.setCourseReminderEnabled(context, value)
            rescheduleReminders()
        }
    }

    /** 修改提前时间。 */
    fun setLeadMinutes(value: Int) {
        viewModelScope.launch {
            SettingsRepository.setCourseReminderLeadMinutes(context, value)
            rescheduleReminders()
        }
    }

    /** 重新读取全量课程并重排提醒。 */
    private suspend fun rescheduleReminders() {
        runCatching {
            val courses = ServiceLocator.appDatabase(context).courseDao().observeAll().first()
            CourseReminderScheduler.rescheduleAll(context, courses)
        }
    }
}
