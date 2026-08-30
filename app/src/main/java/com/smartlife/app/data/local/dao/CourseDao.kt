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
 * 课程 DAO：完整 CRUD + 按星期查询。
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

    /** 观察全部课程（按星期、开始时间排序）。 */
    @Query("SELECT * FROM courses ORDER BY dayOfWeek ASC, startMinute ASC")
    fun observeAll(): Flow<List<CourseEntity>>

    // ===== 星期维度查询 =====

    /** 按星期查询课程（dayOfWeek: 1~7，按开始时间升序）。 */
    @Query("SELECT * FROM courses WHERE dayOfWeek = :dayOfWeek ORDER BY startMinute ASC")
    fun observeByDay(dayOfWeek: Int): Flow<List<CourseEntity>>

    /** 指定星期的课程数量（用于"今日课程数量"统计）。 */
    @Query("SELECT COUNT(*) FROM courses WHERE dayOfWeek = :dayOfWeek")
    fun observeCountByDay(dayOfWeek: Int): Flow<Int>
}
