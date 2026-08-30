package com.smartlife.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartlife.app.data.QuotesProvider
import com.smartlife.app.data.local.dao.BackupDao
import com.smartlife.app.data.local.dao.CourseDao
import com.smartlife.app.data.local.dao.FocusSessionDao
import com.smartlife.app.data.local.dao.QuoteDao
import com.smartlife.app.data.local.dao.TaskDao
import com.smartlife.app.data.local.entity.CourseEntity
import com.smartlife.app.data.local.entity.FocusSessionEntity
import com.smartlife.app.data.local.entity.QuoteEntity
import com.smartlife.app.data.local.entity.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * SmartLife 应用数据库（单例）。
 * v1：tasks / courses / focus_sessions / quotes 四张表。
 * v2：courses 表新增 teacher 列（可空），见 [MIGRATION_1_2]。
 * 首次建库时自动写入 20 条内置励志语（见 [SeedCallback]）。
 */
@Database(
    entities = [
        TaskEntity::class,
        CourseEntity::class,
        FocusSessionEntity::class,
        QuoteEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun courseDao(): CourseDao

    abstract fun focusSessionDao(): FocusSessionDao

    abstract fun quoteDao(): QuoteDao

    /**
     * 备份 DAO（JSON 导入专用，事务内清空+批量插入）。
     * 仅操作既有表，无 schema 变化，version 不变。
     */
    abstract fun backupDao(): BackupDao

    companion object {
        const val DATABASE_NAME = "smartlife_db"

        /**
         * v1 → v2：为 courses 表新增 teacher 列。
         * 可空列（TEXT），旧数据自动补 NULL，不会丢失任何已有课程字段。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN teacher TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 获取数据库单例（双检锁，线程安全）。 */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(SeedCallback)
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }

        /**
         * 首次建库回调：写入内置励志语种子数据。
         * Room 的 onCreate 在首次打开数据库时触发，此时 INSTANCE 已赋值，可安全取 DAO。
         */
        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val quotes = QuotesProvider.quotes.map { QuoteEntity(text = it) }
                    INSTANCE?.quoteDao()?.insertAll(quotes)
                }
            }
        }
    }
}
