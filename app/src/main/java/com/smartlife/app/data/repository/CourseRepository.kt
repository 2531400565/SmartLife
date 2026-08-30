package com.smartlife.app.data.repository

import com.smartlife.app.data.local.dao.CourseDao
import com.smartlife.app.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

/**
 * 课程仓库：向 ViewModel 层暴露响应式数据流。
 * 单双周 / 多星期的过滤在 ViewModel 内存层完成（weekdays 为集合字段，且周次依赖学期设置）。
 */
class CourseRepository(private val courseDao: CourseDao) {

    /** 全部课程（按开始时间排序）。 */
    val allCourses: Flow<List<CourseEntity>> = courseDao.observeAll()

    /** 按 id 观察单门课程。 */
    fun observeById(id: Long): Flow<CourseEntity?> = courseDao.observeById(id)

    // ===== 写操作 =====

    /** 新增课程，返回自增主键。 */
    suspend fun addCourse(course: CourseEntity): Long = courseDao.insert(course)

    /** 更新课程。 */
    suspend fun updateCourse(course: CourseEntity) = courseDao.update(course)

    /** 删除课程。 */
    suspend fun deleteCourse(course: CourseEntity) = courseDao.delete(course)
}
