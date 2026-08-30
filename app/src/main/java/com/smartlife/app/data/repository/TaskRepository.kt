package com.smartlife.app.data.repository

import com.smartlife.app.data.local.dao.TaskDao
import com.smartlife.app.data.local.entity.TaskEntity
import com.smartlife.app.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * 待办仓库：向 ViewModel 层暴露响应式数据流（Room Flow）。
 * 后续 ViewModel 中可用 .stateIn(SharingStarted.WhileSubscribed(5_000), ...) 转为 StateFlow。
 */
class TaskRepository(private val taskDao: TaskDao) {

    /** 全部任务（未完成在前、按截止时间排序）。 */
    val allTasks: Flow<List<TaskEntity>> = taskDao.observeAll()

    /** 未完成任务列表。 */
    val incompleteTasks: Flow<List<TaskEntity>> = taskDao.observeIncomplete()

    /** 已完成任务列表。 */
    val completedTasks: Flow<List<TaskEntity>> = taskDao.observeCompleted()

    /** 未完成总数。 */
    val incompleteCount: Flow<Int> = taskDao.observeIncompleteCount()

    /** 按 id 观察单条任务。 */
    fun observeById(id: Long): Flow<TaskEntity?> = taskDao.observeById(id)

    /** 今日待办数量（未完成且今天到期）。 */
    fun todayTodoCount(): Flow<Int> =
        taskDao.observeTodayCount(DateUtils.startOfToday(), DateUtils.startOfNextDay())

    /** 标题模糊搜索。 */
    fun search(query: String): Flow<List<TaskEntity>> = taskDao.search(query)

    // ===== 写操作 =====

    /** 新增任务，返回自增主键。 */
    suspend fun addTask(task: TaskEntity): Long = taskDao.insert(task)

    /** 更新任务。 */
    suspend fun updateTask(task: TaskEntity) = taskDao.update(task)

    /** 删除任务。 */
    suspend fun deleteTask(task: TaskEntity) = taskDao.delete(task)

    /** 切换完成状态；完成时记录 completedAt，取消完成时置空。 */
    suspend fun toggleComplete(task: TaskEntity) {
        taskDao.update(
            task.copy(
                isCompleted = !task.isCompleted,
                completedAt = if (!task.isCompleted) System.currentTimeMillis() else null
            )
        )
    }
}
