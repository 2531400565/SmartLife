package com.smartlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smartlife.app.data.local.WeekType

/**
 * 课程实体（表：courses）。
 * weekdays：上课的星期集合，1(周一) ~ 7(周日)，经 TypeConverter 存为 "1,3,5"；
 * weekType：单双周（EVERY/ODD/EVEN），经 TypeConverter 存为枚举名；
 * startMinute / endMinute：当天 0~1439 的分钟数（例如 08:30 = 510）；
 * examDate：考试日期时间戳（可选）。
 */
@Entity(
    tableName = "courses",
    indices = [Index("weekdays")]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,                         // 课程名称
    val location: String? = null,             // 教室（可选）
    val teacher: String? = null,              // 任课老师（可选）
    val weekdays: Set<Int>,                   // 上课星期集合 1~7
    val startMinute: Int,                     // 开始时间（当天分钟数）
    val endMinute: Int,                       // 结束时间（当天分钟数）
    val examDate: Long? = null,               // 考试日期时间戳（可选）
    val weekType: WeekType = WeekType.EVERY   // 单双周（默认每周）
)
