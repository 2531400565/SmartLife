package com.smartlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartlife.app.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

/**
 * 课程 DAO：完整 CRUD + 全量观察。
 * 单双周 / 多星期的过滤在 Repository / ViewModel 内存层完成（集合字段无法用 SQL 精确匹配）。
 */
@Dao
interface CourseDao {

    // ===== CRUD =====

    /** 新增；返回自增主键。冲突时整体替换。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity): Long

    /** 按主键更新。 */
    @Update
    suspend fun update(course: CourseEntity)

    /** 按实体删除。 */
    @Delete
    suspend fun delete(course: CourseEntity)

    /** 按 id 观察单条。 */
    @Query("SELECT * FROM courses WHERE id = :id")
    fun observeById(id: Long): Flow<CourseEntity?>

    /** 观察全部课程（按开始时间升序；星期/单双周过滤在内存层完成）。 */
    @Query("SELECT * FROM courses ORDER BY startMinute ASC")
    fun observeAll(): Flow<List<CourseEntity>>
}
