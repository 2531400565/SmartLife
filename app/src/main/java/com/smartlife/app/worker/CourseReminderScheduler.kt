package com.smartlife.app.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.smartlife.app.data.local.WeekType
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.util.DateUtils
import com.smartlife.app.util.WeekUtils
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 课程提醒调度器（P1）。
 *
 * 为每门课程计算「下一次」应提醒的时刻，并用 WorkManager 的 OneTimeWorkRequest
 * 精确延迟调度（initialDelay = 距提醒时刻的毫秒数），因此可以做到精确到分钟。
 *
 * 设计要点：
 * - **每门课程同时只保留一个待执行任务**（enqueueUniqueWork + REPLACE），
 *   触发后由 [CourseReminderWorker] 回调本调度器排下一次，形成长期自维护的周期提醒；
 * - **重排时机**：课程增删改、提醒开关/提前时间变更、学期开始日期变更、应用启动；
 * - **星期 / 单双周过滤**复用 [WeekUtils]，与课表展示逻辑完全一致；
 * - 关闭提醒开关时，仅执行取消，不再排程任何任务。
 *
 * 课程列表由调用方传入（避免调度器反向依赖 Repository 形成循环依赖）。
 */
object CourseReminderScheduler {

    /** 所有课程提醒任务的标签，便于整体取消与排查。 */
    const val WORK_TAG = "course_reminder"

    private const val UNIQUE_WORK_PREFIX = "course_reminder_"

    /** 向后查找的最大天数：足以覆盖单/双周（2 周）与长假等场景。 */
    private const val MAX_LOOKAHEAD_DAYS = 60

    private const val DAY_MILLIS = 86_400_000L

    // ===== Worker 输入数据的键 =====
    const val KEY_COURSE_NAME = "key_course_name"
    const val KEY_LOCATION = "key_location"
    const val KEY_START_MINUTE = "key_start_minute"
    const val KEY_LEAD_MINUTES = "key_lead_minutes"

    /**
     * 重排全部课程提醒。
     *
     * @param context 上下文（内部会取 applicationContext）
     * @param courses 当前全部课程
     */
    suspend fun rescheduleAll(context: Context, courses: List<CourseEntity>) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)

        // 1) 开关关闭 → 整体取消后直接返回（后续无入队，不存在竞态）
        val enabled = SettingsRepository.courseReminderEnabled(appContext).first()
        if (!enabled) {
            workManager.cancelAllWorkByTag(WORK_TAG)
            return
        }

        // 2) 读取提前时间与学期开始日期（决定单双周）
        val leadMinutes = SettingsRepository.courseReminderLeadMinutes(appContext).first()
        val semesterStart = SettingsRepository.semesterStartDate(appContext).first()

        // 3) 为每门课程排下一次提醒。
        //    使用 enqueueUniqueWork + REPLACE：同课程只保留一条任务，重复调用即覆盖，
        //    因此无需先整体取消，也就避免了「取消」与「入队」的竞态。
        val now = System.currentTimeMillis()
        courses.forEach { course ->
            val reminderAt = nextReminderTime(course, semesterStart, leadMinutes, now)
                ?: return@forEach
            enqueue(appContext, workManager, course, reminderAt, leadMinutes)
        }
    }

    /**
     * 取消某门课程的提醒（课程被删除时调用）。
     *
     * 删除后课程已不在列表中，[rescheduleAll] 无法覆盖到它，
     * 因此这里按「唯一任务名」单独取消，确保不会残留已删除课程的提醒。
     */
    fun cancelForCourse(context: Context, courseId: Long) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_WORK_PREFIX + courseId)
    }

    /**
     * 计算某课程下一次提醒的时刻（毫秒时间戳）；没有可提醒的课时返回 null。
     *
     * 从今天起逐天向后查找，命中「星期匹配 + 单双周匹配」且提醒时刻晚于当前时刻的最近一次，
     * 因此天然是按时间顺序取最近的一次。
     */
    private fun nextReminderTime(
        course: CourseEntity,
        semesterStart: Long?,
        leadMinutes: Int,
        now: Long
    ): Long? {
        if (course.weekdays.isEmpty()) return null

        val todayStart = DateUtils.startOfDay(now)
        val leadMillis = leadMinutes * 60_000L

        for (offset in 0..MAX_LOOKAHEAD_DAYS) {
            val dayStart = todayStart + offset * DAY_MILLIS

            // 星期不匹配 → 当天无此课
            if (DateUtils.dayOfWeek(dayStart) !in course.weekdays) continue

            // 未开学时只提醒「每周」课程（与课表展示行为一致）
            if (WeekUtils.isNotStarted(dayStart, semesterStart) && course.weekType != WeekType.EVERY) {
                continue
            }

            // 单双周不匹配 → 本周无此课
            val weekNumber = WeekUtils.weekNumber(dayStart, semesterStart)
            if (!WeekUtils.isActive(course.weekType, weekNumber, course.startWeek, course.endWeek)) continue

            val startAt = dayStart + course.startMinute * 60_000L
            val reminderAt = startAt - leadMillis
            if (reminderAt > now) return reminderAt
        }
        return null
    }

    /** 入队一条课程提醒任务（同课程只保留一条，重复调用为替换）。 */
    private fun enqueue(
        context: Context,
        workManager: WorkManager,
        course: CourseEntity,
        reminderAt: Long,
        leadMinutes: Int
    ) {
        val delay = reminderAt - System.currentTimeMillis()
        if (delay <= 0) return

        val data = workDataOf(
            KEY_COURSE_NAME to course.name,
            KEY_LOCATION to (course.location ?: ""),
            KEY_START_MINUTE to course.startMinute,
            KEY_LEAD_MINUTES to leadMinutes
        )

        val request = OneTimeWorkRequestBuilder<CourseReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_PREFIX + course.id,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
