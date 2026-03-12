package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE starred_repos (
                    repoId INTEGER NOT NULL,
                    repoName TEXT NOT NULL,
                    repoOwner TEXT NOT NULL,
                    repoOwnerAvatarUrl TEXT NOT NULL,
                    repoDescription TEXT,
                    primaryLanguage TEXT,
                    repoUrl TEXT NOT NULL,

                    stargazersCount INTEGER NOT NULL,
                    forksCount INTEGER NOT NULL,
                    openIssuesCount INTEGER NOT NULL,

                    isInstalled INTEGER NOT NULL DEFAULT 0,
                    installedPackageName TEXT,

                    latestVersion TEXT,
                    latestReleaseUrl TEXT,

                    starredAt INTEGER,
                    addedAt INTEGER NOT NULL,
                    lastSyncedAt INTEGER NOT NULL,

                    PRIMARY KEY(repoId)
                )
                """.trimIndent(),
            )
        }
    }
