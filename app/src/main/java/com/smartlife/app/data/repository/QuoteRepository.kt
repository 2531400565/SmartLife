package com.smartlife.app.data.repository

import com.smartlife.app.data.local.dao.QuoteDao
import com.smartlife.app.data.local.entity.QuoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 励志语仓库：向 ViewModel 层暴露响应式数据流。
 */
class QuoteRepository(private val quoteDao: QuoteDao) {

    /** 全部励志语。 */
    val allQuotes: Flow<List<QuoteEntity>> = quoteDao.observeAll()

    /** 随机取一条（首页随机励志语展示用）。 */
    suspend fun getRandomQuote(): QuoteEntity? = quoteDao.getRandom()

    /** 励志语总条数。 */
    suspend fun count(): Int = quoteDao.count()
}
