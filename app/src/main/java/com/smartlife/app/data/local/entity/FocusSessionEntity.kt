package com.smartlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 专注（番茄钟）记录实体（表：focus_sessions）。
 * startedAt：开始时间戳；plannedMinutes：计划专注分钟数；
 * actualSeconds：实际专注秒数（可能小于计划，用于中断统计）；
 * completed：是否完整完成一轮。
 */
@Entity(
    tableName = "focus_sessions",
    indices = [Index("startedAt")]
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startedAt: Long,           // 开始时间戳
    val plannedMinutes: Int,       // 计划分钟数
    val actualSeconds: Long,       // 实际专注秒数
    val completed: Boolean = false // 是否完成整轮
)
