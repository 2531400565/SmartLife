package com.smartlife.app.data.local

/**
 * 课程性质类型。
 * - [EXAM]：考试课
 * - [ASSESSMENT]：考查课
 * - [UNKNOWN]：未知（默认，旧数据或未设置）
 */
enum class CourseType(val label: String) {
    EXAM("考试课"),
    ASSESSMENT("考查课"),
    UNKNOWN("未知")
}
