package com.smartlife.app.util

/**
 * 单个节次的时间段（第 N 节的起止分钟，0~1439）。
 *
 * 用途（v2.2）：课程录入的「节次快捷填充」与 CSV 导入换算的参考时刻表，
 * 仅存于 DataStore，**不进入 Room、不改变课程结构** —— 课程仍存储绝对
 * startMinute / endMinute，修改时刻表只影响之后的快捷填充与导入换算，
 * 不回写已保存课程。
 */
data class CoursePeriod(
    val startMinute: Int,   // 开始（当天分钟数）
    val endMinute: Int      // 结束（当天分钟数）
)

/**
 * 默认节次时刻表（第 1~10 节）。
 * 大节间（2-3 / 4-5 / 6-7 / 8-9）休息 20 分钟，小节间休息 10 分钟。
 */
val DEFAULT_COURSE_PERIODS: List<CoursePeriod> = listOf(
    CoursePeriod(8 * 60, 8 * 60 + 45),         // 第 1 节 08:00-08:45
    CoursePeriod(8 * 60 + 55, 9 * 60 + 40),    // 第 2 节 08:55-09:40
    CoursePeriod(10 * 60, 10 * 60 + 45),       // 第 3 节 10:00-10:45
    CoursePeriod(10 * 60 + 55, 11 * 60 + 40),  // 第 4 节 10:55-11:40
    CoursePeriod(14 * 60, 14 * 60 + 45),       // 第 5 节 14:00-14:45
    CoursePeriod(14 * 60 + 55, 15 * 60 + 40),  // 第 6 节 14:55-15:40
    CoursePeriod(16 * 60, 16 * 60 + 45),       // 第 7 节 16:00-16:45
    CoursePeriod(16 * 60 + 55, 17 * 60 + 40),  // 第 8 节 16:55-17:40
    CoursePeriod(19 * 60, 19 * 60 + 45),       // 第 9 节 19:00-19:45
    CoursePeriod(19 * 60 + 55, 20 * 60 + 40)   // 第 10 节 19:55-20:40
)

/**
 * 节次时刻表工具（纯函数）。
 */
object CoursePeriods {

    /** 常用节次段（每段 2 节）：1-2 / 3-4 / 5-6 / 7-8 / 9-10。 */
    val DEFAULT_SLOTS: List<Pair<Int, Int>> =
        listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 10)

    /** 节次段标签：如 (1,2) → "第1-2节"；(5,5) → "第5节"。 */
    fun slotLabel(from: Int, to: Int): String =
        if (from == to) "第${from}节" else "第${from}-${to}节"

    /**
     * 节次段 → 起止时间：起始 = 第 from 节开始，结束 = 第 to 节结束。
     * 越界（from/to 超出 periods 范围）返回 null。
     */
    fun slotTime(from: Int, to: Int, periods: List<CoursePeriod>): CoursePeriod? {
        val start = periods.getOrNull(from - 1) ?: return null
        val end = periods.getOrNull(to - 1) ?: return null
        return CoursePeriod(start.startMinute, end.endMinute)
    }

    /**
     * 课程时间 → 所在节次段下标（按 [DEFAULT_SLOTS] 顺序）。
     * 优先精确匹配（开始=段首节开始且结束=段尾节结束）；
     * 否则取「包含课程开始时间的段」；都没有则取起始分钟最接近的段。
     */
    fun slotIndexOf(
        startMinute: Int,
        endMinute: Int,
        periods: List<CoursePeriod>
    ): Int {
        DEFAULT_SLOTS.forEachIndexed { index, (from, to) ->
            val slot = slotTime(from, to, periods) ?: return@forEachIndexed
            if (slot.startMinute == startMinute && slot.endMinute == endMinute) return index
        }
        var best = 0
        var bestDistance = Int.MAX_VALUE
        DEFAULT_SLOTS.forEachIndexed { index, (from, to) ->
            val slot = slotTime(from, to, periods) ?: return@forEachIndexed
            if (startMinute in slot.startMinute until slot.endMinute) return index
            val distance = kotlin.math.abs(slot.startMinute - startMinute)
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    /** 序列化：`08:00-08:45,08:55-09:40,…`。 */
    fun encode(periods: List<CoursePeriod>): String =
        periods.joinToString(",") { "${formatMinute(it.startMinute)}-${formatMinute(it.endMinute)}" }

    /**
     * 反序列化；任意一段非法返回 null（调用方应回退默认值）。
     * 仅接受「成对出现」的节次表；实际允许任意数量（>=1）。
     */
    fun decode(raw: String?): List<CoursePeriod>? {
        if (raw.isNullOrBlank()) return null
        val result = mutableListOf<CoursePeriod>()
        val tokens = raw.split(",")
        if (tokens.isEmpty()) return null
        for (token in tokens) {
            val parts = token.split("-")
            if (parts.size != 2) return null
            val start = parseMinute(parts[0]) ?: return null
            val end = parseMinute(parts[1]) ?: return null
            if (start < 0 || end > 1439 || start >= end) return null
            result.add(CoursePeriod(start, end))
        }
        return result
    }

    /** 分钟 → "HH:mm"。 */
    fun formatMinute(minute: Int): String {
        val m = minute.coerceIn(0, 1439)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** "HH:mm" → 分钟数；非法返回 null。 */
    private fun parseMinute(raw: String): Int? {
        val parts = raw.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
}
