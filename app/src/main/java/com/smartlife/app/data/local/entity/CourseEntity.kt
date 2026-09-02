package com.smartlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smartlife.app.data.local.CourseType
import com.smartlife.app.data.local.WeekType

/**
 * 课程实体（表：courses）。
 * weekdays：上课的星期集合，1(周一) ~ 7(周日)，经 TypeConverter 存为 "1,3,5"；
 * weekType：单双周（EVERY/ODD/EVEN），经 TypeConverter 存为枚举名；
 * startWeek / endWeek：上课周次范围（闭区间，如 1~16），旧数据默认 1~16；
 * courseType：课程性质（EXAM/ASSESSMENT/UNKNOWN），经 TypeConverter 存为枚举名；
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
    val weekType: WeekType = WeekType.EVERY,  // 单双周（默认每周）
    val startWeek: Int = 1,                   // 起始周次（默认第 1 周）
    val endWeek: Int = 16,                    // 结束周次（默认第 16 周）
    val courseType: CourseType = CourseType.UNKNOWN // 课程性质（默认未知）
)
