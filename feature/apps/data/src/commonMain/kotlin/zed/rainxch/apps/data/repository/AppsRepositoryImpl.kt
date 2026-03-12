package zed.rainxch.apps.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.GithubRelease
import zed.rainxch.core.domain.model.InstalledApp
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.utils.AppLauncher

class AppsRepositoryImpl(
    private val appLauncher: AppLauncher,
    private val appsRepository: InstalledAppsRepository,
    private val logger: GitHubStoreLogger,
    private val httpClient: HttpClient,
) : AppsRepository {
    override suspend fun getApps(): Flow<List<InstalledApp>> = appsRepository.getAllInstalledApps()

    override suspend fun openApp(
        installedApp: InstalledApp,
        onCantLaunchApp: () -> Unit,
    ) {
        val canLaunch = appLauncher.canLaunchApp(installedApp)

        if (canLaunch) {
            appLauncher
                .launchApp(installedApp)
                .onFailure { error ->
                    logger.error("Failed to launch app: ${error.message}")
                    onCantLaunchApp()
                }
        } else {
            onCantLaunchApp()
        }
    }

    override suspend fun getLatestRelease(
        owner: String,
        repo: String,
    ): GithubRelease? =
        try {
            val releases =
                httpClient
                    .executeRequest<List<ReleaseNetwork>> {
                        get("/repos/$owner/$repo/releases") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                            parameter("per_page", 10)
                        }
                    }.getOrThrow()

            releases
                .asSequence()
                .filter { (it.draft != true) && (it.prerelease != true) }
                .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }
                ?.toDomain()
        } catch (e: RateLimitException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to fetch latest release for $owner/$repo: ${e.message}")
            null
        }
}
