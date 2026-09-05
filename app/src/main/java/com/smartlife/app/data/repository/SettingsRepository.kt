package com.smartlife.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartlife.app.ui.theme.ThemeMode
import com.smartlife.app.util.CoursePeriod
import com.smartlife.app.util.CoursePeriods
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 应用级 DataStore（Preferences）。顶层委托保证进程内单例。 */
private val Context.dataStore by preferencesDataStore(name = "smartlife_settings")

/**
 * 设置仓库：主题模式等用户偏好，经 DataStore 持久化（跨进程重启保留）。
 * 不涉及数据库，不改动任何 Room 接口。
 */
object SettingsRepository {

    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

    /** 学期开始日期（当天 00:00 的 epoch 毫秒；未设置为 null）。 */
    private val SEMESTER_START_KEY = longPreferencesKey("semester_start_date")

    /** 课程提醒总开关（默认开启）。 */
    private val COURSE_REMINDER_ENABLED_KEY = booleanPreferencesKey("course_reminder_enabled")

    /** 课程提醒提前分钟数（默认 20 分钟）。 */
    private val COURSE_REMINDER_LEAD_KEY = intPreferencesKey("course_reminder_lead_minutes")

    /** 节次时刻表（v2.2）：JSON 字符串 `08:00-08:45,…`；未设置=null（用默认）。 */
    private val COURSE_PERIODS_KEY = stringPreferencesKey("course_periods")

    /** 当前主题模式（默认跟随系统）。 */
    fun themeMode(context: Context): Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[THEME_MODE_KEY]
            raw?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM)
            } ?: ThemeMode.SYSTEM
        }

    /** 保存主题模式。 */
    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }

    /** 学期开始日期（可空，未设置时为 null）。 */
    fun semesterStartDate(context: Context): Flow<Long?> =
        context.dataStore.data.map { prefs -> prefs[SEMESTER_START_KEY] }

    /** 保存学期开始日期；传 null 表示清除设置。 */
    suspend fun setSemesterStartDate(context: Context, date: Long?) {
        context.dataStore.edit { prefs ->
            if (date == null) prefs.remove(SEMESTER_START_KEY)
            else prefs[SEMESTER_START_KEY] = date
        }
    }

    // ===== 课程提醒设置 =====

    /** 课程提醒是否开启（默认开启）。 */
    fun courseReminderEnabled(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[COURSE_REMINDER_ENABLED_KEY] ?: true
        }

    /** 保存课程提醒开关。 */
    suspend fun setCourseReminderEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[COURSE_REMINDER_ENABLED_KEY] = enabled }
    }

    /** 课程提醒提前分钟数（默认 [DEFAULT_LEAD_MINUTES]）。 */
    fun courseReminderLeadMinutes(context: Context): Flow<Int> =
        context.dataStore.data.map { prefs ->
            prefs[COURSE_REMINDER_LEAD_KEY] ?: DEFAULT_LEAD_MINUTES
        }

    /** 保存课程提醒提前分钟数。 */
    suspend fun setCourseReminderLeadMinutes(context: Context, minutes: Int) {
        context.dataStore.edit { prefs -> prefs[COURSE_REMINDER_LEAD_KEY] = minutes }
    }

    // ===== 节次时刻表（v2.2）=====

    /**
     * 节次时刻表：null 表示未自定义（UI 层回退 [DEFAULT_COURSE_PERIODS]）。
     * 仅用于课程录入快捷填充 / CSV 换算，不改动 Room 课程数据。
     */
    fun coursePeriods(context: Context): Flow<List<CoursePeriod>?> =
        context.dataStore.data.map { prefs -> CoursePeriods.decode(prefs[COURSE_PERIODS_KEY]) }

    /** 保存节次时刻表；传 null 表示恢复默认（清除自定义）。 */
    suspend fun setCoursePeriods(context: Context, periods: List<CoursePeriod>?) {
        context.dataStore.edit { prefs ->
            if (periods == null) prefs.remove(COURSE_PERIODS_KEY)
            else prefs[COURSE_PERIODS_KEY] = CoursePeriods.encode(periods)
        }
    }

    // ===== 课程提醒常量 =====
    // 说明：本类本身就是 object，Kotlin 不允许在 object 内再声明 companion object，
    // 因此常量直接作为成员，以 SettingsRepository.DEFAULT_LEAD_MINUTES 访问。

    /** 默认提前提醒时间（分钟）。 */
    const val DEFAULT_LEAD_MINUTES = 20

    /** 可选的提前时间（分钟）。 */
    val LEAD_MINUTE_OPTIONS: List<Int> = listOf(10, 15, 20, 30)
}
