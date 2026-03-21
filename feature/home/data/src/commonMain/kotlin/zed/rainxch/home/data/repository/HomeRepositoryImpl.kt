@file:OptIn(ExperimentalTime::class)

package zed.rainxch.home.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.HOME_REPOS
import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.data.dto.GithubRepoSearchResponse
import zed.rainxch.core.data.mappers.toSummary
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.DiscoveryPlatform
import zed.rainxch.core.domain.model.GithubRepoSummary
import zed.rainxch.core.domain.model.PaginatedDiscoveryRepositories
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.home.data.data_source.CachedRepositoriesDataSource
import zed.rainxch.home.data.mappers.toGithubRepoSummary
import zed.rainxch.home.domain.repository.HomeRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

class HomeRepositoryImpl(
    private val httpClient: HttpClient,
    private val devicePlatform: Platform,
    private val cachedDataSource: CachedRepositoriesDataSource,
    private val logger: GitHubStoreLogger,
    private val cacheManager: CacheManager,
) : HomeRepository {
    private fun cacheKey(
        category: String,
        requestedPlatform: DiscoveryPlatform,
        page: Int,
    ): String = "home:$category:${requestedPlatform.name}:page$page"

    @OptIn(ExperimentalTime::class)
    override fun getTrendingRepositories(
        platform: DiscoveryPlatform,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> =
        flow {
            if (page == 1) {
                logger.debug("Attempting to load cached trending repositories...")

                val cachedData = cachedDataSource.getCachedTrendingRepos(platform)

                if (cachedData != null && cachedData.repositories.isNotEmpty()) {
                    logger.debug("Using mirror cached data: ${cachedData.repositories.size} repos")

                    val repos = cachedData.repositories.map { it.toGithubRepoSummary() }

                    val result =
                        PaginatedDiscoveryRepositories(
                            repos = repos,
                            hasMore = false,
                            nextPageIndex = 2,
                        )
                    cacheManager.put(
                        key =
                            cacheKey(
                                category = "trending",
                                requestedPlatform = platform,
                                page = page,
                            ),
                        value = result,
                        ttlMillis = HOME_REPOS,
                    )
                    emit(result)
                    return@flow
                } else {
                    logger.debug("No mirror data, checking local cache...")
                }
            }

            val localCached =
                cacheManager.get<PaginatedDiscoveryRepositories>(
                    cacheKey(
                        category = "trending",
                        requestedPlatform = platform,
                        page = page,
                    ),
                )
            if (localCached != null && localCached.repos.isNotEmpty()) {
                logger.debug("Using locally cached trending repos: ${localCached.repos.size}")
                emit(localCached)
                return@flow
            }

            val thirtyDaysAgo =
                Clock.System
                    .now()
                    .minus(30.days)
                    .toLocalDateTime(TimeZone.UTC)
                    .date

            emitAll(
                searchReposWithInstallersFlow(
                    platform = platform,
                    baseQuery = "stars:>50 archived:false pushed:>=$thirtyDaysAgo",
                    sort = "stars",
                    order = "desc",
                    startPage = page,
                    category = "trending",
                ),
            )
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalTime::class)
    override fun getHotReleaseRepositories(
        platform: DiscoveryPlatform,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> =
        flow {
            if (page == 1) {
                logger.debug("Attempting to load cached hot release repositories...")

                val cachedData = cachedDataSource.getCachedHotReleaseRepos(platform)

                if (cachedData != null && cachedData.repositories.isNotEmpty()) {
                    logger.debug("Using mirror cached data: ${cachedData.repositories.size} repos")

                    val repos = cachedData.repositories.map { it.toGithubRepoSummary() }

                    val result =
                        PaginatedDiscoveryRepositories(
                            repos = repos,
                            hasMore = false,
                            nextPageIndex = 2,
                        )
                    cacheManager.put(
                        key =
                            cacheKey(
                                category = "hot_release",
                                requestedPlatform = platform,
                                page = page,
                            ),
                        value = result,
                        ttlMillis = HOME_REPOS,
                    )
                    emit(result)
                    return@flow
                } else {
                    logger.debug("No mirror data, checking local cache...")
                }
            }

            val localCached =
                cacheManager.get<PaginatedDiscoveryRepositories>(
                    cacheKey(
                        category = "hot_release",
                        requestedPlatform = platform,
                        page = page,
                    ),
                )
            if (localCached != null && localCached.repos.isNotEmpty()) {
                logger.debug("Using locally cached hot release repos: ${localCached.repos.size}")
                emit(localCached)
                return@flow
            }

            val fourteenDaysAgo =
                Clock.System
                    .now()
                    .minus(14.days)
                    .toLocalDateTime(TimeZone.UTC)
                    .date

            emitAll(
                searchReposWithInstallersFlow(
                    platform = platform,
                    baseQuery = "stars:>10 archived:false pushed:>=$fourteenDaysAgo",
                    sort = "updated",
                    order = "desc",
                    startPage = page,
                    category = "hot_release",
                ),
            )
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalTime::class)
    override fun getMostPopular(
        platform: DiscoveryPlatform,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> =
        flow {
            if (page == 1) {
                logger.debug("Attempting to load cached most popular repositories...")

                val cachedData = cachedDataSource.getCachedMostPopularRepos(platform)

                if (cachedData != null && cachedData.repositories.isNotEmpty()) {
                    logger.debug("Using mirror cached data: ${cachedData.repositories.size} repos")

                    val repos = cachedData.repositories.map { it.toGithubRepoSummary() }

                    val result =
                        PaginatedDiscoveryRepositories(
                            repos = repos,
                            hasMore = false,
                            nextPageIndex = 2,
                        )
                    cacheManager.put(cacheKey("most_popular", platform, page), result, HOME_REPOS)
                    emit(result)
                    return@flow
                } else {
                    logger.debug("No mirror data, checking local cache...")
                }
            }

            val localCached =
                cacheManager.get<PaginatedDiscoveryRepositories>(
                    cacheKey(
                        category = "most_popular",
                        requestedPlatform = platform,
                        page = page,
                    ),
                )
            if (localCached != null && localCached.repos.isNotEmpty()) {
                logger.debug("Using locally cached most popular repos: ${localCached.repos.size}")
                emit(localCached)
                return@flow
            }

            val sixMonthsAgo =
                Clock.System
                    .now()
                    .minus(180.days)
                    .toLocalDateTime(TimeZone.UTC)
                    .date

            val oneYearAgo =
                Clock.System
                    .now()
                    .minus(365.days)
                    .toLocalDateTime(TimeZone.UTC)
                    .date

            emitAll(
                searchReposWithInstallersFlow(
                    platform = platform,
                    baseQuery = "stars:>1000 archived:false created:<$sixMonthsAgo pushed:>=$oneYearAgo",
                    sort = "stars",
                    order = "desc",
                    startPage = page,
                    category = "most_popular",
                ),
            )
        }.flowOn(Dispatchers.IO)

    override fun getTopicRepositories(
        topic: zed.rainxch.home.domain.model.TopicCategory,
        platform: DiscoveryPlatform,
    ): Flow<PaginatedDiscoveryRepositories> =
        flow {
            val cachedData = cachedDataSource.getCachedTopicRepos(topic, platform)

            if (cachedData != null && cachedData.repositories.isNotEmpty()) {
                logger.debug("Using cached topic data for ${topic.name}: ${cachedData.repositories.size} repos")

                val repos = cachedData.repositories.map { it.toGithubRepoSummary() }
                emit(
                    PaginatedDiscoveryRepositories(
                        repos = repos,
                        hasMore = false,
                        nextPageIndex = 2,
                    ),
                )
            }
        }.flowOn(Dispatchers.IO)

    override fun searchByTopic(
        searchKeywords: String,
        platform: DiscoveryPlatform,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> =
        flow {
            val cacheCategory = "topic_${searchKeywords.hashCode()}"
            val localCached =
                cacheManager.get<PaginatedDiscoveryRepositories>(
                    cacheKey(
                        category = cacheCategory,
                        requestedPlatform = platform,
                        page = page,
                    ),
                )
            if (localCached != null && localCached.repos.isNotEmpty()) {
                logger.debug("Using cached topic repos: ${localCached.repos.size}")
                emit(localCached)
                return@flow
            }

            emitAll(
                searchReposWithInstallersFlow(
                    platform = platform,
                    baseQuery = "$searchKeywords in:name,description,topics stars:>10 archived:false",
                    sort = "stars",
                    order = "desc",
                    startPage = page,
                    category = cacheCategory,
                ),
            )
        }.flowOn(Dispatchers.IO)

    private fun searchReposWithInstallersFlow(
        platform: DiscoveryPlatform,
        baseQuery: String,
        sort: String,
        order: String,
        startPage: Int,
        category: String,
        desiredCount: Int = 10,
    ): Flow<PaginatedDiscoveryRepositories> =
        flow {
            val results = mutableListOf<GithubRepoSummary>()
            var currentApiPage = startPage
            val perPage = 100
            val semaphore = Semaphore(25)
            val maxPagesToFetch = 5
            var pagesFetchedCount = 0
            var lastEmittedCount = 0

            val query = buildSimplifiedQuery(baseQuery, platform)
            logger.debug("Query: $query | Sort: $sort | Page: $startPage")

            while (results.size < desiredCount && pagesFetchedCount < maxPagesToFetch) {
                currentCoroutineContext().ensureActive()

                try {
                    val response =
                        httpClient
                            .executeRequest<GithubRepoSearchResponse> {
                                get("/search/repositories") {
                                    parameter("q", query)
                                    parameter("sort", sort)
                                    parameter("order", order)
                                    parameter("per_page", perPage)
                                    parameter("page", currentApiPage)
                                }
                            }.getOrElse { error ->
                                logger.error("Search request failed: ${error.message}")
                                throw error
                            }

                    logger.debug("API Page $currentApiPage: Got ${response.items.size} repos")

                    if (response.items.isEmpty()) {
                        logger.debug("No more items from API, breaking")
                        break
                    }

                    val candidates =
                        response.items
                            .map { repo -> repo to calculatePlatformScore(repo) }
                            .filter { it.second > 0 }
                            .take(50)
                            .map { it.first }

                    logger.debug("Checking ${candidates.size} candidates for installers")

                    coroutineScope {
                        val deferredResults =
                            candidates.map { repo ->
                                async {
                                    semaphore.withPermit {
                                        withTimeoutOrNull(5000) {
                                            checkRepoHasInstallers(repo)
                                        }
                                    }
                                }
                            }

                        for (deferred in deferredResults) {
                            currentCoroutineContext().ensureActive()

                            val result = deferred.await()
                            if (result != null) {
                                results.add(result)
                                logger.debug("Found installer repo: ${result.fullName} (${results.size}/$desiredCount)")

                                if (results.size % 3 == 0 || results.size >= desiredCount) {
                                    val newItems = results.subList(lastEmittedCount, results.size)

                                    if (newItems.isNotEmpty()) {
                                        val paginatedResult =
                                            PaginatedDiscoveryRepositories(
                                                repos = newItems.toList(),
                                                hasMore = true,
                                                nextPageIndex = currentApiPage + 1,
                                            )
                                        emit(paginatedResult)
                                        logger.debug("Emitted ${newItems.size} repos (total: ${results.size})")
                                        lastEmittedCount = results.size
                                    }
                                }

                                if (results.size >= desiredCount) {
                                    logger.debug("Reached desired count, breaking")
                                    break
                                }
                            }
                        }
                    }

                    if (results.size >= desiredCount || response.items.size < perPage) {
                        logger.debug("Breaking: results=${results.size}, response size=${response.items.size}")
                        break
                    }

                    currentApiPage++
                    pagesFetchedCount++
                } catch (_: RateLimitException) {
                    logger.error("Rate limited during search")
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Search failed: ${e.message}")
                    e.printStackTrace()
                    break
                }
            }

            if (results.size > lastEmittedCount) {
                val finalBatch = results.subList(lastEmittedCount, results.size)
                val finalHasMore =
                    pagesFetchedCount < maxPagesToFetch && results.size >= desiredCount
                val finalResult =
                    PaginatedDiscoveryRepositories(
                        repos = finalBatch.toList(),
                        hasMore = finalHasMore,
                        nextPageIndex = if (finalHasMore) currentApiPage + 1 else currentApiPage,
                    )
                emit(finalResult)
                logger.debug("Final emit: ${finalBatch.size} repos (total: ${results.size})")
            } else if (results.isEmpty()) {
                emit(
                    PaginatedDiscoveryRepositories(
                        repos = emptyList(),
                        hasMore = false,
                        nextPageIndex = currentApiPage,
                    ),
                )
                logger.debug("No results found")
            }

            if (results.isNotEmpty()) {
                val allResults =
                    PaginatedDiscoveryRepositories(
                        repos = results.toList(),
                        hasMore = pagesFetchedCount < maxPagesToFetch && results.size >= desiredCount,
                        nextPageIndex = currentApiPage + 1,
                    )
                cacheManager.put(
                    key =
                        cacheKey(
                            category = category,
                            requestedPlatform = platform,
                            page = startPage,
                        ),
                    value = allResults,
                    ttlMillis = HOME_REPOS,
                )
                logger.debug("Cached ${results.size} repos for $category page $startPage")
            }
        }.flowOn(Dispatchers.IO)

    private fun buildSimplifiedQuery(
        baseQuery: String,
        requestedPlatform: DiscoveryPlatform,
    ): String {
        val topic =
            when (requestedPlatform) {
                DiscoveryPlatform.All -> null
                DiscoveryPlatform.Android -> "android"
                DiscoveryPlatform.Windows -> "desktop"
                DiscoveryPlatform.Macos -> "macos"
                DiscoveryPlatform.Linux -> "linux"
            }

        return if (topic == null) baseQuery else "$baseQuery topic:$topic"
    }

    private fun calculatePlatformScore(repo: GithubRepoNetworkModel): Int {
        var score = 5
        val topics = repo.topics.orEmpty().map { it.lowercase() }
        val language = repo.language?.lowercase()
        val desc = repo.description?.lowercase() ?: ""

        when (devicePlatform) {
            Platform.ANDROID -> {
                if (topics.contains("android")) score += 10
                if (topics.contains("mobile")) score += 5
                if (language == "kotlin" || language == "java") score += 5
                if (desc.contains("android") || desc.contains("apk")) score += 3
            }

            Platform.WINDOWS, Platform.MACOS, Platform.LINUX -> {
                if (topics.any {
                        it in
                            setOf(
                                "desktop",
                                "electron",
                                "app",
                                "gui",
                                "compose-desktop",
                            )
                    }
                ) {
                    score += 10
                }
                if (topics.contains("cross-platform") || topics.contains("multiplatform")) score += 8
                if (language in setOf("kotlin", "c++", "rust", "c#", "swift", "dart")) score += 5
                if (desc.contains("desktop") || desc.contains("application")) score += 3
            }
        }

        return score
    }

    private suspend fun checkRepoHasInstallers(repo: GithubRepoNetworkModel): GithubRepoSummary? {
        return try {
            val allReleases =
                httpClient
                    .executeRequest<List<GithubReleaseNetworkModel>> {
                        get("/repos/${repo.owner.login}/${repo.name}/releases") {
                            header("Accept", "application/vnd.github.v3+json")
                            parameter("per_page", 10)
                        }
                    }.getOrNull() ?: return null

            val stableRelease =
                allReleases.firstOrNull {
                    it.draft != true && it.prerelease != true
                }

            if (stableRelease == null || stableRelease.assets.isEmpty()) {
                return null
            }

            val relevantAssets =
                stableRelease.assets.filter { asset ->
                    val name = asset.name.lowercase()
                    when (devicePlatform) {
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

            if (relevantAssets.isNotEmpty()) {
                repo.toSummary()
            } else {
                null
            }
        } catch (_: Exception) {
            null
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
