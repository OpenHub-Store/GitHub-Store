package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.InstalledApp

interface InstalledAppsRepository {
    fun getAllInstalledApps(): Flow<List<InstalledApp>>

    fun getAppsWithUpdates(): Flow<List<InstalledApp>>

    fun getUpdateCount(): Flow<Int>

    suspend fun getAppByPackage(packageName: String): InstalledApp?

    suspend fun getAppByRepoId(repoId: Long): InstalledApp?

    fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?>

    suspend fun isAppInstalled(repoId: Long): Boolean

    suspend fun saveInstalledApp(app: InstalledApp)

    suspend fun deleteInstalledApp(packageName: String)

    suspend fun checkForUpdates(packageName: String): Boolean

    suspend fun checkAllForUpdates()

    suspend fun updateAppVersion(
        packageName: String,
        newTag: String,
        newAssetName: String,
        newAssetUrl: String,
        newVersionName: String,
        newVersionCode: Long,
    )

    suspend fun updateApp(app: InstalledApp)

    suspend fun updatePendingStatus(
        packageName: String,
        isPending: Boolean,
    )

    suspend fun <R> executeInTransaction(block: suspend () -> R): R
}
