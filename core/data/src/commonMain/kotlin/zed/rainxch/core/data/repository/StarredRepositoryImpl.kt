@file:OptIn(ExperimentalTime::class)

package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import zed.rainxch.core.data.dto.GitHubStarredResponse
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.StarredRepoDao
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.core.domain.repository.StarredRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class StarredRepositoryImpl(
    private val starredRepoDao: StarredRepoDao,
    private val installedAppsDao: InstalledAppDao,
    private val platform: Platform,
    private val httpClient: HttpClient,
) : StarredRepository {
    companion object {
        private const val SYNC_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    override fun getAllStarred(): Flow<List<zed.rainxch.core.domain.model.StarredRepository>> =
        starredRepoDao
            .getAllStarred()
            .map { it.map { entity -> entity.toDomain() } }

    override suspend fun isStarred(repoId: Long): Boolean = starredRepoDao.isStarred(repoId)

    override suspend fun getLastSyncTime(): Long? = starredRepoDao.getLastSyncTime()

    override suspend fun needsSync(): Boolean {
        val lastSync = getLastSyncTime() ?: return true
        val now = Clock.System.now().toEpochMilliseconds()
        return (now - lastSync) > SYNC_THRESHOLD_MS
    }

    override suspend fun syncStarredRepos(forceRefresh: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh && !needsSync()) {
                    return@withContext Result.success(Unit)
                }

                val allRepos = mutableListOf<GitHubStarredResponse>()
                var page = 1
                val perPage = 100

                while (true) {
                    val response =
                        httpClient.get("/user/starred") {
                            parameter("per_page", perPage)
                            parameter("page", page)
                        }

                    if (!response.status.isSuccess()) {
                        if (response.status.value == 401) {
                            return@withContext Result.failure(
                                Exception("Authentication required. Please sign in with GitHub."),
                            )
                        }
                        return@withContext Result.failure(
                            Exception("Failed to fetch starred repos: ${response.status.description}"),
                        )
                    }

                    val repos: List<GitHubStarredResponse> = response.body()

                    if (repos.isEmpty()) break

                    allRepos.addAll(repos)

                    if (repos.size < perPage) break
                    page++
                }

                val now = Clock.System.now().toEpochMilliseconds()
                val starredRepos = mutableListOf<zed.rainxch.core.domain.model.StarredRepository>()

                coroutineScope {
                    val semaphore = Semaphore(25)
                    val deferredResults =
                        allRepos.map { repo ->
                            async {
                                semaphore.withPermit {
                                    val hasValidAssets =
                                        checkForValidAssets(repo.owner.login, repo.name)
                                    if (hasValidAssets) {
                                        val installedApp = installedAppsDao.getAppByRepoId(repo.id)
                                        zed.rainxch.core.domain.model.StarredRepository(
                                            repoId = repo.id,
                                            repoName = repo.name,
                                            repoOwner = repo.owner.login,
                                            repoOwnerAvatarUrl = repo.owner.avatarUrl,
                                            repoDescription = repo.description,
                                            primaryLanguage = repo.language,
                                            repoUrl = repo.htmlUrl,
                                            stargazersCount = repo.stargazersCount,
                                            forksCount = repo.forksCount,
                                            openIssuesCount = repo.openIssuesCount,
                                            isInstalled = installedApp != null,
                                            installedPackageName = installedApp?.packageName,
                                            latestVersion = null,
                                            latestReleaseUrl = null,
                                            starredAt =
                                                repo.starredAt?.let {
                                                    Instant.parse(it).toEpochMilliseconds()
                                                },
                                            addedAt = now,
                                            lastSyncedAt = now,
                                        )
                                    } else {
                                        null
                                    }
                                }
                            }
                        }

                    deferredResults.awaitAll().filterNotNull().let { validRepos ->
                        starredRepos.addAll(validRepos)
                    }
                }

                starredRepoDao.replaceAllStarred(starredRepos.map { it.toEntity() })

                Result.success(Unit)
            } catch (e: RateLimitException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Failed to sync starred repos" }
                Result.failure(e)
            }
        }

    private suspend fun checkForValidAssets(
        owner: String,
        repo: String,
    ): Boolean {
        return try {
            val releasesResponse =
                httpClient.get("/repos/$owner/$repo/releases") {
                    header("Accept", "application/vnd.github.v3+json")
                    parameter("per_page", 10)
                }

            if (!releasesResponse.status.isSuccess()) {
                return false
            }

            val allReleases: List<GithubReleaseNetworkModel> = releasesResponse.body()

            val stableRelease =
                allReleases.firstOrNull {
                    it.draft != true && it.prerelease != true
                } ?: return false

            if (stableRelease.assets.isEmpty()) {
                return false
            }

            val relevantAssets =
                stableRelease.assets.filter { asset ->
                    val name = asset.name.lowercase()
                    when (platform) {
                        Platform.ANDROID -> {
                            name.endsWith(".apk")
                        }

                        Platform.WINDOWS -> {
                            name.endsWith(".msi") || name.endsWith(".exe")
                        }

                        Platform.MACOS -> {
                            name.endsWith(".dmg") || name.endsWith(".pkg")
                        }

                        Platform.LINUX -> {
                            name.endsWith(".appimage") || name.endsWith(".deb") ||
                                name.endsWith(
                                    ".rpm",
                                )
                        }
                    }
                }

            relevantAssets.isNotEmpty()
        } catch (e: RateLimitException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "Failed to check valid assets for $owner/$repo" }
            false
        }
    }

    @Serializable
    private data class GithubReleaseNetworkModel(
        val assets: List<AssetNetworkModel>,
        val draft: Boolean? = null,
        val prerelease: Boolean? = null,
        @SerialName("published_at") val publishedAt: String? = null,
    )

    @Serializable
    private data class AssetNetworkModel(
        val name: String,
    )
}
