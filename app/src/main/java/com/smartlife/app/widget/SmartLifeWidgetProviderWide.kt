package com.smartlife.app.widget

import android.content.Context
import android.widget.RemoteViews
import com.smartlife.app.R
import com.smartlife.app.ui.navigation.Routes

/**
 * SmartLife 桌面小组件 · 4×2（v2.0 P2 新增）。
 *
 * 展示：下一节课程（名称 + 时间/教室）/ 今日待办 / 今日专注 / 一键开始专注。
 * 点击：课程区 → 课表，待办区 → 待办，专注区 → 专注，开始按钮 → 专注页并自动开始。
 */
class SmartLifeWidgetProviderWide : BaseWidgetProvider() {

    override fun layoutResId(): Int = R.layout.widget_smartlife_wide

    override fun bindViews(context: Context, views: RemoteViews, data: WidgetData) {
        views.setTextViewText(
            R.id.widget_course_name,
            data.nextCourseName.ifBlank { context.getString(R.string.widget_no_course) }
        )
        views.setTextViewText(R.id.widget_course_time, data.nextCourseDetail)
        views.setTextViewText(R.id.widget_todo_value, data.todoCount.toString())
        views.setTextViewText(R.id.widget_focus_value, data.focusText)

        views.setOnClickPendingIntent(R.id.widget_course, clickIntent(context, Routes.TIMETABLE))
        views.setOnClickPendingIntent(R.id.widget_todo, clickIntent(context, Routes.TODO))
        views.setOnClickPendingIntent(R.id.widget_focus, clickIntent(context, Routes.FOCUS))
        views.setOnClickPendingIntent(
            R.id.widget_start_focus,
            clickIntent(context, Routes.FOCUS, salt = "start", autoStartFocus = true)
        )
    }
}
