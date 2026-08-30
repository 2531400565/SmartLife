package com.smartlife.app.data.repository

import com.smartlife.app.data.local.dao.FocusSessionDao
import com.smartlife.app.data.local.entity.FocusSessionEntity
import com.smartlife.app.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * 专注记录仓库：向 ViewModel 层暴露响应式数据流。
 */
class FocusSessionRepository(private val dao: FocusSessionDao) {

    /** 全部专注记录（按开始时间倒序）。 */
    val allSessions: Flow<List<FocusSessionEntity>> = dao.observeAll()

    /** 按 id 观察单条记录。 */
    fun observeById(id: Long): Flow<FocusSessionEntity?> = dao.observeById(id)

    /** 今日专注总时长（秒）。 */
    fun todayFocusSeconds(): Flow<Long> =
        dao.observeTodaySeconds(DateUtils.startOfToday(), DateUtils.startOfNextDay())

    /** 今日完整完成的专注轮数。 */
    fun todayCompletedCount(): Flow<Int> =
        dao.observeTodayCompletedCount(DateUtils.startOfToday(), DateUtils.startOfNextDay())

    // ===== 写操作 =====

    /** 新增专注记录，返回自增主键。 */
    suspend fun addSession(session: FocusSessionEntity): Long = dao.insert(session)

    /** 更新专注记录（如中断后回写实际秒数）。 */
    suspend fun updateSession(session: FocusSessionEntity) = dao.update(session)

    /** 删除专注记录。 */
    suspend fun deleteSession(session: FocusSessionEntity) = dao.delete(session)
}
