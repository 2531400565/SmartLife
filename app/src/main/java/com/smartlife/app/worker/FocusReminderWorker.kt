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

/**
 * 专注结束提醒 Worker（一次性延迟任务）。
 * 由 FocusViewModel 在开始/继续专注时调度，initialDelay = 剩余专注时长；
 * 即使应用进程被回收，WorkManager 也会在时间点附近执行并发出本地通知。
 */
class FocusReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        showReminderNotification(applicationContext)
        return Result.success()
    }

    /** 发送"专注结束"本地通知。 */
    private fun showReminderNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // 通知渠道（仅首次创建生效）
        val channel = NotificationChannel(
            CHANNEL_ID,
            "专注提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "番茄钟结束时发送提醒"
        }
        manager.createNotificationChannel(channel)

        // 点击通知回到 App
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("专注结束")
            .setContentText("本轮番茄钟已完成，起来休息一下吧")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Android 13+ 未授予通知权限时静默忽略（计时功能不受影响）
        }
    }

    companion object {
        const val CHANNEL_ID = "focus_reminder"
        const val NOTIFICATION_ID = 1001
    }
}
