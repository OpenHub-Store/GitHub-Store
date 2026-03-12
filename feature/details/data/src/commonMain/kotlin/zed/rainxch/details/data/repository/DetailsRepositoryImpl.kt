package zed.rainxch.details.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.README
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.RELEASES
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.REPO_DETAILS
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.REPO_STATS
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.USER_PROFILE
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.dto.RepoByIdNetwork
import zed.rainxch.core.data.dto.RepoInfoNetwork
import zed.rainxch.core.data.dto.UserProfileNetwork
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.GithubRelease
import zed.rainxch.core.domain.model.GithubRepoSummary
import zed.rainxch.core.domain.model.GithubUser
import zed.rainxch.core.domain.model.GithubUserProfile
import zed.rainxch.details.data.utils.ReadmeLocalizationHelper
import zed.rainxch.details.data.utils.preprocessMarkdown
import zed.rainxch.details.domain.model.RepoStats
import zed.rainxch.details.domain.repository.DetailsRepository

class DetailsRepositoryImpl(
    private val httpClient: HttpClient,
    private val localizationManager: LocalizationManager,
    private val logger: GitHubStoreLogger,
    private val cacheManager: CacheManager,
) : DetailsRepository {
    @Serializable
    private data class CachedReadme(
        val content: String,
        val languageCode: String?,
        val path: String,
    )

    private val readmeHelper = ReadmeLocalizationHelper(localizationManager)

    private fun RepoByIdNetwork.toGithubRepoSummary(): GithubRepoSummary =
        GithubRepoSummary(
            id = id,
            name = name,
            fullName = fullName,
            owner =
                GithubUser(
                    id = owner.id,
                    login = owner.login,
                    avatarUrl = owner.avatarUrl,
                    htmlUrl = owner.htmlUrl,
                ),
            description = description,
            htmlUrl = htmlUrl,
            stargazersCount = stars,
            forksCount = forks,
            language = language,
            topics = topics,
            releasesUrl = "https://api.github.com/repos/${owner.login}/$name/releases{/id}",
            updatedAt = updatedAt,
            defaultBranch = defaultBranch,
        )

    override suspend fun getRepositoryById(id: Long): GithubRepoSummary {
        val cacheKey = "details:repo_id:$id"

        cacheManager.get<GithubRepoSummary>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for repo id=$id")
            return cached
        }

        return try {
            val result =
                httpClient
                    .executeRequest<RepoByIdNetwork> {
                        get("/repositories/$id") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrThrow()
                    .toGithubRepoSummary()
            cacheManager.put(cacheKey, result, REPO_DETAILS)
            result
        } catch (e: Exception) {
            cacheManager.getStale<GithubRepoSummary>(cacheKey)?.let { stale ->
                logger.debug("Network error, using stale cache for repo id=$id")
                return stale
            }
            throw e
        }
    }

    override suspend fun getRepositoryByOwnerAndName(
        owner: String,
        name: String,
    ): GithubRepoSummary {
        val cacheKey = "details:repo:$owner/$name"

        cacheManager.get<GithubRepoSummary>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for repo $owner/$name")
            return cached
        }

        return try {
            val result =
                httpClient
                    .executeRequest<RepoByIdNetwork> {
                        get("/repos/$owner/$name") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrThrow()
                    .toGithubRepoSummary()

            cacheManager.put(cacheKey, result, REPO_DETAILS)
            result
        } catch (e: Exception) {
            cacheManager.getStale<GithubRepoSummary>(cacheKey)?.let { stale ->
                logger.debug("Network error, using stale cache for $owner/$name")
                return stale
            }
            throw e
        }
    }

    override suspend fun getLatestPublishedRelease(
        owner: String,
        repo: String,
        defaultBranch: String,
    ): GithubRelease? {
        val cacheKey = "details:latest_release:$owner/$repo"

        cacheManager.get<GithubRelease>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for latest release $owner/$repo")
            return cached
        }

        return try {
            val releases =
                httpClient
                    .executeRequest<List<ReleaseNetwork>> {
                        get("/repos/$owner/$repo/releases") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                            parameter("per_page", 10)
                        }
                    }.getOrNull() ?: return null

            val latest =
                releases
                    .asSequence()
                    .filter { (it.draft != true) && (it.prerelease != true) }
                    .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }
                    ?: return null

            val result =
                latest
                    .copy(
                        body = processReleaseBody(latest.body, owner, repo, defaultBranch),
                    ).toDomain()

            cacheManager.put(cacheKey, result, RELEASES)
            result
        } catch (e: Exception) {
            cacheManager.getStale<GithubRelease>(cacheKey)?.let { stale ->
                logger.debug("Network error, using stale cache for latest release $owner/$repo")
                return stale
            }
            throw e
        }
    }

    override suspend fun getAllReleases(
        owner: String,
        repo: String,
        defaultBranch: String,
    ): List<GithubRelease> {
        val cacheKey = "details:releases:$owner/$repo"

        cacheManager.get<List<GithubRelease>>(cacheKey)?.let { cached ->
            if (cached.isNotEmpty()) {
                logger.debug("Cache hit for all releases $owner/$repo: ${cached.size} releases")
                return cached
            }
        }

        return try {
            val releases =
                httpClient
                    .executeRequest<List<ReleaseNetwork>> {
                        get("/repos/$owner/$repo/releases") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                            parameter("per_page", 30)
                        }
                    }.getOrNull() ?: return emptyList()

            val result =
                releases
                    .filter { it.draft != true }
                    .map { release ->
                        release
                            .copy(
                                body = processReleaseBody(release.body, owner, repo, defaultBranch),
                            ).toDomain()
                    }.sortedByDescending { it.publishedAt }

            if (result.isNotEmpty()) {
                cacheManager.put(cacheKey, result, RELEASES)
            }
            result
        } catch (e: Exception) {
            cacheManager.getStale<List<GithubRelease>>(cacheKey)?.let { stale ->
                logger.debug("Network error, using stale cache for releases $owner/$repo")
                return stale
            }
            throw e
        }
    }

    private fun processReleaseBody(
        body: String?,
        owner: String,
        repo: String,
        defaultBranch: String,
    ): String? =
        body
            ?.replace("<details>", "")
            ?.replace("</details>", "")
            ?.replace("<summary>", "")
            ?.replace("</summary>", "")
            ?.replace("\r\n", "\n")
            ?.let { rawMarkdown ->
                preprocessMarkdown(
                    markdown = rawMarkdown,
                    baseUrl = "https://raw.githubusercontent.com/$owner/$repo/$defaultBranch/",
                )
            }

    override suspend fun getReadme(
        owner: String,
        repo: String,
        defaultBranch: String,
    ): Triple<String, String?, String>? {
        val cacheKey = "details:readme:$owner/$repo"

        cacheManager.get<CachedReadme>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for readme $owner/$repo")
            return Triple(cached.content, cached.languageCode, cached.path)
        }

        val result = fetchReadmeFromApi(owner, repo, defaultBranch)

        if (result != null) {
            val cachedReadme =
                CachedReadme(
                    content = result.first,
                    languageCode = result.second,
                    path = result.third,
                )
            cacheManager.put(cacheKey, cachedReadme, README)
        }

        return result
    }

    private suspend fun fetchReadmeFromApi(
        owner: String,
        repo: String,
        defaultBranch: String,
    ): Triple<String, String?, String>? {
        val attempts = readmeHelper.generateReadmeAttempts()
        val baseUrl = "https://raw.githubusercontent.com/$owner/$repo/$defaultBranch/"
        val primaryLang = localizationManager.getPrimaryLanguageCode()

        logger.debug(
            "Attempting to fetch README for language preference: ${localizationManager.getCurrentLanguageCode()}",
        )

        val foundReadmes =
            coroutineScope {
                attempts
                    .map { attempt ->
                        async(start = CoroutineStart.LAZY) {
                            try {
                                logger.debug("Trying ${attempt.path} (priority: ${attempt.priority})...")

                                val rawMarkdown =
                                    httpClient
                                        .executeRequest<String> {
                                            get("$baseUrl${attempt.path}")
                                        }.getOrNull()

                                if (rawMarkdown != null) {
                                    logger.debug("Successfully fetched ${attempt.path}")

                                    val processed =
                                        preprocessMarkdown(
                                            markdown = rawMarkdown,
                                            baseUrl = baseUrl,
                                        )

                                    val detectedLang = readmeHelper.detectReadmeLanguage(processed)
                                    logger.debug("Detected language: ${detectedLang ?: "unknown"} for ${attempt.path}")

                                    attempt to Pair(processed, detectedLang)
                                } else {
                                    null
                                }
                            } catch (e: Throwable) {
                                logger.debug("Failed to fetch ${attempt.path}: ${e.message}")
                                null
                            }
                        }
                    }.also { asyncTasks ->
                        asyncTasks.take(6).forEach { it.start() }
                    }.awaitAll()
                    .filterNotNull()
                    .associateBy({ it.first }, { it.second })
            }

        if (foundReadmes.isEmpty()) {
            logger.error("Failed to fetch any README variant.")
            return null
        }

        foundReadmes.entries
            .firstOrNull { (attempt, content) ->
                attempt.filename != "README.md" && content.second == primaryLang
            }?.let { (attempt, content) ->
                logger.debug("Found localized README matching user language: ${attempt.path}")
                return Triple(content.first, content.second, attempt.path)
            }

        foundReadmes.entries
            .firstOrNull { (attempt, _) ->
                attempt.filename.contains(".$primaryLang.", ignoreCase = true) ||
                    attempt.filename.contains("-${primaryLang.uppercase()}.", ignoreCase = true)
            }?.let { (attempt, content) ->
                logger.debug("Found explicit language file for user: ${attempt.path}")
                return Triple(content.first, content.second ?: primaryLang, attempt.path)
            }

        foundReadmes.entries
            .firstOrNull { (attempt, content) ->
                attempt.filename == "README.md" && content.second == primaryLang
            }?.let { (attempt, content) ->
                logger.debug("Default README matches user language: ${attempt.path}")
                return Triple(content.first, content.second, attempt.path)
            }

        if (primaryLang == "en") {
            foundReadmes.entries
                .firstOrNull { (_, content) ->
                    content.second == "en"
                }?.let { (attempt, content) ->
                    logger.debug("Found English README for English user: ${attempt.path}")
                    return Triple(content.first, content.second, attempt.path)
                }
        }

        foundReadmes.entries
            .firstOrNull { (_, content) ->
                content.second == primaryLang
            }?.let { (attempt, content) ->
                logger.debug("Fallback: Using README matching user language: ${attempt.path}")
                return Triple(content.first, content.second, attempt.path)
            }

        if (primaryLang == "en") {
            foundReadmes.entries
                .firstOrNull { (_, content) ->
                    content.second == "en"
                }?.let { (attempt, content) ->
                    logger.debug("Fallback: Using English README: ${attempt.path}")
                    return Triple(content.first, content.second, attempt.path)
                }
        }

        foundReadmes.entries
            .firstOrNull { (attempt, _) ->
                attempt.path == "README.md"
            }?.let { (attempt, content) ->
                logger.debug("Fallback: Using root README.md (language: ${content.second}): ${attempt.path}")
                return Triple(content.first, content.second, attempt.path)
            }

        foundReadmes.entries
            .firstOrNull { (attempt, _) ->
                attempt.path.startsWith(".github/")
            }?.let { (attempt, content) ->
                logger.debug("Fallback: Using .github README: ${attempt.path}")
                return Triple(content.first, content.second, attempt.path)
            }

        foundReadmes.entries.minByOrNull { it.key.priority }?.let { (attempt, content) ->
            logger.debug("Fallback: Using highest priority README: ${attempt.path}")
            return Triple(content.first, content.second, attempt.path)
        }

        return null
    }

    override suspend fun getRepoStats(
        owner: String,
        repo: String,
    ): RepoStats {
        val cacheKey = "details:stats:$owner/$repo"

        cacheManager.get<RepoStats>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for repo stats $owner/$repo")
            return cached
        }

        return try {
            val info =
                httpClient
                    .executeRequest<RepoInfoNetwork> {
                        get("/repos/$owner/$repo") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrThrow()

            val result =
                RepoStats(
                    stars = info.stars,
                    forks = info.forks,
                    openIssues = info.openIssues,
                )

            cacheManager.put(cacheKey, result, REPO_STATS)
            result
        } catch (e: Exception) {
            cacheManager.getStale<RepoStats>(cacheKey)?.let { stale ->
                logger.debug("Network error, using stale cache for stats $owner/$repo")
                return stale
            }
            throw e
        }
    }

    override suspend fun getUserProfile(username: String): GithubUserProfile {
        val cacheKey = "details:profile:$username"

        cacheManager.get<GithubUserProfile>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for user profile $username")
            return cached
        }

        return try {
            val user =
                httpClient
                    .executeRequest<UserProfileNetwork> {
                        get("/users/$username") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrThrow()

            val result =
                GithubUserProfile(
                    id = user.id,
                    login = user.login,
                    name = user.name,
                    bio = user.bio,
                    avatarUrl = user.avatarUrl,
                    htmlUrl = user.htmlUrl,
                    followers = user.followers,
                    following = user.following,
                    publicRepos = user.publicRepos,
                    location = user.location,
                    company = user.company,
                    blog = user.blog,
                    twitterUsername = user.twitterUsername,
                )

            cacheManager.put(cacheKey, result, USER_PROFILE)
            result
        } catch (e: Exception) {
            cacheManager.getStale<GithubUserProfile>(cacheKey)?.let { stale ->
                logger.debug("Network error, using stale cache for profile $username")
                return stale
            }
            throw e
        }
    }
}
