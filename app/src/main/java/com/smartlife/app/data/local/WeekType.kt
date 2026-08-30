package com.smartlife.app.data.local

/**
 * 课程单双周类型。
 * - [EVERY]：每周都上
 * - [ODD]：仅单周（第 1、3、5…周）
 * - [EVEN]：仅双周（第 2、4、6…周）
 */
enum class WeekType(val label: String) {
    EVERY("每周"),
    ODD("单周"),
    EVEN("双周")
}
