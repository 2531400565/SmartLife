package com.smartlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smartlife.app.data.local.Priority

/**
 * 待办事项实体（表：tasks）。
 * 所有时间字段均为 Unix 毫秒时间戳；dueDateTime/completedAt 可为空。
 */
@Entity(
    tableName = "tasks",
    indices = [Index("isCompleted"), Index("dueDateTime"), Index("createdAt")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,                        // 标题（必填）
    val description: String? = null,          // 描述（可选）
    val priority: Priority = Priority.MEDIUM, // 优先级
    val dueDateTime: Long? = null,            // 截止时间戳（日期+时间，可选）
    val isCompleted: Boolean = false,         // 是否已完成
    val createdAt: Long = System.currentTimeMillis(), // 创建时间戳
    val completedAt: Long? = null             // 完成时间戳（未完成时为 null）
)
