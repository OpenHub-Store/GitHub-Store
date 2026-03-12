package zed.rainxch.search.data.repository

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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.SEARCH_RESULTS
import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.data.dto.GithubRepoSearchResponse
import zed.rainxch.core.data.mappers.toSummary
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.model.GithubRepoSummary
import zed.rainxch.core.domain.model.PaginatedDiscoveryRepositories
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.domain.model.ProgrammingLanguage
import zed.rainxch.domain.model.SearchPlatform
import zed.rainxch.domain.model.SortBy
import zed.rainxch.domain.model.SortOrder
import zed.rainxch.domain.repository.SearchRepository
import zed.rainxch.search.data.dto.GithubReleaseNetworkModel
import zed.rainxch.search.data.utils.LruCache

class SearchRepositoryImpl(
    private val httpClient: HttpClient,
    private val cacheManager: CacheManager,
) : SearchRepository {
    private val releaseCheckCache = LruCache<String, GithubRepoSummary>(maxSize = 500)
    private val cacheMutex = Mutex()

    companion object {
        private const val PER_PAGE = 100
        private const val VERIFY_CONCURRENCY = 15
        private const val PER_CHECK_TIMEOUT_MS = 2000L
        private const val MAX_AUTO_SKIP_PAGES = 3
    }

    private fun searchCacheKey(
        query: String,
        platform: SearchPlatform,
        language: ProgrammingLanguage,
        sortBy: SortBy,
        sortOrder: SortOrder,
        page: Int,
    ): String {
        val queryHash =
            query
                .trim()
                .lowercase()
                .hashCode()
                .toUInt()
                .toString(16)
        return "search:$queryHash:${platform.name}:${language.name}:${sortBy.name}:${sortOrder.name}:page$page"
    }

    override fun searchRepositories(
        query: String,
        searchPlatform: SearchPlatform,
        language: ProgrammingLanguage,
        sortBy: SortBy,
        sortOrder: SortOrder,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> =
        channelFlow {
            val cacheKey = searchCacheKey(query, searchPlatform, language, sortBy, sortOrder, page)

            val cached = cacheManager.get<PaginatedDiscoveryRepositories>(cacheKey)
            if (cached != null) {
                send(cached)
                return@channelFlow
            }

            val searchQuery = buildSearchQuery(query, language)
            val sort = sortBy.toGithubSortParam()
            val order = sortOrder.toGithubParam()

            try {
                var currentPage = page
                var pagesSkipped = 0

                while (pagesSkipped <= MAX_AUTO_SKIP_PAGES) {
                    currentCoroutineContext().ensureActive()

                    val response =
                        httpClient
                            .executeRequest<GithubRepoSearchResponse> {
                                get("/search/repositories") {
                                    parameter("q", searchQuery)
                                    parameter("per_page", PER_PAGE)
                                    parameter("page", currentPage)
                                    if (sort != null) {
                                        parameter("sort", sort)
                                        parameter("order", order)
                                    }
                                }
                            }.getOrThrow()

                    val total = response.totalCount
                    val baseHasMore = (currentPage * PER_PAGE) < total && response.items.isNotEmpty()

                    if (response.items.isEmpty()) {
                        send(
                            PaginatedDiscoveryRepositories(
                                repos = emptyList(),
                                hasMore = false,
                                nextPageIndex = currentPage + 1,
                                totalCount = total,
                            ),
                        )
                        return@channelFlow
                    }

                    val verified = verifyBatch(response.items, searchPlatform)

                    if (verified.isNotEmpty()) {
                        val result =
                            PaginatedDiscoveryRepositories(
                                repos = verified,
                                hasMore = baseHasMore,
                                nextPageIndex = currentPage + 1,
                                totalCount = total,
                            )
                        cacheManager.put(cacheKey, result, SEARCH_RESULTS)
                        send(result)
                        return@channelFlow
                    }

                    if (!baseHasMore) {
                        send(
                            PaginatedDiscoveryRepositories(
                                repos = emptyList(),
                                hasMore = false,
                                nextPageIndex = currentPage + 1,
                                totalCount = total,
                            ),
                        )
                        return@channelFlow
                    }

                    currentPage++
                    pagesSkipped++
                }

                send(
                    PaginatedDiscoveryRepositories(
                        repos = emptyList(),
                        hasMore = true,
                        nextPageIndex = currentPage + 1,
                        totalCount = null,
                    ),
                )
            } catch (e: RateLimitException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun verifyBatch(
        items: List<GithubRepoNetworkModel>,
        searchPlatform: SearchPlatform,
    ): List<GithubRepoSummary> {
        val semaphore = Semaphore(VERIFY_CONCURRENCY)

        val deferredChecks =
            coroutineScope {
                items.map { repo ->
                    async {
                        try {
                            semaphore.withPermit {
                                withTimeoutOrNull(PER_CHECK_TIMEOUT_MS) {
                                    checkRepoHasInstallersCached(repo, searchPlatform)
                                }
                            }
                        } catch (_: CancellationException) {
                            null
                        }
                    }
                }
            }

        return buildList {
            for (i in items.indices) {
                currentCoroutineContext().ensureActive()
                val result =
                    try {
                        deferredChecks[i].await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                if (result != null) add(result)
            }
        }
    }

    private fun buildSearchQuery(
        userQuery: String,
        language: ProgrammingLanguage,
    ): String {
        val clean = userQuery.trim()
        val q =
            if (clean.isBlank()) {
                "stars:>100"
            } else {
                "\"$clean\""
            }
        val scope = " in:name,description"
        val common = " archived:false fork:true"

        val languageFilter =
            if (language != ProgrammingLanguage.All && language.queryValue != null) {
                " language:${language.queryValue}"
            } else {
                ""
            }

        return ("$q$scope$common" + languageFilter).trim()
    }

    private fun assetMatchesPlatform(
        nameRaw: String,
        platform: SearchPlatform,
    ): Boolean {
        val name = nameRaw.lowercase()
        return when (platform) {
            SearchPlatform.All -> {
                name.endsWith(".apk") ||
                    name.endsWith(".msi") || name.endsWith(".exe") ||
                    name.endsWith(".dmg") || name.endsWith(".pkg") ||
                    name.endsWith(".appimage") || name.endsWith(".deb") || name.endsWith(".rpm")
            }

            SearchPlatform.Android -> {
                name.endsWith(".apk")
            }

            SearchPlatform.Windows -> {
                name.endsWith(".exe") || name.endsWith(".msi")
            }

            SearchPlatform.Macos -> {
                name.endsWith(".dmg") || name.endsWith(".pkg")
            }

            SearchPlatform.Linux -> {
                name.endsWith(".appimage") || name.endsWith(".deb") || name.endsWith(".rpm")
            }
        }
    }

    private fun detectAvailablePlatforms(assetNames: List<String>): List<String> {
        val platforms = mutableListOf<String>()
        val allPlatforms =
            listOf(
                SearchPlatform.Android to "Android",
                SearchPlatform.Windows to "Windows",
                SearchPlatform.Macos to "macOS",
                SearchPlatform.Linux to "Linux",
            )
        for ((platform, label) in allPlatforms) {
            if (assetNames.any { assetMatchesPlatform(it, platform) }) {
                platforms.add(label)
            }
        }
        return platforms
    }

    private suspend fun checkRepoHasInstallers(
        repo: GithubRepoNetworkModel,
        targetPlatform: SearchPlatform,
    ): GithubRepoSummary? {
        return try {
            val allReleases =
                httpClient
                    .executeRequest<List<GithubReleaseNetworkModel>> {
                        get("/repos/${repo.owner.login}/${repo.name}/releases") {
                            header("Accept", "application/vnd.github.v3+json")
                            parameter("per_page", 5)
                        }
                    }.getOrNull() ?: return null

            val stableRelease =
                allReleases.firstOrNull {
                    it.draft != true && it.prerelease != true
                }

            if (stableRelease == null || stableRelease.assets.isEmpty()) {
                return null
            }

            val hasRelevantAssets =
                stableRelease.assets.any { asset ->
                    assetMatchesPlatform(asset.name, targetPlatform)
                }

            if (hasRelevantAssets) {
                val assetNames = stableRelease.assets.map { it.name }
                val platforms = detectAvailablePlatforms(assetNames)
                val summary = repo.toSummary()
                summary.copy(
                    updatedAt = stableRelease.publishedAt ?: summary.updatedAt,
                    availablePlatforms = platforms,
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun checkRepoHasInstallersCached(
        repo: GithubRepoNetworkModel,
        targetPlatform: SearchPlatform,
    ): GithubRepoSummary? {
        val key = "${repo.owner.login}/${repo.name}:LATEST_PLATFORM_${targetPlatform.name}"
        val cached =
            cacheMutex.withLock {
                if (releaseCheckCache.contains(key)) releaseCheckCache.get(key) else null
            }
        if (cached != null ||
            cacheMutex.withLock {
                releaseCheckCache.contains(key) && releaseCheckCache.get(key) == null
            }
        ) {
            return cached
        }

        val result = checkRepoHasInstallers(repo, targetPlatform)
        cacheMutex.withLock {
            releaseCheckCache.put(key, result)
        }
        return result
    }
}
