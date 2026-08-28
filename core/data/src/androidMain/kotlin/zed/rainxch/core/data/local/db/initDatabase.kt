package zed.rainxch.core.data.local.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import zed.rainxch.core.data.local.db.migrations.MIGRATION_1_2
import zed.rainxch.core.data.local.db.migrations.MIGRATION_2_3
import zed.rainxch.core.data.local.db.migrations.MIGRATION_3_4
import zed.rainxch.core.data.local.db.migrations.MIGRATION_4_5
import zed.rainxch.core.data.local.db.migrations.MIGRATION_5_6
import zed.rainxch.core.data.local.db.migrations.MIGRATION_6_7
import zed.rainxch.core.data.local.db.migrations.MIGRATION_7_8
import zed.rainxch.core.data.local.db.migrations.MIGRATION_8_9
import zed.rainxch.core.data.local.db.migrations.MIGRATION_9_10
import zed.rainxch.core.data.local.db.migrations.MIGRATION_10_11
import zed.rainxch.core.data.local.db.migrations.MIGRATION_11_12
import zed.rainxch.core.data.local.db.migrations.MIGRATION_12_13
import zed.rainxch.core.data.local.db.migrations.MIGRATION_13_14
import zed.rainxch.core.data.local.db.migrations.MIGRATION_14_15
import zed.rainxch.core.data.local.db.migrations.MIGRATION_15_16
import zed.rainxch.core.data.local.db.migrations.MIGRATION_16_17
import zed.rainxch.core.data.local.db.migrations.MIGRATION_17_18

fun initDatabase(context: Context): AppDatabase {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath("github_store.db")
    return Room
        .databaseBuilder<AppDatabase>(
            context = appContext,
            name = dbFile.absolutePath,
        ).setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
        ).fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()
}
