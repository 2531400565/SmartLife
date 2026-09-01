package com.smartlife.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.smartlife.app.MainActivity
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.ui.navigation.Routes
import com.smartlife.app.util.DateUtils
import com.smartlife.app.util.WeekUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 桌面小组件展示数据（v2.0 P2）。
 *
 * - [todoCount]        今日未完成待办数量
 * - [nextCourseName]   下一节课程名称（今天没课为空串）
 * - [nextCourseDetail] 下一节课程「HH:mm」或「HH:mm · 教室」（今天没课为空串）
 * - [focusText]        今日专注时长文本（如 1时5分 / 23分 / 40秒）
 */
data class WidgetData(
    val todoCount: Int = 0,
    val nextCourseName: String = "",
    val nextCourseDetail: String = "",
    val focusText: String = "0秒"
)

/**
 * 桌面小组件基类（v2.0 P2 升级为双尺寸）：
 *
 * - 2×2 小组件 [SmartLifeWidgetProvider]：今日待办数量 + 下一节课程，点击进入 Dashboard；
 * - 4×2 宽组件 [SmartLifeWidgetProviderWide]：下一节课程 / 今日待办 / 今日专注 / 一键开始专注。
 *
 * 公共逻辑（数据读取、刷新、PendingIntent 构造）统一放基类；
 * 子类通过 [layoutResId] 与 [bindViews] 决定各自布局与点击行为。
 *
 * 实现说明：
 * - 使用原生 RemoteViews，不引入额外依赖（Glance 会新增 Gradle 依赖）；
 * - 深浅色：布局颜色全部引用 @color/widget_*，由 values / values-night 自动切换；
 * - 刷新：系统按 updatePeriodMillis（30 分钟）回调 + 应用回前台时主动刷新；
 * - 数据读取在 IO 协程中完成，并用 goAsync() 延长广播生命周期，避免被系统提前回收。
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    /** 子类布局资源 id。 */
    protected abstract fun layoutResId(): Int

    /** 子类绑定数据与点击事件。 */
    protected abstract fun bindViews(
        context: Context,
        views: RemoteViews,
        data: WidgetData
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val appContext = context.applicationContext
        // 广播生命周期很短，必须先挂起再异步取数据
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val data = loadData(appContext)
                val views = RemoteViews(appContext.packageName, layoutResId()).apply {
                    bindViews(appContext, this, data)
                }
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** 读取今日数据（全部走现有 Repository，不新增数据库字段）。 */
    private suspend fun loadData(context: Context): WidgetData {
        val todoCount = runCatching {
            ServiceLocator.taskRepository(context).todayTodoCount().first()
        }.getOrDefault(0)

        val focusSeconds = runCatching {
            ServiceLocator.focusSessionRepository(context).todayFocusSeconds().first()
        }.getOrDefault(0L)

        val courses = runCatching {
            ServiceLocator.courseRepository(context).allCourses.first()
        }.getOrDefault(emptyList())

        val semesterStart = runCatching {
            SettingsRepository.semesterStartDate(context).first()
        }.getOrNull()

        val next = findNextCourse(courses, semesterStart, System.currentTimeMillis())

        return WidgetData(
            todoCount = todoCount,
            nextCourseName = next?.name ?: "",
            nextCourseDetail = next?.let { course ->
                val time = DateUtils.formatMinute(course.startMinute)
                if (course.location.isNullOrBlank()) time else "$time · ${course.location}"
            } ?: "",
            focusText = formatDuration(focusSeconds)
        )
    }

    /**
     * 找出今天的下一节课（与首页 Hero Card 同一套规则）：
     * 今天有课 且 单双周匹配，且开始时间晚于当前时刻的最近一节。
     */
    private fun findNextCourse(
        courses: List<CourseEntity>,
        semesterStart: Long?,
        now: Long
    ): CourseEntity? {
        val day = DateUtils.todayDayOfWeek()
        val weekNumber = if (WeekUtils.isNotStarted(now, semesterStart)) {
            null
        } else {
            WeekUtils.weekNumber(now, semesterStart)
        }
        val nowMinute = DateUtils.minutesOfDay(now)
        return courses
            .filter { it.weekdays.contains(day) && WeekUtils.isActive(it.weekType, weekNumber) }
            .filter { it.startMinute > nowMinute }
            .minByOrNull { it.startMinute }
    }

    /**
     * 构造跳转到指定页面的 PendingIntent。
     *
     * [salt] 区分同一路由的不同入口（如「专注页」与「一键开始专注」），
     * [autoStartFocus] 为 true 时携带「自动开始专注」标记。
     * 用不同的 data Uri 与 requestCode 区分，避免 PendingIntent 被系统复用导致点错目标。
     */
    protected fun clickIntent(
        context: Context,
        route: String,
        salt: String = "",
        autoStartFocus: Boolean = false
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_ROUTE, route)
            if (autoStartFocus) putExtra(MainActivity.EXTRA_START_FOCUS, true)
            data = Uri.parse(
                "smartlife://widget/$route" +
                    salt.takeIf { it.isNotBlank() }?.let { "/$it" }.orEmpty()
            )
        }
        val key = route + salt
        return PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** 秒数 → 紧凑时长文本（小组件空间有限）。 */
    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        return when {
            h > 0 -> "${h}时${m}分"
            m > 0 -> "${m}分"
            else -> "${seconds % 60}秒"
        }
    }

    companion object {
        /** 全部 SmartLife 小组件 Provider（2×2 小组件 + 4×2 宽组件）。 */
        private val allProviders = arrayOf(
            SmartLifeWidgetProvider::class.java,
            SmartLifeWidgetProviderWide::class.java
        )

        /**
         * 主动刷新全部小组件（应用回到前台时调用）。
         * 以广播形式触发各 Provider 的 onUpdate，复用同一套数据读取与渲染逻辑。
         */
        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                allProviders.forEach { providerClass ->
                    runCatching {
                        val manager = AppWidgetManager.getInstance(appContext)
                        val ids = manager.getAppWidgetIds(ComponentName(appContext, providerClass))
                        if (ids.isEmpty()) return@runCatching
                        val intent = Intent(appContext, providerClass).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        }
                        appContext.sendBroadcast(intent)
                    }
                }
            }
        }
    }
}
