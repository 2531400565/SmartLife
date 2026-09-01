package com.smartlife.app.widget

import android.content.Context
import android.widget.RemoteViews
import com.smartlife.app.R
import com.smartlife.app.ui.navigation.Routes

/**
 * SmartLife 桌面小组件 · 2×2（v2.0 P2）。
 *
 * 展示：今日待办数量 / 下一节课程（名称 + 时间/教室）。
 * 点击整卡任意区域 → 首页 Dashboard。
 */
class SmartLifeWidgetProvider : BaseWidgetProvider() {

    override fun layoutResId(): Int = R.layout.widget_smartlife

    override fun bindViews(context: Context, views: RemoteViews, data: WidgetData) {
        views.setTextViewText(R.id.widget_todo_value, data.todoCount.toString())
        views.setTextViewText(
            R.id.widget_course_name,
            data.nextCourseName.ifBlank { context.getString(R.string.widget_no_course) }
        )
        views.setTextViewText(R.id.widget_course_time, data.nextCourseDetail)

        // 整卡点击 → 首页 Dashboard
        val dashboard = clickIntent(context, Routes.DASHBOARD)
        views.setOnClickPendingIntent(R.id.widget_todo, dashboard)
        views.setOnClickPendingIntent(R.id.widget_course, dashboard)
    }
}
