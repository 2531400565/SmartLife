package com.smartlife.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartlife.app.MainActivity
import com.smartlife.app.R
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.ui.navigation.Routes
import com.smartlife.app.util.DateUtils
import kotlinx.coroutines.flow.first

/**
 * 课程提醒 Worker（P1）：在上课前 N 分钟发出本地通知。
 *
 * 由 [CourseReminderScheduler] 以「精确延迟」的方式调度；本 Worker 只负责：
 * 1. 发出通知（标题 📚 课程名 / 正文「X 分钟后开始（HH:mm）」/ 教室）；
 * 2. 点击通知跳转课表页；
 * 3. 触发后回调调度器排下一次提醒，使周期提醒可长期自维护。
 */
class CourseReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val name = inputData.getString(CourseReminderScheduler.KEY_COURSE_NAME)
            ?: return Result.success()
        val location = inputData.getString(CourseReminderScheduler.KEY_LOCATION).orEmpty()
        val startMinute = inputData.getInt(CourseReminderScheduler.KEY_START_MINUTE, -1)
        val lead = inputData.getInt(
            CourseReminderScheduler.KEY_LEAD_MINUTES,
            SettingsRepository.DEFAULT_LEAD_MINUTES
        )

        showNotification(applicationContext, name, location, startMinute, lead)

        // 触发后重排下一次提醒（课程可能已变化，这里重新读取全量课程）
        runCatching { rescheduleNext(applicationContext) }

        return Result.success()
    }

    /** 发送「即将上课」本地通知。 */
    private fun showNotification(
        context: Context,
        courseName: String,
        location: String,
        startMinute: Int,
        leadMinutes: Int
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // 通知渠道（仅首次创建生效）
        val channel = NotificationChannel(
            CHANNEL_ID,
            "课程提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "上课前按设定时间提醒即将开始的课程"
        }
        manager.createNotificationChannel(channel)

        // 点击通知 → 打开课表页
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_ROUTE, Routes.TIMETABLE)
        }
        val requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 如：📚 高等数学 / 20 分钟后开始（08:00）/ 教三402
        val startTimeText =
            if (startMinute >= 0) DateUtils.formatMinute(startMinute) else ""
        val content = if (startTimeText.isBlank()) {
            "$leadMinutes 分钟后开始"
        } else {
            "$leadMinutes 分钟后开始（$startTimeText）"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📚 $courseName")
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        // 说明：不使用 setDefaults(DEFAULT_ALL)，其中含 DEFAULT_VIBRATE，
        // 需要 android.permission.VIBRATE，本项目未声明，可能在部分机型抛 SecurityException；
        // 提示音 / 提醒行为统一由 IMPORTANCE_HIGH 通知渠道控制。

        // 教室信息作为展开后的补充内容
        if (location.isNotBlank()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle().bigText("$content\n$location")
            )
            builder.setSubText(location)
        }

        try {
            val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ 未授予通知权限时静默忽略（不影响其它功能）
        }
    }

    /** 重新读取全量课程并重排提醒。 */
    private suspend fun rescheduleNext(context: Context) {
        val courses = ServiceLocator.appDatabase(context).courseDao().observeAll().first()
        CourseReminderScheduler.rescheduleAll(context, courses)
    }

    companion object {
        const val CHANNEL_ID = "course_reminder"
    }
}
