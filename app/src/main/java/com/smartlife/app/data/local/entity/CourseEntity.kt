package com.smartlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 课程实体（表：courses）。
 * dayOfWeek：1(周一) ~ 7(周日)；
 * startMinute / endMinute：当天 0~1439 的分钟数（例如 08:30 = 510）；
 * examDate：考试日期时间戳（可选）。
 */
@Entity(
    tableName = "courses",
    indices = [Index("dayOfWeek")]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,              // 课程名称
    val location: String? = null,  // 教室（可选）
    val dayOfWeek: Int,            // 星期 1~7
    val startMinute: Int,          // 开始时间（当天分钟数）
    val endMinute: Int,            // 结束时间（当天分钟数）
    val examDate: Long? = null,    // 考试日期时间戳（可选）
    val teacher: String? = null    // 任课老师（可选；v2 由 Migration 新增，可空）
)
