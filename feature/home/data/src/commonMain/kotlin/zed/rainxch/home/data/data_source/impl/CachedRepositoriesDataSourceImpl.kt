package zed.rainxch.home.data.data_source.impl

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.DiscoveryPlatform
import zed.rainxch.home.data.data_source.CachedRepositoriesDataSource
import zed.rainxch.home.data.dto.CachedGithubRepoSummary
import zed.rainxch.home.data.dto.CachedRepoResponse
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.domain.model.TopicCategory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CachedRepositoriesDataSourceImpl(
    private val logger: GitHubStoreLogger,
) : CachedRepositoriesDataSource {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val httpClient =
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 10_000
            }
            expectSuccess = false
        }

    private val cacheMutex = Mutex()
    private val memoryCache = mutableMapOf<CacheKey, CacheEntry>()
    private val topicMemoryCache = mutableMapOf<TopicCacheKey, CacheEntry>()

    private data class CacheEntry(
        val data: CachedRepoResponse,
        val fetchedAt: Instant,
    )

    override suspend fun getCachedTrendingRepos(platform: DiscoveryPlatform): CachedRepoResponse? =
        fetchCachedReposForCategory(platform, HomeCategory.TRENDING)

    override suspend fun getCachedHotReleaseRepos(platform: DiscoveryPlatform): CachedRepoResponse? =
        fetchCachedReposForCategory(platform, HomeCategory.HOT_RELEASE)

    override suspend fun getCachedMostPopularRepos(platform: DiscoveryPlatform): CachedRepoResponse? =
        fetchCachedReposForCategory(platform, HomeCategory.MOST_POPULAR)

    override suspend fun getCachedTopicRepos(
        topic: TopicCategory,
        platform: DiscoveryPlatform,
    ): CachedRepoResponse? {
        val topicFolder = when (topic) {
            TopicCategory.PRIVACY -> "privacy"
            TopicCategory.MEDIA -> "media"
            TopicCategory.PRODUCTIVITY -> "productivity"
            TopicCategory.NETWORKING -> "networking"
            TopicCategory.DEV_TOOLS -> "dev-tools"
        }

        val topicCacheKey = TopicCacheKey(topic, platform)
        val cached = cacheMutex.withLock { topicMemoryCache[topicCacheKey] }
        if (cached != null) {
            val age = Clock.System.now() - cached.fetchedAt
            if (age < CACHE_TTL) {
                logger.debug("Topic memory cache hit for $topicCacheKey (age: ${age.inWholeSeconds}s)")
                return cached.data
            }
        }

        return withContext(Dispatchers.IO) {
            val paths = listOf(
                "cached-data/topics/$topicFolder/android.json",
                "cached-data/topics/$topicFolder/windows.json",
                "cached-data/topics/$topicFolder/macos.json",
                "cached-data/topics/$topicFolder/linux.json",
            )

            val responses = coroutineScope {
                paths.map { path ->
                    async {
                        val url = "https://raw.githubusercontent.com/OpenHub-Store/api/main/$path"
                        val filePlatform = when {
                            path.contains("/android") -> DiscoveryPlatform.Android
                            path.contains("/windows") -> DiscoveryPlatform.Windows
                            path.contains("/macos") -> DiscoveryPlatform.Macos
                            path.contains("/linux") -> DiscoveryPlatform.Linux
                            else -> error("Unknown platform in path: $path")
                        }
                        try {
                            logger.debug("Fetching topic cache: $url")
                            val response: HttpResponse = httpClient.get(url)
                            if (response.status.isSuccess()) {
                                json.decodeFromString<CachedRepoResponse>(response.bodyAsText())
                                    .let { repoResponse ->
                                        repoResponse.copy(
                                            repositories = repoResponse.repositories.map {
                                                it.copy(availablePlatforms = listOf(filePlatform))
                                            },
                                        )
                                    }
                            } else {
                                logger.error("HTTP ${response.status.value} from $url")
                                null
                            }
                        } catch (e: SerializationException) {
                            logger.error("Parse error from $url: ${e.message}")
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.error("Error with $url: ${e.message}")
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (responses.isEmpty()) {
                logger.error("All topic mirrors failed for $topicCacheKey")
                return@withContext null
            }

            val allMergedRepos = responses
                .asSequence()
                .flatMap { it.repositories.asSequence() }
                .groupBy { it.id }
                .values
                .map { duplicates ->
                    duplicates.reduce { acc, repo ->
                        acc.copy(
                            availablePlatforms = (acc.availablePlatforms + repo.availablePlatforms).distinct(),
                            latestReleaseDate = listOfNotNull(
                                acc.latestReleaseDate,
                                repo.latestReleaseDate,
                            ).maxOrNull(),
                        )
                    }
                }
                .sortedByDescending { it.stargazersCount }

            val filteredRepos = when (platform) {
                DiscoveryPlatform.All -> allMergedRepos
                else -> allMergedRepos.filter { platform in it.availablePlatforms }
            }.toList()

            val merged = CachedRepoResponse(
                category = "topic",
                platform = platform.name.lowercase(),
                lastUpdated = responses.maxOf { it.lastUpdated },
                totalCount = filteredRepos.size,
                repositories = filteredRepos,
            )

            if (responses.size == paths.size) {
                cacheMutex.withLock {
                    topicMemoryCache[topicCacheKey] =
                        CacheEntry(data = merged, fetchedAt = Clock.System.now())
                }
            }

            merged
        }
    }

    private suspend fun fetchCachedReposForCategory(
        platform: DiscoveryPlatform,
        category: HomeCategory,
    ): CachedRepoResponse? {
        val cacheKey = CacheKey(platform, category)

        val cached = cacheMutex.withLock { memoryCache[cacheKey] }
        if (cached != null) {
            val age = Clock.System.now() - cached.fetchedAt
            if (age < CACHE_TTL) {
                logger.debug("Memory cache hit for $cacheKey (age: ${age.inWholeSeconds}s)")
                return cached.data
            } else {
                logger.debug("Memory cache expired for $cacheKey (age: ${age.inWholeSeconds}s)")
            }
        }

        return withContext(Dispatchers.IO) {
            val paths =
                when (category) {
                    HomeCategory.TRENDING -> {
                        listOf(
                            "cached-data/trending/android.json",
                            "cached-data/trending/windows.json",
                            "cached-data/trending/macos.json",
                            "cached-data/trending/linux.json",
                        )
                    }

                    HomeCategory.HOT_RELEASE -> {
                        listOf(
                            "cached-data/new-releases/android.json",
                            "cached-data/new-releases/windows.json",
                            "cached-data/new-releases/macos.json",
                            "cached-data/new-releases/linux.json",
                        )
                    }

                    HomeCategory.MOST_POPULAR -> {
                        listOf(
                            "cached-data/most-popular/android.json",
                            "cached-data/most-popular/windows.json",
                            "cached-data/most-popular/macos.json",
                            "cached-data/most-popular/linux.json",
                        )
                    }
                }

            val responses =
                coroutineScope {
                    paths
                        .map { path ->
                            async {
                                val url = "https://raw.githubusercontent.com/OpenHub-Store/api/main/$path"
                                val filePlatform =
                                    when {
                                        path.contains("/android") -> DiscoveryPlatform.Android
                                        path.contains("/windows") -> DiscoveryPlatform.Windows
                                        path.contains("/macos") -> DiscoveryPlatform.Macos
                                        path.contains("/linux") -> DiscoveryPlatform.Linux
                                        else -> error("Unknown platform in path: $path")
                                    }
                                try {
                                    logger.debug("Fetching from: $url")
                                    val response: HttpResponse = httpClient.get(url)
                                    if (response.status.isSuccess()) {
                                        json
                                            .decodeFromString<CachedRepoResponse>(response.bodyAsText())
                                            .let { repoResponse ->
                                                repoResponse.copy(
                                                    repositories =
                                                        repoResponse.repositories.map {
                                                            it.copy(availablePlatforms = listOf(filePlatform))
                                                        },
                                                )
                                            }
                                    } else {
                                        logger.error("HTTP ${response.status.value} from $url")
                                        null
                                    }
                                } catch (e: SerializationException) {
                                    logger.error("Parse error from $url: ${e.message}")
                                    null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logger.error("Error with $url: ${e.message}")
                                    null
                                }
                            }
                        }.awaitAll()
                        .filterNotNull()
                }

            if (responses.isEmpty()) {
                logger.error("All mirrors failed for $cacheKey")
                return@withContext null
            }

            val allMergedRepos =
                responses
                    .asSequence()
                    .flatMap { it.repositories.asSequence() }
                    .groupBy { it.id }
                    .values
                    .map { duplicates ->
                        duplicates.reduce { acc, repo ->
                            acc.copy(
                                availablePlatforms = (acc.availablePlatforms + repo.availablePlatforms).distinct(),
                                trendingScore =
                                    listOfNotNull(
                                        acc.trendingScore,
                                        repo.trendingScore,
                                    ).maxOrNull(),
                                popularityScore =
                                    listOfNotNull(
                                        acc.popularityScore,
                                        repo.popularityScore,
                                    ).maxOrNull(),
                                latestReleaseDate =
                                    listOfNotNull(
                                        acc.latestReleaseDate,
                                        repo.latestReleaseDate,
                                    ).maxOrNull(),
                            )
                        }
                    }.sortedWith(
                        compareByDescending<CachedGithubRepoSummary> { it.trendingScore }
                            .thenByDescending { it.popularityScore }
                            .thenByDescending { it.latestReleaseDate },
                    )

            val filteredRepos =
                when (platform) {
                    DiscoveryPlatform.All -> allMergedRepos
                    else -> allMergedRepos.filter { platform in it.availablePlatforms }
                }.toList()

            val merged =
                CachedRepoResponse(
                    category = responses.first().category,
                    platform = platform.name.lowercase(),
                    lastUpdated = responses.maxOf { it.lastUpdated },
                    totalCount = filteredRepos.size,
                    repositories = filteredRepos,
                )

            if (responses.size == paths.size) {
                cacheMutex.withLock {
                    memoryCache[cacheKey] =
                        CacheEntry(data = merged, fetchedAt = Clock.System.now())
                }
            }

            merged
        }
    }

    private companion object {
        private val CACHE_TTL = 1.hours
    }

    private data class CacheKey(
        val platform: DiscoveryPlatform,
        val category: HomeCategory,
    )

    private data class TopicCacheKey(
        val topic: TopicCategory,
        val platform: DiscoveryPlatform,
    )
}
