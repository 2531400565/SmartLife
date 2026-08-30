package com.smartlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartlife.app.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 专注记录 DAO：完整 CRUD + 今日统计。
 */
@Dao
interface FocusSessionDao {

    // ===== CRUD =====

    /** 新增；返回自增主键。冲突时整体替换。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity): Long

    /** 按主键更新。 */
    @Update
    suspend fun update(session: FocusSessionEntity)

    /** 按实体删除。 */
    @Delete
    suspend fun delete(session: FocusSessionEntity)

    /** 按 id 观察单条。 */
    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<FocusSessionEntity?>

    /** 观察全部记录（按开始时间倒序）。 */
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    // ===== 今日统计 =====

    /** 今日专注总时长（秒）；无记录时 COALESCE 归零。 */
    @Query("SELECT COALESCE(SUM(actualSeconds), 0) FROM focus_sessions WHERE startedAt >= :startOfDay AND startedAt < :startOfNextDay")
    fun observeTodaySeconds(startOfDay: Long, startOfNextDay: Long): Flow<Long>

    /** 今日完整完成的专注轮数。 */
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE startedAt >= :startOfDay AND startedAt < :startOfNextDay AND completed = 1")
    fun observeTodayCompletedCount(startOfDay: Long, startOfNextDay: Long): Flow<Int>
}
