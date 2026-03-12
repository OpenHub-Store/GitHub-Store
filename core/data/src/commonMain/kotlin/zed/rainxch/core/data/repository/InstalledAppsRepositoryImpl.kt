package zed.rainxch.core.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.local.db.AppDatabase
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.UpdateHistoryDao
import zed.rainxch.core.data.local.db.entities.UpdateHistoryEntity
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.model.GithubRelease
import zed.rainxch.core.domain.model.InstallSource
import zed.rainxch.core.domain.model.InstalledApp
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.Installer

class InstalledAppsRepositoryImpl(
    private val database: AppDatabase,
    private val installedAppsDao: InstalledAppDao,
    private val historyDao: UpdateHistoryDao,
    private val installer: Installer,
    private val httpClient: HttpClient,
) : InstalledAppsRepository {
    override suspend fun <R> executeInTransaction(block: suspend () -> R): R =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                block()
            }
        }

    override fun getAllInstalledApps(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAllInstalledApps()
            .map { it.map { app -> app.toDomain() } }

    override fun getAppsWithUpdates(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAppsWithUpdates()
            .map { it.map { app -> app.toDomain() } }

    override fun getUpdateCount(): Flow<Int> = installedAppsDao.getUpdateCount()

    override suspend fun getAppByPackage(packageName: String): InstalledApp? =
        installedAppsDao
            .getAppByPackage(packageName)
            ?.toDomain()

    override suspend fun getAppByRepoId(repoId: Long): InstalledApp? = installedAppsDao.getAppByRepoId(repoId)?.toDomain()

    override fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?> =
        installedAppsDao.getAppByRepoIdAsFlow(repoId).map { it?.toDomain() }

    override suspend fun isAppInstalled(repoId: Long): Boolean = installedAppsDao.getAppByRepoId(repoId) != null

    override suspend fun saveInstalledApp(app: InstalledApp) {
        installedAppsDao.insertApp(app.toEntity())
    }

    override suspend fun deleteInstalledApp(packageName: String) {
        installedAppsDao.deleteByPackageName(packageName)
    }

    private suspend fun fetchLatestPublishedRelease(
        owner: String,
        repo: String,
    ): GithubRelease? {
        return try {
            val releases =
                httpClient
                    .executeRequest<List<ReleaseNetwork>> {
                        get("/repos/$owner/$repo/releases") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                            parameter("per_page", 10)
                        }
                    }.getOrNull() ?: return null

            val latest =
                releases
                    .asSequence()
                    .filter { (it.draft != true) && (it.prerelease != true) }
                    .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }
                    ?: return null

            latest.toDomain()
        } catch (e: Exception) {
            Logger.e { "Failed to fetch latest release for $owner/$repo: ${e.message}" }
            null
        }
    }

    override suspend fun checkForUpdates(packageName: String): Boolean {
        val app = installedAppsDao.getAppByPackage(packageName) ?: return false

        try {
            val latestRelease =
                fetchLatestPublishedRelease(
                    owner = app.repoOwner,
                    repo = app.repoName,
                )

            if (latestRelease != null) {
                val normalizedInstalledTag = normalizeVersion(app.installedVersion)
                val normalizedLatestTag = normalizeVersion(latestRelease.tagName)

                val installableAssets =
                    latestRelease.assets.filter { asset ->
                        installer.isAssetInstallable(asset.name)
                    }
                val primaryAsset = installer.choosePrimaryAsset(installableAssets)

                val isUpdateAvailable = normalizedInstalledTag != normalizedLatestTag

                Logger.d {
                    "Update check for ${app.appName}: " +
                        "installedTag=${app.installedVersion}, latestTag=${latestRelease.tagName}, " +
                        "installedCode=${app.installedVersionCode}, " +
                        "isUpdate=$isUpdateAvailable"
                }

                installedAppsDao.updateVersionInfo(
                    packageName = packageName,
                    available = isUpdateAvailable,
                    version = latestRelease.tagName,
                    assetName = primaryAsset?.name,
                    assetUrl = primaryAsset?.downloadUrl,
                    assetSize = primaryAsset?.size,
                    releaseNotes = latestRelease.description ?: "",
                    timestamp = System.currentTimeMillis(),
                    latestVersionName = latestRelease.tagName,
                    latestVersionCode = null,
                )

                return isUpdateAvailable
            } else {
                installedAppsDao.updateLastChecked(packageName, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Logger.e { "Failed to check updates for $packageName: ${e.message}" }
            installedAppsDao.updateLastChecked(packageName, System.currentTimeMillis())
        }

        return false
    }

    override suspend fun checkAllForUpdates() {
        val apps = installedAppsDao.getAllInstalledApps().first()
        apps.forEach { app ->
            if (app.updateCheckEnabled) {
                try {
                    checkForUpdates(app.packageName)
                } catch (e: Exception) {
                    Logger.w { "Failed to check updates for ${app.packageName}: ${e.message}" }
                }
            }
        }
    }

    override suspend fun updateAppVersion(
        packageName: String,
        newTag: String,
        newAssetName: String,
        newAssetUrl: String,
        newVersionName: String,
        newVersionCode: Long,
    ) {
        val app = installedAppsDao.getAppByPackage(packageName) ?: return

        Logger.d {
            "Updating app version: $packageName from ${app.installedVersion} to $newTag"
        }

        historyDao.insertHistory(
            UpdateHistoryEntity(
                packageName = packageName,
                appName = app.appName,
                repoOwner = app.repoOwner,
                repoName = app.repoName,
                fromVersion = app.installedVersion,
                toVersion = newTag,
                updatedAt = System.currentTimeMillis(),
                updateSource = InstallSource.THIS_APP,
                success = true,
            ),
        )

        installedAppsDao.updateApp(
            app.copy(
                installedVersion = newTag,
                installedAssetName = newAssetName,
                installedAssetUrl = newAssetUrl,
                installedVersionName = newVersionName,
                installedVersionCode = newVersionCode,
                latestVersion = newTag,
                latestAssetName = newAssetName,
                latestAssetUrl = newAssetUrl,
                latestVersionName = newVersionName,
                latestVersionCode = newVersionCode,
                isUpdateAvailable = false,
                lastUpdatedAt = System.currentTimeMillis(),
                lastCheckedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateApp(app: InstalledApp) {
        installedAppsDao.updateApp(app.toEntity())
    }

    override suspend fun updatePendingStatus(
        packageName: String,
        isPending: Boolean,
    ) {
        val app = installedAppsDao.getAppByPackage(packageName) ?: return
        installedAppsDao.updateApp(app.copy(isPendingInstall = isPending))
    }

    private fun normalizeVersion(version: String): String = version.removePrefix("v").removePrefix("V").trim()
}
