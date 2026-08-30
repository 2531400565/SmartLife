package com.smartlife.app.util

import java.util.Calendar
import java.util.TimeZone

/**
 * 日期时间工具（本地时区，全部基于 Unix 毫秒时间戳）。
 * 供 DAO 的"今日"范围查询使用。
 */
object DateUtils {

    /** 某时间戳所在天的 00:00:00（本地时区）。 */
    fun startOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /** 今天 00:00:00。 */
    fun startOfToday(): Long = startOfDay(System.currentTimeMillis())

    /** 明天 00:00:00。配合 startOfToday 形成 [today, tomorrow) 半开区间。 */
    fun startOfNextDay(): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfToday()
            add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    /**
     * 今天是星期几（1=周一 ... 7=周日），与课表 dayOfWeek 约定一致。
     * Calendar.DAY_OF_WEEK：SUNDAY=1 ... SATURDAY=7 → 转换为周一=1 ... 周日=7。
     */
    fun todayDayOfWeek(): Int {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return (day + 5) % 7 + 1
    }

    /** 今日日期文本，如 "2026年8月30日 周日"（首页展示用）。 */
    fun todayText(): String {
        val cal = Calendar.getInstance()
        val weekDays = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        val weekday = weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1] // DAY_OF_WEEK: 1=周日
        return "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月" +
            "${cal.get(Calendar.DAY_OF_MONTH)}日 $weekday"
    }

    /** 截止日期展示文本（仅日期）：今天 / 明天 / X月X日（跨年时带年份）。 */
    fun dueDateText(timestamp: Long): String {
        val now = Calendar.getInstance()
        val due = Calendar.getInstance().apply { timeInMillis = timestamp }
        return when (startOfDay(timestamp)) {
            startOfToday() -> "今天"
            startOfNextDay() -> "明天"
            else -> {
                val yearPrefix =
                    if (due.get(Calendar.YEAR) != now.get(Calendar.YEAR)) "${due.get(Calendar.YEAR)}年" else ""
                "${yearPrefix}${due.get(Calendar.MONTH) + 1}月${due.get(Calendar.DAY_OF_MONTH)}日"
            }
        }
    }

    /**
     * 截止日期时间展示文本（日期 + 时间）：
     * 今天 → "今天 18:30"；明天 → "明天 09:00"；其他 → "9月3日 14:20"。
     */
    fun dueDateTimeText(timestamp: Long): String {
        val due = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hm = "%02d:%02d".format(due.get(Calendar.HOUR_OF_DAY), due.get(Calendar.MINUTE))
        val date = when (startOfDay(timestamp)) {
            startOfToday() -> "今天"
            startOfNextDay() -> "明天"
            else -> {
                val now = Calendar.getInstance()
                val yearPrefix =
                    if (due.get(Calendar.YEAR) != now.get(Calendar.YEAR)) "${due.get(Calendar.YEAR)}年" else ""
                "${yearPrefix}${due.get(Calendar.MONTH) + 1}月${due.get(Calendar.DAY_OF_MONTH)}日"
            }
        }
        return "$date $hm"
    }

    /**
     * 任务是否逾期：未完成 + 有截止时间 + 截止时刻早于「当前时刻」。
     * 精确到时分秒，不再按整天判断。
     */
    fun isOverdue(dueDateTime: Long?, isCompleted: Boolean): Boolean =
        !isCompleted && dueDateTime != null && dueDateTime < System.currentTimeMillis()

    /** 逾期时长展示文本："已逾期 X小时" / "已逾期 X分钟" / "已逾期"。 */
    fun overdueDurationText(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val hours = diff / 3_600_000L
        val minutes = diff / 60_000L
        return when {
            hours >= 1 -> "已逾期 $hours 小时"
            minutes >= 1 -> "已逾期 $minutes 分钟"
            else -> "已逾期"
        }
    }

    /** 取时间戳的当天分钟数（HH:mm → 0~1439）。 */
    fun minutesOfDay(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** 将「某天的日期」与「HH:mm 分钟数」合并为完整时间戳。 */
    fun combineDateAndTime(dateMillis: Long, minutesOfDay: Int): Long {
        val date = Calendar.getInstance().apply { timeInMillis = dateMillis }
        return Calendar.getInstance().apply {
            set(
                date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH),
                minutesOfDay / 60, minutesOfDay % 60, 0
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * 本地"当天零点" → Material3 DatePicker 使用的 UTC 当天零点。
     *
     * epoch 毫秒是全球统一时间轴，不能按"UTC 比本地慢"直觉做减法：
     *   本地零点这一瞬间 = UTC 零点 - 时区偏移
     * 因此要得到"UTC 日历上同一天"的零点，必须 **加上** 时区偏移。
     *
     * 早期实现误用减法，导致编辑任务/课程回显时日期整体偏移一天，
     * 并使"今天截止"的任务被误判为逾期。此方向已由 JVM 校验脚本覆盖。
     */
    fun toUtcStartOfDay(timestamp: Long): Long {
        val localStart = startOfDay(timestamp)
        return localStart + TimeZone.getDefault().getOffset(localStart)
    }

    /** DatePicker 选中的 UTC 日期 → 本地当天 23:59:59（作为任务截止时刻）。 */
    fun fromUtcToLocalEndOfDay(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return Calendar.getInstance().apply {
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** 当天分钟数(0~1439) → "HH:mm"（课程表时间展示）。 */
    fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    /**
     * 考试倒计时文本：
     * 已过期 → null（不显示）；今天 → "今天考试"；明天 → "明天考试"；否则 → "距考试 X 天"。
     */
    fun examCountdownText(examDate: Long): String? {
        val diffDays = ((startOfDay(examDate) - startOfToday()) / 86_400_000L).toInt()
        return when {
            diffDays < 0 -> null
            diffDays == 0 -> "今天考试"
            diffDays == 1 -> "明天考试"
            else -> "距考试 $diffDays 天"
        }
    }
}
