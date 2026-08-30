package com.smartlife.app.util

import com.smartlife.app.data.local.Priority
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.data.local.entity.FocusSessionEntity
import com.smartlife.app.data.local.entity.QuoteEntity
import com.smartlife.app.data.local.entity.TaskEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 一次性备份数据（四类实体的内存集合）。
 */
data class BackupData(
    val tasks: List<TaskEntity>,
    val courses: List<CourseEntity>,
    val sessions: List<FocusSessionEntity>,
    val quotes: List<QuoteEntity>
)

/**
 * JSON 备份工具：负责 Task / Course / FocusSession / Quote 的序列化与反序列化。
 * 使用 Android 内置 org.json，无第三方依赖、完全离线。
 *
 * 格式：
 * {
 *   "version": 1,
 *   "exportedAt": 1725000000000,
 *   "app": "SmartLife",
 *   "data": {
 *     "tasks":         [ { "id":1, "title":"...", "priority":"MEDIUM", ... } ],
 *     "courses":       [ { "id":1, "name":"...", "teacher":null, ... } ],
 *     "focusSessions": [ { "id":1, "startedAt":..., "plannedMinutes":25, ... } ],
 *     "quotes":        [ { "id":1, "text":"...", "author":null } ]
 *   }
 * }
 */
object JsonBackup {

    private const val FORMAT_VERSION = 1

    /** 导出：四类数据 → 格式化 JSON 字符串。 */
    fun export(
        tasks: List<TaskEntity>,
        courses: List<CourseEntity>,
        sessions: List<FocusSessionEntity>,
        quotes: List<QuoteEntity>
    ): String {
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("app", "SmartLife")

        val data = JSONObject()
        data.put("tasks", JSONArray().apply { tasks.forEach { put(it.toJson()) } })
        data.put("courses", JSONArray().apply { courses.forEach { put(it.toJson()) } })
        data.put("focusSessions", JSONArray().apply { sessions.forEach { put(it.toJson()) } })
        data.put("quotes", JSONArray().apply { quotes.forEach { put(it.toJson()) } })
        root.put("data", data)

        return root.toString(2)
    }

    /**
     * 导入：解析并严格校验 JSON。
     * 任何格式问题抛 [IllegalArgumentException]（调用方负责回滚与提示），不会产生半套数据。
     */
    fun parse(json: String): BackupData {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            throw IllegalArgumentException("不是有效的 JSON 文件")
        }
        if (root.optInt("version", -1) != FORMAT_VERSION) {
            throw IllegalArgumentException("不支持的备份版本（需要 version = $FORMAT_VERSION）")
        }
        val data = root.optJSONObject("data")
            ?: throw IllegalArgumentException("缺少 data 字段，文件已损坏")

        val tasks = parseTasks(data.optJSONArray("tasks") ?: JSONArray())
        val courses = parseCourses(data.optJSONArray("courses") ?: JSONArray())
        val sessions = parseSessions(data.optJSONArray("focusSessions") ?: JSONArray())
        val quotes = parseQuotes(data.optJSONArray("quotes") ?: JSONArray())

        return BackupData(tasks, courses, sessions, quotes)
    }

    // ===== 序列化 =====

    private fun TaskEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description ?: JSONObject.NULL)
        put("priority", priority.name)
        put("dueDate", dueDate ?: JSONObject.NULL)
        put("isCompleted", isCompleted)
        put("createdAt", createdAt)
        put("completedAt", completedAt ?: JSONObject.NULL)
    }

    private fun CourseEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("location", location ?: JSONObject.NULL)
        put("teacher", teacher ?: JSONObject.NULL)
        put("dayOfWeek", dayOfWeek)
        put("startMinute", startMinute)
        put("endMinute", endMinute)
        put("examDate", examDate ?: JSONObject.NULL)
    }

    private fun FocusSessionEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("startedAt", startedAt)
        put("plannedMinutes", plannedMinutes)
        put("actualSeconds", actualSeconds)
        put("completed", completed)
    }

    private fun QuoteEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("author", author ?: JSONObject.NULL)
    }

    // ===== 反序列化 + 校验 =====

    private fun parseTasks(array: JSONArray): List<TaskEntity> {
        val list = ArrayList<TaskEntity>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val title = o.optString("title", "").trim()
            if (title.isEmpty()) throw IllegalArgumentException("第 $i 条任务缺少标题")
            list += TaskEntity(
                id = o.optLong("id", 0L),
                title = title,
                description = if (o.isNull("description")) null else o.optString("description"),
                priority = runCatching { Priority.valueOf(o.optString("priority", "MEDIUM")) }
                    .getOrDefault(Priority.MEDIUM),
                dueDate = if (o.isNull("dueDate")) null else o.optLong("dueDate"),
                isCompleted = o.optBoolean("isCompleted", false),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt")
            )
        }
        return list
    }

    private fun parseCourses(array: JSONArray): List<CourseEntity> {
        val list = ArrayList<CourseEntity>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val name = o.optString("name", "").trim()
            if (name.isEmpty()) throw IllegalArgumentException("第 $i 条课程缺少名称")
            list += CourseEntity(
                id = o.optLong("id", 0L),
                name = name,
                location = if (o.isNull("location")) null else o.optString("location"),
                teacher = if (o.isNull("teacher")) null else o.optString("teacher"),
                dayOfWeek = o.optInt("dayOfWeek", 1).coerceIn(1, 7),
                startMinute = o.optInt("startMinute", 0).coerceIn(0, 1439),
                endMinute = o.optInt("endMinute", 0).coerceIn(0, 1439),
                examDate = if (o.isNull("examDate")) null else o.optLong("examDate")
            )
        }
        return list
    }

    private fun parseSessions(array: JSONArray): List<FocusSessionEntity> {
        val list = ArrayList<FocusSessionEntity>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list += FocusSessionEntity(
                id = o.optLong("id", 0L),
                startedAt = o.optLong("startedAt", 0L),
                plannedMinutes = o.optInt("plannedMinutes", 25),
                actualSeconds = o.optLong("actualSeconds", 0L),
                completed = o.optBoolean("completed", false)
            )
        }
        return list
    }

    private fun parseQuotes(array: JSONArray): List<QuoteEntity> {
        val list = ArrayList<QuoteEntity>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val text = o.optString("text", "").trim()
            if (text.isEmpty()) throw IllegalArgumentException("第 $i 条励志语缺少文本")
            list += QuoteEntity(
                id = o.optLong("id", 0L),
                text = text,
                author = if (o.isNull("author")) null else o.optString("author")
            )
        }
        return list
    }
}
