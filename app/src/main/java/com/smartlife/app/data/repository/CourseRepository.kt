package com.smartlife.app.data.repository

import com.smartlife.app.data.local.dao.CourseDao
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * 课程仓库：向 ViewModel 层暴露响应式数据流。
 */
class CourseRepository(private val courseDao: CourseDao) {

    /** 全部课程（按星期、开始时间排序）。 */
    val allCourses: Flow<List<CourseEntity>> = courseDao.observeAll()

    /** 按 id 观察单门课程。 */
    fun observeById(id: Long): Flow<CourseEntity?> = courseDao.observeById(id)

    /** 按星期查询课程（dayOfWeek: 1~7）。 */
    fun coursesByDay(dayOfWeek: Int): Flow<List<CourseEntity>> =
        courseDao.observeByDay(dayOfWeek)

    /** 今日课程数量（按系统当前星期）。 */
    fun todayCourseCount(): Flow<Int> =
        courseDao.observeCountByDay(DateUtils.todayDayOfWeek())

    // ===== 写操作 =====

    /** 新增课程，返回自增主键。 */
    suspend fun addCourse(course: CourseEntity): Long = courseDao.insert(course)

    /** 更新课程。 */
    suspend fun updateCourse(course: CourseEntity) = courseDao.update(course)

    /** 删除课程。 */
    suspend fun deleteCourse(course: CourseEntity) = courseDao.delete(course)
}
