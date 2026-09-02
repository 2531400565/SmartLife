package com.smartlife.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
 * v3：tasks.dueDate → dueDateTime；courses.dayOfWeek → weekdays(Set) + 新增 weekType，见 [MIGRATION_2_3]。
 * v4：courses 表新增 startWeek / endWeek / courseType 三列，见 [MIGRATION_3_4]。
 * 首次建库时自动写入 20 条内置励志语（见 [SeedCallback]）。
 */
@Database(
    entities = [
        TaskEntity::class,
        CourseEntity::class,
        FocusSessionEntity::class,
        QuoteEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
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

        /**
         * v2 → v3：
         * 1) tasks 表：dueDate 改名 dueDateTime（旧值本就是"当天 23:59:59"，
         *    即"仅日期 + 默认 23:59"，直接保留原值即可，无数据丢失）。
         * 2) courses 表：dayOfWeek(单值) → weekdays(TEXT 集合)，新增 weekType(TEXT 默认 EVERY)。
         *
         * 说明：minSdk 24 的 SQLite 不支持 ALTER TABLE RENAME/DROP COLUMN，
         * 故采用「新建表 → 拷贝数据 → 删旧表 → 重命名 → 重建索引」的安全重建方式，
         * 全程保留已有数据，不要求用户清数据。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ===== tasks：dueDate → dueDateTime =====
                db.execSQL(
                    """
                    CREATE TABLE `tasks_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `priority` TEXT NOT NULL,
                        `dueDateTime` INTEGER,
                        `isCompleted` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `completedAt` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `tasks_new` (`id`, `title`, `description`, `priority`, `dueDateTime`, `isCompleted`, `createdAt`, `completedAt`)
                    SELECT `id`, `title`, `description`, `priority`, `dueDate`, `isCompleted`, `createdAt`, `completedAt` FROM `tasks`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_isCompleted` ON `tasks` (`isCompleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueDateTime` ON `tasks` (`dueDateTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_createdAt` ON `tasks` (`createdAt`)")

                // ===== courses：dayOfWeek → weekdays + 新增 weekType =====
                db.execSQL(
                    """
                    CREATE TABLE `courses_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `location` TEXT,
                        `teacher` TEXT,
                        `weekdays` TEXT NOT NULL,
                        `startMinute` INTEGER NOT NULL,
                        `endMinute` INTEGER NOT NULL,
                        `examDate` INTEGER,
                        `weekType` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `courses_new` (`id`, `name`, `location`, `teacher`, `weekdays`, `startMinute`, `endMinute`, `examDate`, `weekType`)
                    SELECT `id`, `name`, `location`, `teacher`, CAST(`dayOfWeek` AS TEXT), `startMinute`, `endMinute`, `examDate`, 'EVERY' FROM `courses`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `courses`")
                db.execSQL("ALTER TABLE `courses_new` RENAME TO `courses`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_weekdays` ON `courses` (`weekdays`)")
            }
        }

        /**
         * v3 → v4：courses 表新增三列（可空/带默认值，不丢数据）。
         * - startWeek INTEGER NOT NULL DEFAULT 1   （起始周次）
         * - endWeek INTEGER NOT NULL DEFAULT 16    （结束周次）
         * - courseType TEXT NOT NULL DEFAULT 'UNKNOWN'（课程性质）
         * 旧课程自动按「第 1~16 周、未知性质」处理，行为与升级前完全一致。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `courses` ADD COLUMN `startWeek` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `courses` ADD COLUMN `endWeek` INTEGER NOT NULL DEFAULT 16")
                db.execSQL("ALTER TABLE `courses` ADD COLUMN `courseType` TEXT NOT NULL DEFAULT 'UNKNOWN'")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
