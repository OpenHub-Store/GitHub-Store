package zed.rainxch.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity

@Dao
interface InstalledAppDao {
    @Query("SELECT * FROM installed_apps ORDER BY installedAt DESC")
    fun getAllInstalledApps(): Flow<List<InstalledAppEntity>>

    @Query("SELECT * FROM installed_apps WHERE isUpdateAvailable = 1 ORDER BY lastCheckedAt DESC")
    fun getAppsWithUpdates(): Flow<List<InstalledAppEntity>>

    @Query("SELECT * FROM installed_apps WHERE packageName = :packageName")
    suspend fun getAppByPackage(packageName: String): InstalledAppEntity?

    @Query("SELECT * FROM installed_apps WHERE repoId = :repoId")
    suspend fun getAppByRepoId(repoId: Long): InstalledAppEntity?

    @Query("SELECT * FROM installed_apps WHERE repoId = :repoId")
    fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledAppEntity?>

    @Query("SELECT * FROM installed_apps WHERE repoId = :repoId ORDER BY installedAt DESC")
    suspend fun getAppsByRepoId(repoId: Long): List<InstalledAppEntity>

    @Query("SELECT * FROM installed_apps WHERE repoId = :repoId ORDER BY installedAt DESC")
    fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledAppEntity>>

    @Query("SELECT COUNT(*) FROM installed_apps WHERE isUpdateAvailable = 1")
    fun getUpdateCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: InstalledAppEntity)

    @Update
    suspend fun updateApp(app: InstalledAppEntity)

    @Delete
    suspend fun deleteApp(app: InstalledAppEntity)

    @Query("DELETE FROM installed_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("SELECT packageName FROM installed_apps")
    suspend fun getTrackedPackageNames(): List<String>

    @Query(
        """
    UPDATE installed_apps
    SET isUpdateAvailable = :available,
        latestVersion = :version,
        latestAssetName = :assetName,
        latestAssetUrl = :assetUrl,
        latestAssetSize = :assetSize,
        releaseNotes = :releaseNotes,
        lastCheckedAt = :timestamp,
        latestVersionName = :latestVersionName,
        latestVersionCode = :latestVersionCode,
        latestReleasePublishedAt = :latestReleasePublishedAt
    WHERE packageName = :packageName
""",
    )
    suspend fun updateVersionInfo(
        packageName: String,
        available: Boolean,
        version: String?,
        assetName: String?,
        assetUrl: String?,
        assetSize: Long?,
        releaseNotes: String?,
        timestamp: Long,
        latestVersionName: String?,
        latestVersionCode: Long?,
        latestReleasePublishedAt: String?,
    )

    @Query("UPDATE installed_apps SET includePreReleases = :enabled WHERE packageName = :packageName")
    suspend fun updateIncludePreReleases(packageName: String, enabled: Boolean)

    @Query("UPDATE installed_apps SET updateCheckEnabled = :enabled WHERE packageName = :packageName")
    suspend fun updateUpdateCheckEnabled(packageName: String, enabled: Boolean)

    @Query(
        """
        UPDATE installed_apps
           SET assetFilterRegex = :regex,
               fallbackToOlderReleases = :fallback
         WHERE packageName = :packageName
        """,
    )
    suspend fun updateAssetFilter(
        packageName: String,
        regex: String?,
        fallback: Boolean,
    )

    @Query(
        """
        UPDATE installed_apps
           SET preferredAssetVariant = :variant,
               preferredAssetTokens = :tokens,
               assetGlobPattern = :glob,
               pickedAssetIndex = :pickedIndex,
               pickedAssetSiblingCount = :siblingCount,
               preferredVariantStale = 0
         WHERE packageName = :packageName
        """,
    )
    suspend fun updatePreferredVariant(
        packageName: String,
        variant: String?,
        tokens: String?,
        glob: String?,
        pickedIndex: Int?,
        siblingCount: Int?,
    )

    @Query(
        """
        UPDATE installed_apps
           SET preferredVariantStale = :stale
         WHERE packageName = :packageName
        """,
    )
    suspend fun updateVariantStaleness(
        packageName: String,
        stale: Boolean,
    )

    @Query("UPDATE installed_apps SET lastCheckedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateLastChecked(
        packageName: String,
        timestamp: Long,
    )

    @Query(
        """
        UPDATE installed_apps
           SET skippedReleaseTag = :tag,
               isUpdateAvailable = CASE WHEN :tag IS NULL THEN isUpdateAvailable ELSE 0 END
         WHERE packageName = :packageName
        """,
    )
    suspend fun setSkippedReleaseTag(
        packageName: String,
        tag: String?,
    )

    @Query(
        """
        SELECT * FROM installed_apps
         WHERE skippedReleaseTag IS NOT NULL
         ORDER BY appName COLLATE NOCASE ASC
        """,
    )
    fun getAppsWithSkippedReleaseTag(): Flow<List<InstalledAppEntity>>

    @Query(
        """
        UPDATE installed_apps
           SET installedVersion = :installedVersion,
               installedVersionName = :installedVersionName,
               installedVersionCode = :installedVersionCode,
               isUpdateAvailable = :isUpdateAvailable
         WHERE packageName = :packageName
        """,
    )
    suspend fun updateInstalledVersion(
        packageName: String,
        installedVersion: String,
        installedVersionName: String?,
        installedVersionCode: Long,
        isUpdateAvailable: Boolean,
    )

    @Query(
        """
        UPDATE installed_apps
           SET pendingInstallFilePath = :path,
               pendingInstallVersion = :version,
               pendingInstallAssetName = :assetName
         WHERE packageName = :packageName
        """,
    )
    suspend fun updatePendingInstallFilePath(
        packageName: String,
        path: String?,
        version: String?,
        assetName: String?,
    )

    @Query(
        """
        UPDATE installed_apps
           SET isUpdateAvailable = 0,
               latestVersion = NULL,
               latestAssetName = NULL,
               latestAssetUrl = NULL,
               latestAssetSize = NULL,
               latestVersionName = NULL,
               latestVersionCode = NULL,
               latestReleasePublishedAt = NULL,
               releaseNotes = NULL,
               lastCheckedAt = :timestamp
         WHERE packageName = :packageName
        """,
    )
    suspend fun clearUpdateMetadata(
        packageName: String,
        timestamp: Long,
    )
}
