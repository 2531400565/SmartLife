package com.smartlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.data.local.entity.FocusSessionEntity
import com.smartlife.app.data.local.entity.QuoteEntity
import com.smartlife.app.data.local.entity.TaskEntity

/**
 * 备份专用 DAO：供 JSON 导入在【单个 SQLite 事务】内执行「清空 + 批量插入」。
 * 仅操作既有四张表，不新增任何表/列（数据库 version 不变，无需 Migration）。
 * 说明：导入必须原子化（失败整体回滚），现有各业务 DAO 没有"清空全部/批量插入"能力，
 * 故新增本接口——不修改任何既有 DAO / Repository 的公共接口。
 */
@Dao
interface BackupDao {

    // ===== 清空 =====

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM courses")
    suspend fun clearCourses()

    @Query("DELETE FROM focus_sessions")
    suspend fun clearFocusSessions()

    @Query("DELETE FROM quotes")
    suspend fun clearQuotes()

    // ===== 批量插入（主键冲突按 id 覆盖，保留备份中的 id）=====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSessions(sessions: List<FocusSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotes(quotes: List<QuoteEntity>)
}
