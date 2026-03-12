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
        latestVersionCode = :latestVersionCode
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
    )

    @Query("UPDATE installed_apps SET lastCheckedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateLastChecked(
        packageName: String,
        timestamp: Long,
    )
}
