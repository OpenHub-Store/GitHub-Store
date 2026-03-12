package zed.rainxch.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.local.db.entities.FavoriteRepoEntity

@Dao
interface FavoriteRepoDao {
    @Query("SELECT * FROM favorite_repos ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteRepoEntity>>

    @Query("SELECT * FROM favorite_repos WHERE repoId = :repoId")
    suspend fun getFavoriteById(repoId: Long): FavoriteRepoEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_repos WHERE repoId = :repoId)")
    fun isFavorite(repoId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_repos WHERE repoId = :repoId)")
    suspend fun isFavoriteSync(repoId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(repo: FavoriteRepoEntity)

    @Delete
    suspend fun deleteFavorite(repo: FavoriteRepoEntity)

    @Query("DELETE FROM favorite_repos WHERE repoId = :repoId")
    suspend fun deleteFavoriteById(repoId: Long)

    @Query(
        """
        UPDATE favorite_repos 
        SET isInstalled = :installed, 
            installedPackageName = :packageName 
        WHERE repoId = :repoId
    """,
    )
    suspend fun updateInstallStatus(
        repoId: Long,
        installed: Boolean,
        packageName: String?,
    )

    @Query(
        """
        UPDATE favorite_repos 
        SET latestVersion = :version,
            latestReleaseUrl = :releaseUrl,
            lastSyncedAt = :timestamp
        WHERE repoId = :repoId
    """,
    )
    suspend fun updateLatestVersion(
        repoId: Long,
        version: String?,
        releaseUrl: String?,
        timestamp: Long,
    )
}
