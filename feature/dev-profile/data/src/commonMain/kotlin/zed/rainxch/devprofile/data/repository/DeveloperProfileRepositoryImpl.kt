package zed.rainxch.devprofile.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.devprofile.data.dto.GitHubRepoResponse
import zed.rainxch.devprofile.data.dto.GitHubUserResponse
import zed.rainxch.devprofile.data.mappers.toDomain
import zed.rainxch.devprofile.domain.model.DeveloperProfile
import zed.rainxch.devprofile.domain.model.DeveloperRepository
import zed.rainxch.devprofile.domain.repository.DeveloperProfileRepository

class DeveloperProfileRepositoryImpl(
    private val httpClient: HttpClient,
    private val platform: Platform,
    private val installedAppsDao: InstalledAppDao,
    private val favouritesRepository: FavouritesRepository,
    private val logger: GitHubStoreLogger,
) : DeveloperProfileRepository {
    override suspend fun getDeveloperProfile(username: String): Result<DeveloperProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get("/users/$username")

                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch developer profile: ${response.status.description}"),
                    )
                }

                val userResponse: GitHubUserResponse = response.body()
                Result.success(userResponse.toDomain())
            } catch (e: RateLimitException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to fetch developer profile for $username")
                Result.failure(e)
            }
        }
    }

    override suspend fun getDeveloperRepositories(username: String): Result<List<DeveloperRepository>> {
        return withContext(Dispatchers.IO) {
            try {
                val allRepos = mutableListOf<GitHubRepoResponse>()
                var page = 1
                val perPage = 100

                while (true) {
                    val response =
                        httpClient.get("/users/$username/repos") {
                            parameter("per_page", perPage)
                            parameter("page", page)
                            parameter("type", "owner")
                            parameter("sort", "updated")
                            parameter("direction", "desc")
                        }

                    if (!response.status.isSuccess()) {
                        return@withContext Result.failure(
                            Exception("Failed to fetch repositories: ${response.status.description}"),
                        )
                    }

                    val repos: List<GitHubRepoResponse> = response.body()

                    if (repos.isEmpty()) break

                    allRepos.addAll(repos.filter { !it.archived && !it.fork })

                    if (repos.size < perPage) break
                    page++
                }

                val allFavorites = favouritesRepository.getAllFavorites().first()
                val favoriteIds = allFavorites.map { it.repoId }.toSet()

                val processedRepos =
                    coroutineScope {
                        val semaphore = Semaphore(20)
                        val deferredResults =
                            allRepos.map { repo ->
                                async {
                                    semaphore.withPermit {
                                        processRepository(repo, favoriteIds)
                                    }
                                }
                            }
                        deferredResults.awaitAll()
                    }

                Result.success(processedRepos)
            } catch (e: RateLimitException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to fetch repositories for $username")
                Result.failure(e)
            }
        }
    }

    private suspend fun processRepository(
        repo: GitHubRepoResponse,
        favoriteIds: Set<Long>,
    ): DeveloperRepository {
        val installedApp = installedAppsDao.getAppByRepoId(repo.id)
        val isFavorite = favoriteIds.contains(repo.id)

        val (hasReleases, hasInstallableAssets, latestVersion) =
            checkReleaseInfo(
                owner = repo.fullName.split("/")[0],
                repoName = repo.name,
            )

        return repo.toDomain(
            hasReleases = hasReleases,
            hasInstallableAssets = hasInstallableAssets,
            isInstalled = installedApp != null,
            isFavorite = isFavorite,
            latestVersion = latestVersion,
        )
    }

    private suspend fun checkReleaseInfo(
        owner: String,
        repoName: String,
    ): Triple<Boolean, Boolean, String?> {
        return try {
            val response =
                httpClient.get("/repos/$owner/$repoName/releases") {
                    parameter("per_page", 10)
                }

            if (!response.status.isSuccess()) {
                return Triple(false, false, null)
            }

            val releases: List<ReleaseNetworkModel> = response.body()

            val stableRelease =
                releases.firstOrNull {
                    it.draft != true && it.prerelease != true
                }

            if (stableRelease == null) {
                return Triple(releases.isNotEmpty(), false, null)
            }

            val hasInstallableAssets =
                stableRelease.assets.any { asset ->
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
                                name.endsWith(".rpm")
                        }
                    }
                }

            Triple(
                true,
                hasInstallableAssets,
                if (hasInstallableAssets) stableRelease.tagName else null,
            )
        } catch (e: RateLimitException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to check releases for $owner/$repoName : ${e.message}")
            Triple(false, false, null)
        }
    }

    @Serializable
    private data class ReleaseNetworkModel(
        val assets: List<AssetNetworkModel>,
        val draft: Boolean? = null,
        val prerelease: Boolean? = null,
        @SerialName("tag_name") val tagName: String,
        @SerialName("published_at") val publishedAt: String? = null,
    )

    @Serializable
    private data class AssetNetworkModel(
        val name: String,
    )
}
