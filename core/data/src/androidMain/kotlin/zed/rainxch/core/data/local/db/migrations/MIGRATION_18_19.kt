package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_18_19 =
    object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE installed_apps
                ADD COLUMN lastUpdateCheckReport TEXT DEFAULT NULL
                """.trimIndent(),
            )
        }
    }
