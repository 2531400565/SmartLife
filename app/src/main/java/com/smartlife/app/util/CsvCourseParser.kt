package com.smartlife.app.util

import com.smartlife.app.data.local.CourseType
import com.smartlife.app.data.local.WeekType
import com.smartlife.app.data.local.entity.CourseEntity
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 课程 CSV 解析器（P3 初版，v2.2 增强）。
 *
 * 支持 UTF-8 文本，两种格式（按字段数自动识别）：
 *
 * **完整版（推荐，10~11 列，v2.2）**
 * `课程名,教室,老师,星期,周次,周次范围,课程性质,开始时间,结束时间[,考试日期]`
 *
 * ```
 * 软件体系结构,大数据实训中心 3-505,周久源,1,每周,1-16,考试课,08:00,09:35,2026-12-20
 * Linux 云计算,云计算实训平台 3-405,张晨,2,双周,1-15,考查课,14:00,15:35,
 * 商务数据分析,大学外语研楼 605,李雪兰,2,每周,3-16,考查课,10:00,11:35,
 * ```
 *
 * **简版（兼容旧文件，6~7 列）**
 * `课程名,教室,星期,周次,开始时间,结束时间[,考试日期]`
 * ```
 * 高等数学,教三402,1|3,ODD,08:00,09:35,2026-11-20
 * ```
 * 简版等价于：老师留空、周次范围 1-16、课程性质未知。
 *
 * 字段规则：
 * - 星期：1~7 整数，多值用 `|` `、` `/` 空格分隔（如 `1|3` 表示周一、周三）；
 * - 周次：`EVERY/ALL/每周`、`ODD/单周`、`EVEN/双周`（大小写不敏感，留空视为 EVERY）；
 * - 周次范围：`起始-结束`，如 `1-16` / `11-16` / `3-16` / `1-15`（1~30 内，start ≤ end，留空默认 1-16）；
 * - 课程性质：`EXAM/考试/考试课` → 考试课；`ASSESSMENT/考查/考查课` → 考查课；留空或其它 → 未知；
 * - 时间为 `HH:mm`；考试日期为 `yyyy-MM-dd`，可留空；
 * - 自动跳过空行与表头行；
 * - **单行解析失败只跳过该行并记录错误，不影响其它行**。
 *
 * 注意：本类只做解析，不写数据库；写入由 Repository 负责（追加，不删除已有课程）。
 */
object CsvCourseParser {

    /** 简版最少字段数。 */
    private const val LEGACY_MIN_FIELDS = 6

    /** 完整版最少字段数（考试日期可选）。 */
    private const val FULL_MIN_FIELDS = 10

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
            // 首行若是表头则跳过（如「课程名,…」）
            if (lineNo == 1 && looksLikeHeader(parts)) return@forEachIndexed

            when {
                parts.size >= FULL_MIN_FIELDS -> parseFull(parts, lineNo, courses, errors)
                parts.size >= LEGACY_MIN_FIELDS -> parseLegacy(parts, lineNo, courses, errors)
                else -> errors.add(
                    "第 $lineNo 行：字段数 ${parts.size} 无法识别。" +
                        "简版需 6~7 列（课程名,教室,星期,周次,开始,结束[,考试日期]）；" +
                        "完整版需 10~11 列（课程名,教室,老师,星期,周次,周次范围,课程性质,开始,结束[,考试日期]）"
                )
            }
        }

        return ParseResult(courses = courses, errors = errors)
    }

    // ===== 完整版（10~11 列，v2.2）=====

    private fun parseFull(
        parts: List<String>,
        lineNo: Int,
        courses: MutableList<CourseEntity>,
        errors: MutableList<String>
    ) {
        val name = parts[0]
        if (name.isBlank()) {
            errors.add("第 $lineNo 行：课程名为空")
            return
        }
        val location = parts[1].ifBlank { null }
        val teacher = parts[2].ifBlank { null }

        val weekdays = parseWeekdays(parts[3])
        if (weekdays == null) {
            errors.add("第 $lineNo 行：星期格式错误「${parts[3]}」（应为 1~7，多个用 | 分隔）")
            return
        }
        val weekType = parseWeekType(parts[4])
        if (weekType == null) {
            errors.add("第 $lineNo 行：周次格式错误「${parts[4]}」（应为 EVERY/ODD/EVEN 或 每周/单周/双周）")
            return
        }
        val (startWeek, endWeek) = parseWeekRange(parts[5])
            ?: run {
                errors.add("第 $lineNo 行：周次范围格式错误「${parts[5]}」（应为 起始-结束，如 1-16）")
                return
            }
        val courseType = parseCourseType(parts[6])

        val startMinute = parseTime(parts[7])
        if (startMinute == null) {
            errors.add("第 $lineNo 行：开始时间格式错误「${parts[7]}」（应为 HH:mm）")
            return
        }
        val endMinute = parseTime(parts[8])
        if (endMinute == null) {
            errors.add("第 $lineNo 行：结束时间格式错误「${parts[8]}」（应为 HH:mm）")
            return
        }
        if (startMinute >= endMinute) {
            errors.add("第 $lineNo 行：开始时间应早于结束时间")
            return
        }
        // 考试日期为可选字段
        val examRaw = parts.getOrNull(9)?.takeIf { it.isNotBlank() }
        val examDate = if (examRaw != null) {
            val parsed = parseDate(examRaw)
            if (parsed == null) {
                errors.add("第 $lineNo 行：考试日期格式错误「$examRaw」（应为 yyyy-MM-dd）")
                return
            }
            parsed
        } else {
            null
        }

        courses.add(
            CourseEntity(
                name = name,
                location = location,
                teacher = teacher,
                weekdays = weekdays,
                startMinute = startMinute,
                endMinute = endMinute,
                examDate = examDate,
                weekType = weekType,
                startWeek = startWeek,
                endWeek = endWeek,
                courseType = courseType
            )
        )
    }

    // ===== 简版（6~7 列，P3 兼容）=====

    private fun parseLegacy(
        parts: List<String>,
        lineNo: Int,
        courses: MutableList<CourseEntity>,
        errors: MutableList<String>
    ) {
        val name = parts[0]
        if (name.isBlank()) {
            errors.add("第 $lineNo 行：课程名为空")
            return
        }
        val location = parts[1].ifBlank { null }

        val weekdays = parseWeekdays(parts[2])
        if (weekdays == null) {
            errors.add("第 $lineNo 行：星期格式错误「${parts[2]}」（应为 1~7，多个用 | 分隔）")
            return
        }
        val weekType = parseWeekType(parts[3])
        if (weekType == null) {
            errors.add("第 $lineNo 行：周次格式错误「${parts[3]}」（应为 EVERY/ODD/EVEN 或 每周/单周/双周）")
            return
        }
        val startMinute = parseTime(parts[4])
        if (startMinute == null) {
            errors.add("第 $lineNo 行：开始时间格式错误「${parts[4]}」（应为 HH:mm）")
            return
        }
        val endMinute = parseTime(parts[5])
        if (endMinute == null) {
            errors.add("第 $lineNo 行：结束时间格式错误「${parts[5]}」（应为 HH:mm）")
            return
        }
        if (startMinute >= endMinute) {
            errors.add("第 $lineNo 行：开始时间应早于结束时间")
            return
        }
        // 考试日期为可选字段
        val examRaw = parts.getOrNull(6)?.takeIf { it.isNotBlank() }
        val examDate = if (examRaw != null) {
            val parsed = parseDate(examRaw)
            if (parsed == null) {
                errors.add("第 $lineNo 行：考试日期格式错误「$examRaw」（应为 yyyy-MM-dd）")
                return
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
                weekType = weekType,
                startWeek = 1,
                endWeek = 16,
                courseType = CourseType.UNKNOWN
            )
        )
    }

    // ===== 通用解析 =====

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

    /**
     * 解析周次范围：`1-16` / `11-16` / `1-15`（容忍前后括号与"周"字）。
     * 要求 1 ≤ start ≤ end ≤ 30，否则返回 null。
     */
    private fun parseWeekRange(raw: String): Pair<Int, Int>? {
        val cleaned = raw.replace(Regex("[^0-9\\-]"), "")
        val parts = cleaned.split("-")
        if (parts.size != 2) return null
        val start = parts[0].toIntOrNull() ?: return null
        val end = parts[1].toIntOrNull() ?: return null
        if (start < 1 || end > 30 || start > end) return null
        return start to end
    }

    /** 解析课程性质：中文 / 英文枚举，未识别一律 UNKNOWN。 */
    private fun parseCourseType(raw: String): CourseType = when (raw.trim().uppercase(Locale.getDefault())) {
        "EXAM", "考试", "考试课" -> CourseType.EXAM
        "ASSESSMENT", "考查", "考查课" -> CourseType.ASSESSMENT
        else -> CourseType.UNKNOWN
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
