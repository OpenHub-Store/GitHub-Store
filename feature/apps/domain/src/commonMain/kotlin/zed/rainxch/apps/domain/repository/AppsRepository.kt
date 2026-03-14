package zed.rainxch.apps.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.apps.domain.model.ImportResult
import zed.rainxch.core.domain.model.DeviceApp
import zed.rainxch.core.domain.model.GithubRelease
import zed.rainxch.core.domain.model.InstalledApp

interface AppsRepository {
    suspend fun getApps(): Flow<List<InstalledApp>>

    suspend fun openApp(
        installedApp: InstalledApp,
        onCantLaunchApp: () -> Unit = { },
    )

    suspend fun getLatestRelease(
        owner: String,
        repo: String,
    ): GithubRelease?

    suspend fun getDeviceApps(): List<DeviceApp>

    suspend fun getTrackedPackageNames(): Set<String>

    suspend fun fetchRepoInfo(owner: String, repo: String): GithubRepoInfo?

    suspend fun linkAppToRepo(deviceApp: DeviceApp, repoInfo: GithubRepoInfo)

    suspend fun exportApps(): String

    suspend fun importApps(json: String): ImportResult
}
