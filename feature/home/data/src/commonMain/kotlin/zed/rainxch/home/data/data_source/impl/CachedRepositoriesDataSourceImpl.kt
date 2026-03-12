package zed.rainxch.home.data.data_source.impl

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.home.data.data_source.CachedRepositoriesDataSource
import zed.rainxch.home.data.dto.CachedRepoResponse
import zed.rainxch.home.domain.model.HomeCategory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CachedRepositoriesDataSourceImpl(
    private val platform: Platform,
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
    private val memoryCache = mutableMapOf<HomeCategory, CacheEntry>()

    private data class CacheEntry(
        val data: CachedRepoResponse,
        val fetchedAt: Instant,
    )

    override suspend fun getCachedTrendingRepos(): CachedRepoResponse? = fetchCachedReposForCategory(HomeCategory.TRENDING)

    override suspend fun getCachedHotReleaseRepos(): CachedRepoResponse? = fetchCachedReposForCategory(HomeCategory.HOT_RELEASE)

    override suspend fun getCachedMostPopularRepos(): CachedRepoResponse? = fetchCachedReposForCategory(HomeCategory.MOST_POPULAR)

    private suspend fun fetchCachedReposForCategory(category: HomeCategory): CachedRepoResponse? {
        val cached = cacheMutex.withLock { memoryCache[category] }
        if (cached != null) {
            val age = Clock.System.now() - cached.fetchedAt
            if (age < CACHE_TTL) {
                logger.debug("Memory cache hit for $category (age: ${age.inWholeSeconds}s)")
                return cached.data
            } else {
                logger.debug("Memory cache expired for $category (age: ${age.inWholeSeconds}s)")
            }
        }

        return withContext(Dispatchers.IO) {
            val platformName =
                when (platform) {
                    Platform.ANDROID -> "android"
                    Platform.WINDOWS -> "windows"
                    Platform.MACOS -> "macos"
                    Platform.LINUX -> "linux"
                }

            val path =
                when (category) {
                    HomeCategory.TRENDING -> "cached-data/trending/$platformName.json"
                    HomeCategory.HOT_RELEASE -> "cached-data/new-releases/$platformName.json"
                    HomeCategory.MOST_POPULAR -> "cached-data/most-popular/$platformName.json"
                }

            val mirrorUrls =
                listOf(
                    "https://raw.githubusercontent.com/OpenHub-Store/api/main/$path",
                    "https://cdn.jsdelivr.net/gh/OpenHub-Store/api@main/$path",
                    "https://cdn.statically.io/gh/OpenHub-Store/api/main/$path",
                )

            for (url in mirrorUrls) {
                try {
                    logger.debug("Fetching from: $url")
                    val response: HttpResponse = httpClient.get(url)

                    if (response.status.isSuccess()) {
                        val responseText = response.bodyAsText()
                        val parsed = json.decodeFromString<CachedRepoResponse>(responseText)

                        cacheMutex.withLock {
                            memoryCache[category] =
                                CacheEntry(
                                    data = parsed,
                                    fetchedAt = Clock.System.now(),
                                )
                        }

                        return@withContext parsed
                    } else {
                        logger.error("HTTP ${response.status.value} from $url")
                    }
                } catch (e: SerializationException) {
                    logger.error("Parse error from $url: ${e.message}")
                } catch (e: Exception) {
                    logger.error("Error with $url: ${e.message}")
                }
            }

            logger.error("All mirrors failed for $category")
            null
        }
    }

    private companion object {
        private val CACHE_TTL = 5.minutes
    }
}
