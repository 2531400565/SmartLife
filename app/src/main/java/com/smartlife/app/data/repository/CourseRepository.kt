package com.smartlife.app.data.repository

import com.smartlife.app.data.local.dao.CourseDao
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.util.DateUtils
import com.smartlife.app.util.WeekUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 课程仓库：向 ViewModel 层暴露响应式数据流。
 * 单双周 / 多星期的过滤在内存层完成（weekdays 为集合字段）。
 */
class CourseRepository(private val courseDao: CourseDao) {

    /** 全部课程（按开始时间排序）。 */
    val allCourses: Flow<List<CourseEntity>> = courseDao.observeAll()

    /** 按 id 观察单门课程。 */
    fun observeById(id: Long): Flow<CourseEntity?> = courseDao.observeById(id)

    /** 某天 + 指定周次下应显示的课程（含单双周过滤），按开始时间升序。 */
    fun coursesForDay(dayOfWeek: Int, weekNumber: Int): Flow<List<CourseEntity>> =
        allCourses.map { list ->
            list.filter { it.weekdays.contains(dayOfWeek) && WeekUtils.isActive(it.weekType, weekNumber) }
                .sortedBy { it.startMinute }
        }

    /** 今日课程数量（当前星期 + 当前单双周）。 */
    fun todayCourseCount(): Flow<Int> =
        allCourses.map { list ->
            val day = DateUtils.todayDayOfWeek()
            val week = WeekUtils.weekNumber(System.currentTimeMillis())
            list.count { it.weekdays.contains(day) && WeekUtils.isActive(it.weekType, week) }
        }

    // ===== 写操作 =====

    /** 新增课程，返回自增主键。 */
    suspend fun addCourse(course: CourseEntity): Long = courseDao.insert(course)

    /** 更新课程。 */
    suspend fun updateCourse(course: CourseEntity) = courseDao.update(course)

    /** 删除课程。 */
    suspend fun deleteCourse(course: CourseEntity) = courseDao.delete(course)
}
