package com.smartlife.app

import android.app.Application
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.worker.CourseReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmartLifeApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 启动时刷新课程提醒：覆盖「设备重启 / 应用被杀 / 课程在外部被改」等场景
        refreshCourseReminders()
    }

    private fun refreshCourseReminders() {
        appScope.launch {
            runCatching {
                val courses = ServiceLocator.appDatabase(this@SmartLifeApplication)
                    .courseDao()
                    .observeAll()
                    .first()
                CourseReminderScheduler.rescheduleAll(this@SmartLifeApplication, courses)
            }
        }
    }
}
