package com.smartlife.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartlife.app.ui.theme.ThemeMode
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
}
