package com.smartlife.app.data.repository

import android.content.Context
import com.smartlife.app.data.local.dao.CourseDao
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.worker.CourseReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 课程仓库：向 ViewModel 层暴露响应式数据流。
 * 单双周 / 多星期的过滤在 ViewModel 内存层完成（weekdays 为集合字段，且周次依赖学期设置）。
 *
 * P1：课程的增删改会自动重排课程提醒（[CourseReminderScheduler]），
 * 因此无论是课表页、当前学期课程页还是 CSV 导入，提醒都能保持同步。
 * [appContext] 为可选参数，仅用于提醒调度，不参与数据存取。
 */
class CourseRepository(
    private val courseDao: CourseDao,
    private val appContext: Context? = null
) {

    /** 全部课程（按开始时间排序）。 */
    val allCourses: Flow<List<CourseEntity>> = courseDao.observeAll()

    /** 按 id 观察单门课程。 */
    fun observeById(id: Long): Flow<CourseEntity?> = courseDao.observeById(id)

    // ===== 写操作 =====

    /** 新增课程，返回自增主键。 */
    suspend fun addCourse(course: CourseEntity): Long {
        val id = courseDao.insert(course)
        rescheduleReminders()
        return id
    }

    /** 更新课程。 */
    suspend fun updateCourse(course: CourseEntity) {
        courseDao.update(course)
        rescheduleReminders()
    }

    /** 删除课程；同时取消该课程已排程的提醒（删除后重排无法覆盖到它）。 */
    suspend fun deleteCourse(course: CourseEntity) {
        courseDao.delete(course)
        appContext?.let { CourseReminderScheduler.cancelForCourse(it, course.id) }
        rescheduleReminders()
    }

    /** 批量追加课程（CSV 导入使用），返回新增数量。 */
    suspend fun addCourses(courses: List<CourseEntity>): Int {
        var count = 0
        courses.forEach { course ->
            courseDao.insert(course)
            count++
        }
        rescheduleReminders()
        return count
    }

    /**
     * 依据最新课程数据重排提醒。
     * 失败不影响数据操作本身（提醒属于辅助能力）。
     */
    private suspend fun rescheduleReminders() {
        val context = appContext ?: return
        runCatching {
            val courses = courseDao.observeAll().first()
            CourseReminderScheduler.rescheduleAll(context, courses)
        }
    }
}
