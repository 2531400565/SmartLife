package com.smartlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartlife.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * 待办事项 DAO：完整 CRUD + 常用查询。
 * 查询统一返回 Flow，实现数据库级响应式更新（MVVM 数据流基石）。
 */
@Dao
interface TaskDao {

    // ===== CRUD =====

    /** 新增；返回自增主键。冲突时整体替换（含更新语义）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    /** 按主键更新。 */
    @Update
    suspend fun update(task: TaskEntity)

    /** 按实体删除。 */
    @Delete
    suspend fun delete(task: TaskEntity)

    /** 按 id 观察单条。 */
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    /** 观察全部任务（未完成在前、按截止时间升序、再按创建时间倒序）。 */
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueDate ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    // ===== 列表 / 状态查询 =====

    /** 未完成任务列表（按截止时间升序）。 */
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY dueDate ASC")
    fun observeIncomplete(): Flow<List<TaskEntity>>

    /** 已完成任务列表（按完成时间倒序）。 */
    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<TaskEntity>>

    /** 搜索：标题模糊匹配。 */
    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<TaskEntity>>

    /** 今日待办数量：未完成且 today 00:00 <= dueDate < 次日 00:00。 */
    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0 AND dueDate >= :startOfDay AND dueDate < :startOfNextDay")
    fun observeTodayCount(startOfDay: Long, startOfNextDay: Long): Flow<Int>

    /** 未完成总数（首页角标等用途）。 */
    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0")
    fun observeIncompleteCount(): Flow<Int>
}
