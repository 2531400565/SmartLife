package com.smartlife.app.data.local

/**
 * 待办优先级（低 / 中 / 高）。
 * Room 2.3+ 自动将枚举存储为字符串（枚举名），无需 TypeConverter。
 */
enum class Priority(val label: String) {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高")
}
