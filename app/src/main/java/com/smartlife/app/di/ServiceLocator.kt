package com.smartlife.app.di

import android.content.Context
import com.smartlife.app.data.local.AppDatabase
import com.smartlife.app.data.repository.CourseRepository
import com.smartlife.app.data.repository.FocusSessionRepository
import com.smartlife.app.data.repository.QuoteRepository
import com.smartlife.app.data.repository.TaskRepository

/**
 * 轻量服务定位器（无 DI 框架时的简单方案）。
 * Repository 均为薄封装（内部仅持 DAO 引用），每次新建成本极低；
 * 数据库本身为单例，因此数据源始终唯一。
 */
object ServiceLocator {

    @Volatile
    private var database: AppDatabase? = null

    /** 获取应用数据库单例。 */
    fun appDatabase(context: Context): AppDatabase =
        database ?: synchronized(this) {
            database ?: AppDatabase.getInstance(context.applicationContext).also { database = it }
        }

    fun taskRepository(context: Context): TaskRepository =
        TaskRepository(appDatabase(context).taskDao())

    fun courseRepository(context: Context): CourseRepository =
        CourseRepository(appDatabase(context).courseDao())

    fun focusSessionRepository(context: Context): FocusSessionRepository =
        FocusSessionRepository(appDatabase(context).focusSessionDao())

    fun quoteRepository(context: Context): QuoteRepository =
        QuoteRepository(appDatabase(context).quoteDao())
}
