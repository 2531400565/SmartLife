package com.smartlife.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 励志语实体。对应「内置 20 条中文励志语」决策。
 * Phase 0 仅定义数据模型并接入 Room；种子数据写入与 DAO 在后续模块实现。
 */
@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "author") val author: String? = null
)
