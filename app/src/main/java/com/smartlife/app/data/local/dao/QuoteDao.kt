package com.smartlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartlife.app.data.local.entity.QuoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 励志语 DAO。
 * 完整 CRUD 中：读为 observeAll / getRandom / count，
 * 写为 insertAll（内置种子数据，只增不覆盖）。
 */
@Dao
interface QuoteDao {

    /** 批量写入（用于首次启动种子数据；主键冲突时忽略，避免重复入库）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(quotes: List<QuoteEntity>)

    /** 观察全部励志语。 */
    @Query("SELECT * FROM quotes")
    fun observeAll(): Flow<List<QuoteEntity>>

    /** 随机取一条（首页随机励志语展示用）。 */
    @Query("SELECT * FROM quotes ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandom(): QuoteEntity?

    /** 励志语总条数。 */
    @Query("SELECT COUNT(*) FROM quotes")
    suspend fun count(): Int
}
