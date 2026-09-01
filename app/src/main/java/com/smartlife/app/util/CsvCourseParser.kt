package com.smartlife.app.util

import com.smartlife.app.data.local.WeekType
import com.smartlife.app.data.local.entity.CourseEntity
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 课程 CSV 解析器（P3）。
 *
 * 支持 UTF-8 文本，字段顺序为：
 * `课程名, 教室, 星期, 周次, 开始时间, 结束时间, 考试日期`
 *
 * 示例：
 * ```
 * 高等数学,教三402,1|3,ODD,08:00,09:35,2026-11-20
 * 大学英语,教五101,2,EVERY,10:00,11:35,
 * ```
 *
 * 规则：
 * - 星期支持多值：`1|3` 表示周一、周三（分隔符支持 `|` `、` `/` 空格）；
 * - 周次支持 `EVERY` / `ODD` / `EVEN`（大小写不敏感，留空视为 EVERY）；
 * - 时间为 `HH:mm`；考试日期为 `yyyy-MM-dd`，可留空；
 * - 自动跳过空行与表头行；
 * - **单行解析失败只跳过该行并记录错误，不影响其它行**。
 *
 * 注意：本类只做解析，不写数据库；写入由 Repository 负责（追加，不删除已有课程）。
 */
object CsvCourseParser {

    /** 期望的最少字段数（考试日期可选）。 */
    private const val MIN_FIELDS = 6

    /** 解析结果。 */
    data class ParseResult(
        /** 成功解析出的课程。 */
        val courses: List<CourseEntity>,
        /** 被跳过的行的错误描述（含行号）。 */
        val errors: List<String>
    )

    /** 解析 CSV 文本。 */
    fun parse(text: String): ParseResult {
        val courses = mutableListOf<CourseEntity>()
        val errors = mutableListOf<String>()

        // 去掉 UTF-8 BOM（Excel 导出的 CSV 常带）
        val content = text.removePrefix("\uFEFF")
        val lines = content.split('\n')

        lines.forEachIndexed { index, rawLine ->
            val lineNo = index + 1
            val line = rawLine.trim()
            if (line.isBlank()) return@forEachIndexed

            val parts = splitCsvLine(line)
            // 首行若是表头则跳过（如「课程名,教室,...」）
            if (lineNo == 1 && looksLikeHeader(parts)) return@forEachIndexed

            if (parts.size < MIN_FIELDS) {
                errors.add("第 $lineNo 行：字段不足 $MIN_FIELDS 个（课程名,教室,星期,周次,开始时间,结束时间[,考试日期]）")
                return@forEachIndexed
            }

            val name = parts[0]
            if (name.isBlank()) {
                errors.add("第 $lineNo 行：课程名为空")
                return@forEachIndexed
            }

            val location = parts[1].ifBlank { null }

            val weekdays = parseWeekdays(parts[2])
            if (weekdays == null) {
                errors.add("第 $lineNo 行：星期格式错误「${parts[2]}」（应为 1~7，多个用 | 分隔）")
                return@forEachIndexed
            }

            val weekType = parseWeekType(parts[3])
            if (weekType == null) {
                errors.add("第 $lineNo 行：周次格式错误「${parts[3]}」（应为 EVERY / ODD / EVEN）")
                return@forEachIndexed
            }

            val startMinute = parseTime(parts[4])
            if (startMinute == null) {
                errors.add("第 $lineNo 行：开始时间格式错误「${parts[4]}」（应为 HH:mm）")
                return@forEachIndexed
            }

            val endMinute = parseTime(parts[5])
            if (endMinute == null) {
                errors.add("第 $lineNo 行：结束时间格式错误「${parts[5]}」（应为 HH:mm）")
                return@forEachIndexed
            }

            if (startMinute >= endMinute) {
                errors.add("第 $lineNo 行：开始时间应早于结束时间")
                return@forEachIndexed
            }

            // 考试日期为可选字段
            val examRaw = parts.getOrNull(6)?.takeIf { it.isNotBlank() }
            val examDate = if (examRaw != null) {
                val parsed = parseDate(examRaw)
                if (parsed == null) {
                    errors.add("第 $lineNo 行：考试日期格式错误「$examRaw」（应为 yyyy-MM-dd）")
                    return@forEachIndexed
                }
                parsed
            } else {
                null
            }

            courses.add(
                CourseEntity(
                    name = name,
                    location = location,
                    teacher = null,
                    weekdays = weekdays,
                    startMinute = startMinute,
                    endMinute = endMinute,
                    examDate = examDate,
                    weekType = weekType
                )
            )
        }

        return ParseResult(courses = courses, errors = errors)
    }

    /** 判断是否为表头行（首列含「课程」字样即视为表头）。 */
    private fun looksLikeHeader(parts: List<String>): Boolean {
        val first = parts.firstOrNull() ?: return false
        return first.contains("课程")
    }

    /**
     * 解析星期集合：`1|3` → {1, 3}；分隔符支持 `|`、`、`、`/`、空格。
     * 任一值不在 1~7 之间即返回 null。
     */
    private fun parseWeekdays(raw: String): Set<Int>? {
        val tokens = raw.split('|', '、', '/', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val result = LinkedHashSet<Int>()
        for (token in tokens) {
            val value = token.toIntOrNull() ?: return null
            if (value !in 1..7) return null
            result.add(value)
        }
        return result
    }

    /** 解析单双周：大小写不敏感，留空视为 EVERY。 */
    private fun parseWeekType(raw: String): WeekType? {
        val value = raw.trim().uppercase(Locale.getDefault())
        if (value.isBlank()) return WeekType.EVERY
        return when (value) {
            "EVERY", "ALL", "每周" -> WeekType.EVERY
            "ODD", "单周" -> WeekType.ODD
            "EVEN", "双周" -> WeekType.EVEN
            else -> null
        }
    }

    /** 解析 `HH:mm` → 当天分钟数（0~1439）；非法返回 null。 */
    private fun parseTime(raw: String): Int? {
        val parts = raw.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** 解析 `yyyy-MM-dd` → 当天 00:00 的时间戳；非法返回 null。 */
    private fun parseDate(raw: String): Long? = runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        format.isLenient = false
        val date = format.parse(raw.trim()) ?: return null
        DateUtils.startOfDay(date.time)
    }.getOrNull()

    /**
     * 按逗号切分一行，支持双引号包裹的字段（字段内可含逗号）与 `""` 转义。
     */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')   // 转义的双引号
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString())
        return result.map { it.trim() }
    }
}
