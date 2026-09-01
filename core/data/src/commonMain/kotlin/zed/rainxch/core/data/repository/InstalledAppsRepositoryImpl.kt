package zed.rainxch.core.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlin.coroutines.cancellation.CancellationException
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
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.clearPending
import zed.rainxch.core.domain.model.installation.confirmInstall
import zed.rainxch.core.domain.model.installation.markPending
import zed.rainxch.core.domain.model.smart_detect.MatchingPreview
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.utils.AssetFilter
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.data.services.FileLocationsProvider
import zed.rainxch.core.domain.utils.UpdateVerdict
import zed.rainxch.core.domain.utils.VersionMath
import java.io.File
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class InstalledAppsRepositoryImpl(
    private val database: AppDatabase,
    private val installedAppsDao: InstalledAppDao,
    private val historyDao: UpdateHistoryDao,
    private val installer: Installer,
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: zed.rainxch.core.data.network.BackendApiClient,
    private val forgejoClientRegistry: zed.rainxch.core.data.network.ForgejoClientRegistry,
    private val fileLocationsProvider: FileLocationsProvider,
) : InstalledAppsRepository {

    private val httpClient: HttpClient get() = clientProvider.client

    private companion object {

        const val RELEASE_WINDOW = 50
        const val MAX_CHECK_LOG_BYTES = 262_144
    }

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

    override suspend fun getAppByRepoId(repoId: Long): InstalledApp? =
        installedAppsDao.getAppByRepoId(repoId)?.toDomain()

    override fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?> =
        installedAppsDao.getAppByRepoIdAsFlow(repoId).map { it?.toDomain() }

    override suspend fun getAppsByRepoId(repoId: Long): List<InstalledApp> =
        installedAppsDao.getAppsByRepoId(repoId).map { it.toDomain() }

    override fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledApp>> =
        installedAppsDao.getAppsByRepoIdAsFlow(repoId).map { list -> list.map { it.toDomain() } }

    override suspend fun isAppInstalled(repoId: Long): Boolean =
        installedAppsDao.getAppByRepoId(repoId) != null

    override suspend fun saveInstalledApp(app: InstalledApp) {
        installedAppsDao.insertApp(app.toEntity())
    }

    override suspend fun deleteInstalledApp(packageName: String) {
        installedAppsDao.deleteByPackageName(packageName)
    }

    private data class ReleaseWindow(
        val releases: List<GithubRelease>,
        val source: String,
    )

    private suspend fun fetchReleaseWindow(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
        sourceHost: String? = null,
    ): ReleaseWindow {
        if (sourceHost != null) {
            return ReleaseWindow(
                releases = fetchForgejoReleaseWindow(sourceHost, owner, repo, includePreReleases),
                source = "forgejo",
            )
        }
        val backendResult = backendApiClient.getReleases(owner, repo, perPage = RELEASE_WINDOW)
        val backendReleases = backendResult.fold(
            onSuccess = { it },
            onFailure = { error ->
                if (!zed.rainxch.core.data.network.shouldFallbackToGithubOrRethrow(error)) {
                    return ReleaseWindow(emptyList(), "backend_blocked")
                }
                null
            },
        )
        if (backendReleases != null) {
            return ReleaseWindow(
                releases = backendReleases
                    .asSequence()
                    .filter { it.draft != true }
                    .sortedByDescending { it.publishedAt ?: it.createdAt ?: "" }
                    .map { it.toDomain() }
                    .filter { includePreReleases || !it.isEffectivelyPreRelease() }
                    .toList(),
                source = "backend",
            )
        }

        return try {
            val releases =
                httpClient
                    .executeRequest<List<ReleaseNetwork>> {
                        get("/repos/$owner/$repo/releases") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                            parameter("per_page", RELEASE_WINDOW)
                        }
                    }.getOrNull()
            if (releases == null) {
                return ReleaseWindow(emptyList(), "github_empty")
            }

            ReleaseWindow(
                releases = releases
                    .asSequence()
                    .filter { it.draft != true }
                    .sortedByDescending { it.publishedAt ?: it.createdAt ?: "" }
                    .map { it.toDomain() }
                    .onEach { release ->
                        val flagSays = release.isPrerelease
                        val tagSays = VersionMath.isPreReleaseTag(release.tagName)
                        if (flagSays != tagSays) {
                            Logger.w {
                                "Pre-release flag/tag mismatch for $owner/$repo " +
                                        "release '${release.tagName}' (name='${release.name}'): " +
                                        "apiFlag=$flagSays, tagMarker=$tagSays — " +
                                        "treating as pre-release=${true}"
                            }
                        }
                    }
                    .filter { includePreReleases || !it.isEffectivelyPreRelease() }
                    .toList(),
                source = "github",
            )
        } catch (e: CancellationException) {

            throw e
        } catch (e: Exception) {
            Logger.e { "Failed to fetch releases for $owner/$repo: ${e.message}" }
            ReleaseWindow(emptyList(), "github_error")
        }
    }

    private data class ResolvedRelease(
        val release: GithubRelease,
        val primaryAsset: GithubAsset,
        val variantWasLost: Boolean,
    )

    private fun resolveTrackedRelease(
        releases: List<GithubRelease>,
        filter: AssetFilter?,
        fallbackToOlderReleases: Boolean,
        preferredVariant: String?,
        preferredTokens: Set<String>,
        preferredGlob: String?,
        pickedIndex: Int?,
        pickedSiblingCount: Int?,
        trackedPackageName: String,
        installedAssetName: String?,
    ): ResolvedRelease? {
        if (releases.isEmpty()) return null

        val candidates =
            if (filter != null && !fallbackToOlderReleases) {
                releases.take(1)
            } else {
                releases
            }

        val hasAnyPin =
            preferredVariant != null ||
                    preferredTokens.isNotEmpty() ||
                    !preferredGlob.isNullOrBlank()

        for (release in candidates) {
            val installableForPlatform =
                release.assets.filter { installer.isAssetInstallable(it.name) }
            val installableForApp =
                if (filter == null) installableForPlatform
                else installableForPlatform.filter { filter.matches(it.name) }

            if (installableForApp.isEmpty()) continue

            val fingerprintMatch =
                AssetVariant.resolvePreferredAsset(
                    assets = installableForApp,
                    pinnedVariant = preferredVariant,
                    pinnedTokens = preferredTokens.takeIf { it.isNotEmpty() },
                    pinnedGlob = preferredGlob,
                )

            val positionMatch =
                if (fingerprintMatch == null && hasAnyPin) {
                    AssetVariant.resolveBySamePosition(
                        assets = installableForApp,
                        originalIndex = pickedIndex,
                        siblingCountAtPickTime = pickedSiblingCount,
                    )
                } else {
                    null
                }

            val installedStem =
                installedAssetName
                    ?.let { AssetVariant.extractBaseStem(it) }
                    ?.takeIf { it.isNotEmpty() }
            val autoPickPool =
                AssetVariant
                    .filterByPackageFlavor(installableForApp, trackedPackageName)
                    .let { pool ->
                        if (installedStem == null) {
                            pool
                        } else {
                            val matching =
                                pool.filter {
                                    AssetVariant.extractBaseStem(it.name) == installedStem
                                }

                            matching.ifEmpty { pool }
                        }
                    }
            val primary = fingerprintMatch
                ?: positionMatch
                ?: installer.choosePrimaryAsset(autoPickPool)
                ?: continue

            val variantWasLost =
                hasAnyPin && fingerprintMatch == null && positionMatch == null

            return ResolvedRelease(release, primary, variantWasLost)
        }

        return null
    }

    override suspend fun checkForUpdates(packageName: String): Boolean {
        val app = installedAppsDao.getAppByPackage(packageName) ?: return false

        if (!app.updateCheckEnabled) {
            Logger.d { "[UPDATE-CHECK] $packageName disabled -> branch=disabled reason=update_check_disabled" }
            return false
        }

        try {
            val window =
                fetchReleaseWindow(
                    owner = app.repoOwner,
                    repo = app.repoName,
                    includePreReleases = app.includePreReleases,
                    sourceHost = app.sourceHost,
                )
            val releases = window.releases

            if (releases.isEmpty()) {
                Logger.i {
                    "[UPDATE-CHECK] $packageName branch=empty_window reason=no_releases_in_window " +
                        "windowSource=${window.source} windowSize=0"
                }
                appendCheckLog(packageName, null, "-")
                return false
            }

            val compiledFilter =
                AssetFilter.parse(app.assetFilterRegex)
                    ?.onFailure { error ->
                        Logger.w {
                            "Invalid asset filter for $packageName " +
                                    "(${app.assetFilterRegex}): ${error.message} — ignoring"
                        }
                    }?.getOrNull()

            val resolved = resolveTrackedRelease(
                releases = releases,
                filter = compiledFilter,
                fallbackToOlderReleases = app.fallbackToOlderReleases,
                preferredVariant = app.preferredAssetVariant,
                preferredTokens = AssetVariant.deserializeTokens(app.preferredAssetTokens),
                preferredGlob = app.assetGlobPattern,
                pickedIndex = app.pickedAssetIndex,
                pickedSiblingCount = app.pickedAssetSiblingCount,
                trackedPackageName = app.packageName,
                installedAssetName = app.installedAssetName,
            )

            if (resolved == null) {
                Logger.d {
                    "No matching release found for ${app.appName} in window of ${releases.size}; " +
                            "filter=${app.assetFilterRegex}, fallback=${app.fallbackToOlderReleases}"
                }
                Logger.i {
                    "[UPDATE-CHECK] ${app.appName} $packageName branch=no_match " +
                        "reason=no_matching_release windowSource=${window.source} " +
                        "windowSize=${releases.size}"
                }
                appendCheckLog(packageName, null, "-")
                return false
            }

            val (matchedRelease, primaryAsset, variantWasLost) = resolved

            val verdict =
                UpdateVerdict.decide(
                    installedTag = app.installedVersion,
                    installedVersionCode = app.installedVersionCode,
                    storedLatestTag = app.latestVersion,
                    storedLatestVersionCode = app.latestVersionCode,
                    storedPublishedAt = app.latestReleasePublishedAt,
                    wasUpdateAvailable = app.isUpdateAvailable,
                    skippedTag = app.skippedReleaseTag,
                    matchedTag = matchedRelease.tagName,
                    matchedPublishedAt = matchedRelease.publishedAt,
                    matchedIsPrerelease = matchedRelease.isPrerelease,
                )

            if (verdict.skipBecameStale) {
                installedAppsDao.setSkippedReleaseTag(packageName, null)
            }

            val reconcilable = verdict.reconcilable
            val opaqueMatched = verdict.opaqueMatched
            val sameTag = verdict.sameTag
            val usedTimestampLogic = verdict.usedTimestampLogic
            val codesAlreadyMatch = verdict.codesAlreadyMatch
            val isUpdateAvailable = verdict.isUpdateAvailable
            val branch = verdict.branch
            val reason = verdict.reason

            val reportText =
                listOf(
                    "usedTimestampLogic=$usedTimestampLogic",
                    "branch=$branch",
                    "reason=$reason",
                    "windowSource=${window.source}",
                    "windowSize=${releases.size}",
                    "includePreReleases=${app.includePreReleases}",
                    "opaqueMatched=$opaqueMatched",
                    "sameTag=$sameTag",
                    "reconcilable=$reconcilable",
                    "installedTag=${app.installedVersion}",
                    "matchedTag=${matchedRelease.tagName}",
                    "matchedAssetName=${primaryAsset.name}",
                    "matchedAssetUrl=${primaryAsset.downloadUrl}",
                    "storedPublishedAt=${app.latestReleasePublishedAt}",
                    "matchedPublishedAt=${matchedRelease.publishedAt}",
                    "codesAlreadyMatch=$codesAlreadyMatch",
                    "isUpdate=$isUpdateAvailable",
                ).joinToString("\n")
            Logger.i { "[UPDATE-CHECK] ${app.appName} $packageName\n$reportText" }

            val resolvedLatestVersionCode =
                if (matchedRelease.tagName == app.latestVersion) app.latestVersionCode else null

            installedAppsDao.updateVersionInfo(
                packageName = packageName,
                available = isUpdateAvailable,
                version = matchedRelease.tagName,
                assetName = primaryAsset.name,
                assetUrl = primaryAsset.downloadUrl,
                assetSize = primaryAsset.size,
                releaseNotes = matchedRelease.description ?: "",
                timestamp = System.currentTimeMillis(),
                latestVersionName = matchedRelease.tagName,
                latestVersionCode = resolvedLatestVersionCode,
                latestReleasePublishedAt = matchedRelease.publishedAt,
            )

            if (UpdateVerdict.mayRewriteInstalledTag(verdict) &&
                app.installedVersion != matchedRelease.tagName
            ) {
                installedAppsDao.updateInstalledVersion(
                    packageName = packageName,
                    installedVersion = matchedRelease.tagName,
                    installedVersionName = app.installedVersionName,
                    installedVersionCode = app.installedVersionCode,
                    isUpdateAvailable = false,
                )
            }

            if (variantWasLost != app.preferredVariantStale) {
                installedAppsDao.updateVariantStaleness(packageName, variantWasLost)
            }

            appendCheckLog(packageName, matchedRelease.publishedAt, if (isUpdateAvailable) "1" else "0")

            return isUpdateAvailable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e { "Failed to check updates for $packageName: ${e.message}" }
            appendCheckLog(packageName, null, "E")
        }

        return false
    }

    override suspend fun checkAllForUpdates() {
        val apps = installedAppsDao.getAllInstalledApps().first()
        apps.forEach { app ->
            if (app.updateCheckEnabled) {
                try {
                    checkForUpdates(app.packageName)
                } catch (e: CancellationException) {
                    throw e
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
        signingFingerprint: String?,
        isPendingInstall: Boolean,
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

        appendCheckLog(packageName, app.latestReleasePublishedAt, "I")

        installedAppsDao.updateApp(
            app.toDomain()
                .confirmInstall(
                    tag = newTag,
                    assetName = newAssetName,
                    assetUrl = newAssetUrl,
                    versionName = newVersionName,
                    versionCode = newVersionCode,
                    signingFingerprint = signingFingerprint,
                    isPending = isPendingInstall,
                    at = System.currentTimeMillis(),
                )
                .toEntity(),
        )
    }

    override suspend fun updateApp(app: InstalledApp) {
        installedAppsDao.updateApp(app.toEntity())
    }

    override suspend fun updateInstalledVersion(
        packageName: String,
        installedVersion: String,
        installedVersionName: String?,
        installedVersionCode: Long,
        isUpdateAvailable: Boolean,
    ) {
        installedAppsDao.updateInstalledVersion(
            packageName = packageName,
            installedVersion = installedVersion,
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            isUpdateAvailable = isUpdateAvailable,
        )
    }

    override suspend fun updatePendingStatus(
        packageName: String,
        isPending: Boolean,
    ) {
        val app = installedAppsDao.getAppByPackage(packageName) ?: return
        installedAppsDao.updateApp(
            app.toDomain()
                .let { if (isPending) it.markPending() else it.clearPending() }
                .toEntity(),
        )
    }

    override suspend fun setIncludePreReleases(
        packageName: String,
        enabled: Boolean,
    ) {
        installedAppsDao.updateIncludePreReleases(packageName, enabled)
    }

    override suspend fun setUpdateCheckEnabled(
        packageName: String,
        enabled: Boolean,
    ) {
        installedAppsDao.updateUpdateCheckEnabled(packageName, enabled)
        if (enabled) {
            try {
                checkForUpdates(packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w {
                    "Failed to re-check after enabling update check for $packageName: ${e.message}"
                }
            }
        } else {
            installedAppsDao.clearUpdateMetadata(packageName, System.currentTimeMillis())
        }
    }

    override suspend fun setAssetFilter(
        packageName: String,
        regex: String?,
        fallbackToOlderReleases: Boolean,
    ) {
        val normalized = regex?.trim()?.takeIf { it.isNotEmpty() }
        installedAppsDao.updateAssetFilter(
            packageName = packageName,
            regex = normalized,
            fallback = fallbackToOlderReleases,
        )

        try {
            checkForUpdates(packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w {
                "Saved new asset filter for $packageName but immediate " +
                        "re-check failed: ${e.message}"
            }
        }
    }

    override suspend fun setPreferredVariant(
        packageName: String,
        variant: String?,
        tokens: String?,
        glob: String?,
        pickedIndex: Int?,
        siblingCount: Int?,
    ) {
        val normalizedVariant = variant?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedTokens = tokens?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedGlob = glob?.trim()?.takeIf { it.isNotEmpty() }
        installedAppsDao.updatePreferredVariant(
            packageName = packageName,
            variant = normalizedVariant,
            tokens = normalizedTokens,
            glob = normalizedGlob,
            pickedIndex = pickedIndex,
            siblingCount = siblingCount?.takeIf { it > 0 },
        )

        try {
            checkForUpdates(packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w {
                "Saved new variant for $packageName but immediate " +
                        "re-check failed: ${e.message}"
            }
        }
    }

    override suspend fun clearPreferredVariant(packageName: String) {
        setPreferredVariant(
            packageName = packageName,
            variant = null,
            tokens = null,
            glob = null,
            pickedIndex = null,
            siblingCount = null,
        )
    }

    override suspend fun setSkippedReleaseTag(
        packageName: String,
        tag: String?,
    ) {
        installedAppsDao.setSkippedReleaseTag(packageName, tag?.takeIf { it.isNotBlank() })
    }

    override fun getAppsWithSkippedReleaseTag(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAppsWithSkippedReleaseTag()
            .map { it.map { entity -> entity.toDomain() } }

    override suspend fun setPendingInstallFilePath(
        packageName: String,
        path: String?,
        version: String?,
        assetName: String?,
    ) {
        installedAppsDao.updatePendingInstallFilePath(
            packageName = packageName,
            path = path,
            version = version,
            assetName = assetName,
        )
    }

    override suspend fun previewMatchingAssets(
        owner: String,
        repo: String,
        regex: String?,
        includePreReleases: Boolean,
        fallbackToOlderReleases: Boolean,
    ): MatchingPreview {
        val parseResult = AssetFilter.parse(regex)
        if (parseResult != null && parseResult.isFailure) {
            return MatchingPreview(
                release = null,
                matchedAssets = emptyList(),
                regexError = parseResult.exceptionOrNull()?.message,
            )
        }
        val filter = parseResult?.getOrNull()

        val releases = fetchReleaseWindow(owner, repo, includePreReleases).releases
        if (releases.isEmpty()) {
            return MatchingPreview(release = null, matchedAssets = emptyList())
        }

        val candidates =
            if (filter != null && !fallbackToOlderReleases) {
                releases.take(1)
            } else {
                releases
            }

        for (release in candidates) {
            val installableForPlatform =
                release.assets.filter { installer.isAssetInstallable(it.name) }
            val matched =
                if (filter == null) installableForPlatform
                else installableForPlatform.filter { filter.matches(it.name) }
            if (matched.isNotEmpty()) {
                return MatchingPreview(release = release, matchedAssets = matched)
            }
        }

        return MatchingPreview(
            release = releases.firstOrNull(),
            matchedAssets = emptyList(),
        )
    }

    private suspend fun fetchForgejoReleaseWindow(
        host: String,
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): List<GithubRelease> {
        val client = forgejoClientRegistry.clientFor(host)
        return try {
            val releases = client.getReleases(owner, repo, perPage = RELEASE_WINDOW).getOrNull()
                ?: return emptyList()
            releases
                .asSequence()
                .filter { it.draft != true }
                .sortedByDescending { it.publishedAt ?: it.createdAt ?: "" }
                .map { it.toDomain() }
                .filter { includePreReleases || !it.isEffectivelyPreRelease() }
                .toList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e { "Forgejo fetch failed for $host/$owner/$repo: ${e.message}" }
            emptyList()
        }
    }

    // Debug trace, one line per check. Format:
    //   <packageName> <release publishedAt|-> <device-local check time> <flag>
    // Flags: 1 = judged as update, 0 = checked but not an update, I = installed,
    //        - = ran but nothing matched, E = check threw.
    // Appended to {appDownloadsDir}/update-check.log. Debug-only: never read by
    // app logic, never surfaced in the UI.
    private fun appendCheckLog(
        packageName: String,
        releaseTimestamp: String?,
        flag: String,
    ) {
        try {
            val dir = File(fileLocationsProvider.appDownloadsDir())
            if (!dir.exists() && !dir.mkdirs()) return
            val file = File(dir, "update-check.log")
            if (file.length() > MAX_CHECK_LOG_BYTES) file.writeText("")
            val stamp = releaseTimestamp?.takeIf { it.isNotBlank() } ?: "-"
            file.appendText("$packageName $stamp ${localNow()} $flag\n")
        } catch (t: Throwable) {
            Logger.w { "update-check log write failed: ${t.message}" }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun localNow(): String =
        try {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val h = now.hour.toString().padStart(2, '0')
            val m = now.minute.toString().padStart(2, '0')
            val s = now.second.toString().padStart(2, '0')
            "${now.date} $h:$m:$s"
        } catch (t: Throwable) {
            System.currentTimeMillis().toString()
        }
}
