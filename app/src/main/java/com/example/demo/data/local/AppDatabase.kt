package com.example.demo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.example.demo.data.local.converter.DateConverters
import com.example.demo.data.local.dao.PeriodRecordDao
import com.example.demo.data.local.dao.PlanDao
import com.example.demo.data.local.dao.PomodoroSessionDao
import com.example.demo.data.local.dao.TodoDao
import com.example.demo.data.local.dao.UserSettingDao
import com.example.demo.data.local.dao.WorkLogDao
import com.example.demo.data.local.entity.PeriodRecord
import com.example.demo.data.local.entity.Plan
import com.example.demo.data.local.entity.PomodoroSession
import com.example.demo.data.local.entity.Todo
import com.example.demo.data.local.entity.UserSetting
import com.example.demo.data.local.entity.WorkLog

/**
 * 应用数据库。
 *
 * exportSchema = true 会把当前表结构导出成 app/schemas/<DB 类全名>/<version>.json，
 * 这份 JSON 就是自动迁移和写 Migration 的基线，作用等同 Flyway 的版本化脚本，
 * **必须提交进 Git**，否则以后无法生成正确的迁移。
 *
 * version 每次改动表结构都要 +1，并提供对应的 Migration。
 */
@Database(
    entities = [
        PeriodRecord::class,
        Todo::class,
        WorkLog::class,
        PomodoroSession::class,
        UserSetting::class,
        Plan::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun periodRecordDao(): PeriodRecordDao
    abstract fun todoDao(): TodoDao
    abstract fun workLogDao(): WorkLogDao
    abstract fun pomodoroSessionDao(): PomodoroSessionDao
    abstract fun userSettingDao(): UserSettingDao
    abstract fun planDao(): PlanDao

    companion object {
        const val DATABASE_NAME = "moonlog.db"

        /**
         * v1 → v2：新增 plan 表（P10 计划栏）；pomodoro_session 加 mode 列区分计时模式。
         * mode 默认 0=番茄钟，使 P7 的历史行免回填即归入正确统计口径。
         * 列定义必须与 Entity 逐字对齐，否则 Room 校验迁移后 schema 会抛异常。
         */
        private val MIG_1_2 = Migration(1, 2) { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `plan` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `mode` INTEGER NOT NULL,
                    `target_minutes` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """
            )
            db.execSQL("ALTER TABLE `pomodoro_session` ADD COLUMN `mode` INTEGER NOT NULL DEFAULT 0")
        }

        /**
         * v2 → v3：plan 表加 is_daily 列（P11 每日计划）。
         * 默认 0=非每日，使既有行免回填即归入正确语义；列定义必须与 Entity 逐字对齐。
         */
        private val MIG_2_3 = Migration(2, 3) { db ->
            db.execSQL("ALTER TABLE `plan` ADD COLUMN `is_daily` INTEGER NOT NULL DEFAULT 0")
        }

        /**
         * v3 → v4：user_setting 加 show_fertile_window 列（易孕窗口显示开关）。
         * 默认 0=隐藏：存量用户升级后易孕窗立即收起（隐私默认最小化），免回填；
         * 列定义必须与 Entity 逐字对齐，否则 Room 校验迁移后 schema 会抛异常。
         */
        private val MIG_3_4 = Migration(3, 4) { db ->
            db.execSQL("ALTER TABLE `user_setting` ADD COLUMN `show_fertile_window` INTEGER NOT NULL DEFAULT 0")
        }

        /**
         * 双重检查锁的单例。数据库连接是重资源，全应用只应有一个实例，
         * 等价于后端的单例 DataSource / 连接池。
         *
         * @Volatile 保证 INSTANCE 的写入对所有线程立即可见，
         * 否则双重检查锁在 JVM 上是不安全的（这是经典 DCL 陷阱）。
         */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                // 显式迁移优先：升级时保留用户数据。
                .addMigrations(MIG_1_2, MIG_2_3, MIG_3_4)
                // demo 阶段的安全网：未覆盖的版本跨度才删库重建。
                // ⚠️ 正式版必须删掉这行，改为手写 Migration，否则用户升级即丢数据。
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
