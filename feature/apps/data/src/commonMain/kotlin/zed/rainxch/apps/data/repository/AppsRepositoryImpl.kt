package zed.rainxch.apps.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.apps.domain.model.ImportResult
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.DeviceApp
import zed.rainxch.core.domain.model.ExportedApp
import zed.rainxch.core.domain.model.ExportedAppList
import zed.rainxch.core.domain.model.GithubRelease
import zed.rainxch.core.domain.model.InstallSource
import zed.rainxch.core.domain.model.InstalledApp
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.ThemesRepository
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.utils.AppLauncher
import kotlin.time.Clock

class AppsRepositoryImpl(
    private val appLauncher: AppLauncher,
    private val appsRepository: InstalledAppsRepository,
    private val logger: GitHubStoreLogger,
    private val httpClient: HttpClient,
    private val packageMonitor: PackageMonitor,
    private val themesRepository: ThemesRepository,
) : AppsRepository {

    private val json = Json { ignoreUnknownKeys = true }

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
            val includePreReleases = themesRepository.getIncludePreReleases().first()

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
                .filter { it.draft != true }
                .filter { includePreReleases || it.prerelease != true }
                .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }
                ?.toDomain()
        } catch (e: RateLimitException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to fetch latest release for $owner/$repo: ${e.message}")
            null
        }

    override suspend fun getDeviceApps(): List<DeviceApp> =
        packageMonitor.getAllInstalledApps()

    override suspend fun getTrackedPackageNames(): Set<String> =
        appsRepository.getAllInstalledApps().first().map { it.packageName }.toSet()

    override suspend fun fetchRepoInfo(owner: String, repo: String): GithubRepoInfo? =
        try {
            val repoModel =
                httpClient
                    .executeRequest<GithubRepoNetworkModel> {
                        get("/repos/$owner/$repo") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrThrow()

            // Also fetch latest release tag
            val includePreReleases = themesRepository.getIncludePreReleases().first()
            val latestTag = try {
                val releases =
                    httpClient
                        .executeRequest<List<ReleaseNetwork>> {
                            get("/repos/$owner/$repo/releases") {
                                header(HttpHeaders.Accept, "application/vnd.github+json")
                                parameter("per_page", 5)
                            }
                        }.getOrThrow()

                releases
                    .asSequence()
                    .filter { it.draft != true }
                    .filter { includePreReleases || it.prerelease != true }
                    .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }
                    ?.tagName
            } catch (_: Exception) {
                null
            }

            GithubRepoInfo(
                id = repoModel.id,
                name = repoModel.name,
                owner = repoModel.owner.login,
                ownerAvatarUrl = repoModel.owner.avatarUrl,
                description = repoModel.description,
                language = repoModel.language,
                htmlUrl = repoModel.htmlUrl,
                latestReleaseTag = latestTag,
            )
        } catch (e: RateLimitException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to fetch repo info for $owner/$repo: ${e.message}")
            null
        }

    override suspend fun linkAppToRepo(deviceApp: DeviceApp, repoInfo: GithubRepoInfo) {
        val now = Clock.System.now().toEpochMilliseconds()

        val installedApp = InstalledApp(
            packageName = deviceApp.packageName,
            repoId = repoInfo.id,
            repoName = repoInfo.name,
            repoOwner = repoInfo.owner,
            repoOwnerAvatarUrl = repoInfo.ownerAvatarUrl,
            repoDescription = repoInfo.description,
            primaryLanguage = repoInfo.language,
            repoUrl = repoInfo.htmlUrl,
            installedVersion = deviceApp.versionName ?: "unknown",
            installedAssetName = null,
            installedAssetUrl = null,
            latestVersion = repoInfo.latestReleaseTag,
            latestAssetName = null,
            latestAssetUrl = null,
            latestAssetSize = null,
            appName = deviceApp.appName,
            installSource = InstallSource.MANUAL,
            installedAt = now,
            lastCheckedAt = 0L,
            lastUpdatedAt = now,
            isUpdateAvailable = false,
            updateCheckEnabled = true,
            releaseNotes = null,
            systemArchitecture = "",
            fileExtension = "apk",
            isPendingInstall = false,
            installedVersionName = deviceApp.versionName,
            installedVersionCode = deviceApp.versionCode,
        )

        appsRepository.saveInstalledApp(installedApp)
    }

    override suspend fun exportApps(): String {
        val apps = appsRepository.getAllInstalledApps().first()
        val exported = ExportedAppList(
            version = 1,
            exportedAt = Clock.System.now().toEpochMilliseconds(),
            apps = apps.map { app ->
                ExportedApp(
                    packageName = app.packageName,
                    repoOwner = app.repoOwner,
                    repoName = app.repoName,
                    repoUrl = app.repoUrl,
                )
            },
        )
        return json.encodeToString(ExportedAppList.serializer(), exported)
    }

    override suspend fun importApps(jsonString: String): ImportResult {
        val exportedList = try {
            json.decodeFromString(ExportedAppList.serializer(), jsonString)
        } catch (e: Exception) {
            logger.error("Failed to parse import JSON: ${e.message}")
            return ImportResult(imported = 0, skipped = 0, failed = 1)
        }

        val trackedPackages = getTrackedPackageNames()
        var imported = 0
        var skipped = 0
        var failed = 0

        for (exportedApp in exportedList.apps) {
            if (exportedApp.packageName in trackedPackages) {
                skipped++
                continue
            }

            try {
                val repoInfo = fetchRepoInfo(exportedApp.repoOwner, exportedApp.repoName)
                if (repoInfo == null) {
                    failed++
                    continue
                }

                // Try to get device app info if installed
                val systemInfo = packageMonitor.getInstalledPackageInfo(exportedApp.packageName)

                val deviceApp = DeviceApp(
                    packageName = exportedApp.packageName,
                    appName = exportedApp.repoName,
                    versionName = systemInfo?.versionName,
                    versionCode = systemInfo?.versionCode ?: 0L,
                )

                linkAppToRepo(deviceApp, repoInfo)
                imported++
            } catch (e: Exception) {
                logger.error("Failed to import ${exportedApp.repoOwner}/${exportedApp.repoName}: ${e.message}")
                failed++
            }
        }

        return ImportResult(imported = imported, skipped = skipped, failed = failed)
    }
}
