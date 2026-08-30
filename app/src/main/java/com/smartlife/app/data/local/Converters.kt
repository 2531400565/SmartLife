package com.smartlife.app.data.local

import androidx.room.TypeConverter

/**
 * Room 类型转换器：用于 CourseEntity 的复合字段。
 * - weekdays(Set<Int>) ⇄ String（升序逗号分隔，如 "1,3,5"）
 * - weekType(WeekType) ⇄ String（枚举名，如 "EVERY"）
 */
class Converters {

    /** Set<Int> → "1,3,5"（升序，保证存储稳定、可读）。 */
    @TypeConverter
    fun fromWeekdays(weekdays: Set<Int>): String = weekdays.sorted().joinToString(",")

    /** "1,3,5" → Set<Int>（过滤非法值；空串返回空集）。 */
    @TypeConverter
    fun toWeekdays(value: String): Set<Int> =
        if (value.isBlank()) emptySet()
        else value.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()

    /** WeekType → 枚举名。 */
    @TypeConverter
    fun fromWeekType(weekType: WeekType): String = weekType.name

    /** 枚举名 → WeekType（未知值回退 EVERY，避免旧/异常数据崩溃）。 */
    @TypeConverter
    fun toWeekType(value: String): WeekType =
        runCatching { WeekType.valueOf(value) }.getOrDefault(WeekType.EVERY)
}
