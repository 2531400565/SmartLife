package com.smartlife.app.util

import com.smartlife.app.data.local.WeekType
import java.util.Calendar

/**
 * 单双周计算工具。
 *
 * 以「学期第 1 周（周一）」为基准计算当前周数，再据此判定单 / 双周。
 * 逻辑与「写死今天第几周」解耦，[semesterStartMillis] 可被外部注入覆盖，
 * 预留后续接入学校校历 / 用户设置。
 */
object WeekUtils {

    private const val DAY_MILLIS = 86_400_000L

    /**
     * 学期第 1 周（周一）的 00:00 时间戳。
     * 当前为占位默认值（2026-07-27 周一），仅供单双周计算；
     * 后续接入学校校历或用户设置时，由调用方赋值覆盖（例如应用启动时读取设置）。
     */
    @Volatile
    var semesterStartMillis: Long = semesterStartOf(2026, 6, 27) // month 0-based：6 = 7 月

    /**
     * 计算某时间戳属于本学期第几周（从 1 开始）。
     * 早于开学日期时按第 1 周处理。
     */
    fun weekNumber(millis: Long): Int {
        val start = startOfMonday(semesterStartMillis)
        val diffDays = (DateUtils.startOfDay(millis) - start) / DAY_MILLIS
        return ((diffDays / 7 + 1).coerceAtLeast(1)).toInt()
    }

    /**
     * 判断某门课在指定周次是否应显示。
     * - EVERY：每周都显示
     * - ODD：仅单周（奇数周）
     * - EVEN：仅双周（偶数周）
     */
    fun isActive(courseWeekType: WeekType, weekNumber: Int): Boolean = when (courseWeekType) {
        WeekType.EVERY -> true
        WeekType.ODD -> weekNumber % 2 == 1
        WeekType.EVEN -> weekNumber % 2 == 0
    }

    /** 当前周次展示文本，如 "第 5 周（单周）"。 */
    fun currentWeekText(): String {
        val week = weekNumber(System.currentTimeMillis())
        val label = if (week % 2 == 1) "单周" else "双周"
        return "第 $week 周（$label）"
    }

    /** 将某时间戳对齐到它所在周的周一 00:00（周边界基准）。 */
    private fun startOfMonday(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = DateUtils.startOfDay(millis) }
        val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=周日 ... 7=周六
        val offsetToMonday = (dow + 5) % 7        // 回到周一需回退的天数
        cal.add(Calendar.DAY_OF_MONTH, -offsetToMonday)
        return cal.timeInMillis
    }

    /** 构造指定年/月/日的 00:00 时间戳（month 0-based）。 */
    private fun semesterStartOf(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
