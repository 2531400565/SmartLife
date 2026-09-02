package com.smartlife.app.util

import com.smartlife.app.data.local.WeekType

/**
 * 单双周 / 周次计算工具（纯函数，无状态）。
 *
 * 所有计算都基于传入的 [semesterStartDate]（学期开始日期，来自用户设置），
 * 不使用自然周或年份周数，也不写死任何日期。
 *
 * 规则：
 * - 当前日期 < semesterStartDate → 未开学
 * - semesterStartDate 当天 → 第 1 周
 * - 周数 = floor((today - semesterStartDate) / 7) + 1
 * - 奇数周 = 单周，偶数周 = 双周
 */
object WeekUtils {

    private const val DAY_MILLIS = 86_400_000L

    /** 学期状态。 */
    enum class Status { NOT_STARTED, IN_SESSION }

    /** 是否未开学：未设置开学日期，或当前日期早于开学日期。 */
    fun isNotStarted(todayMillis: Long, semesterStartDate: Long?): Boolean {
        if (semesterStartDate == null) return true
        return DateUtils.startOfDay(todayMillis) < DateUtils.startOfDay(semesterStartDate)
    }

    /** 当前状态。 */
    fun status(todayMillis: Long, semesterStartDate: Long?): Status =
        if (isNotStarted(todayMillis, semesterStartDate)) Status.NOT_STARTED else Status.IN_SESSION

    /**
     * 周数（第 1 周起）。
     * 未开学时返回 1（无实际意义，应配合 [isNotStarted] 使用）。
     */
    fun weekNumber(todayMillis: Long, semesterStartDate: Long?): Int {
        if (semesterStartDate == null) return 1
        val start = DateUtils.startOfDay(semesterStartDate)
        val today = DateUtils.startOfDay(todayMillis)
        val days = (today - start) / DAY_MILLIS
        return ((days / 7 + 1).coerceAtLeast(1)).toInt()
    }

    /** 当前周开始日期（当天 00:00）；未开学返回 null。 */
    fun weekStart(todayMillis: Long, semesterStartDate: Long?): Long? {
        if (isNotStarted(todayMillis, semesterStartDate)) return null
        val start = DateUtils.startOfDay(semesterStartDate!!)
        return start + (weekNumber(todayMillis, semesterStartDate) - 1) * 7 * DAY_MILLIS
    }

    /** 当前周结束日期（当天 00:00，即本周最后一天）；未开学返回 null。 */
    fun weekEnd(todayMillis: Long, semesterStartDate: Long?): Long? =
        weekStart(todayMillis, semesterStartDate)?.plus(6 * DAY_MILLIS)

    /** 周次 → 单/双周类型。 */
    fun weekType(weekNumber: Int): WeekType =
        if (weekNumber % 2 == 1) WeekType.ODD else WeekType.EVEN

    /**
     * 课程在指定周次是否应显示。
     *
     * 可见条件：
     * 1) 当前周在课程的周次范围内（startWeek..endWeek，闭区间）；
     * 2) 且单双周匹配（EVERY 每周都上；ODD 仅单周；EVEN 仅双周）。
     *
     * 未开学 / 未设置（weekNumber 为 null）时，仅显示「每周」且无周次范围限制的课程。
     */
    fun isActive(
        courseWeekType: WeekType,
        weekNumber: Int?,
        startWeek: Int = 1,
        endWeek: Int = 16
    ): Boolean {
        // 周次范围：当前周必须在 [startWeek, endWeek] 内
        if (weekNumber != null && (weekNumber < startWeek || weekNumber > endWeek)) return false
        return when (courseWeekType) {
            WeekType.EVERY -> true
            WeekType.ODD -> weekNumber != null && weekNumber % 2 == 1
            WeekType.EVEN -> weekNumber != null && weekNumber % 2 == 0
        }
    }
}
