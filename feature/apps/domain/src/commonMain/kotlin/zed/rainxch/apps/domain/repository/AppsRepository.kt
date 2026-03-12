package zed.rainxch.apps.domain.repository

import kotlinx.coroutines.flow.Flow
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
}
