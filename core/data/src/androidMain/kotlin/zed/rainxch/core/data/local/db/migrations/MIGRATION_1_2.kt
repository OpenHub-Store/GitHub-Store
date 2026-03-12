package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE installed_apps ADD COLUMN installedVersionName TEXT")
            db.execSQL("ALTER TABLE installed_apps ADD COLUMN installedVersionCode INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE installed_apps ADD COLUMN latestVersionName TEXT")
            db.execSQL("ALTER TABLE installed_apps ADD COLUMN latestVersionCode INTEGER")
        }
    }
