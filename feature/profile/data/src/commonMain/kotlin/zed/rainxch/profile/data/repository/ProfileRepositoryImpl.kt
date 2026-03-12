package zed.rainxch.profile.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.USER_PROFILE
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.dto.UserProfileNetwork
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.data.services.FileLocationsProvider
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.repository.AuthenticationState
import zed.rainxch.feature.profile.data.BuildKonfig
import zed.rainxch.profile.data.mappers.toUserProfile
import zed.rainxch.profile.domain.model.UserProfile
import zed.rainxch.profile.domain.repository.ProfileRepository

class ProfileRepositoryImpl(
    private val authenticationState: AuthenticationState,
    private val tokenStore: TokenStore,
    private val httpClient: HttpClient,
    private val cacheManager: CacheManager,
    private val logger: GitHubStoreLogger,
    private val fileLocationsProvider: FileLocationsProvider,
) : ProfileRepository {
    companion object {
        private const val CACHE_KEY = "profile:me"
    }

    override val isUserLoggedIn: Flow<Boolean>
        get() =
            authenticationState
                .isUserLoggedIn()
                .flowOn(Dispatchers.IO)

    override fun getUser(): Flow<UserProfile?> =
        flow {
            val token = tokenStore.currentToken()
            if (token == null) {
                cacheManager.invalidate(CACHE_KEY)
                emit(null)
                return@flow
            }

            val cached = cacheManager.get<UserProfile>(CACHE_KEY)
            if (cached != null) {
                logger.debug("Profile cache hit")
                emit(cached)
                return@flow
            }

            try {
                val networkProfile =
                    httpClient
                        .executeRequest<UserProfileNetwork> {
                            get("/user") {
                                header(HttpHeaders.Accept, "application/vnd.github+json")
                            }
                        }.getOrThrow()

                val userProfile = networkProfile.toUserProfile()
                cacheManager.put(CACHE_KEY, userProfile, USER_PROFILE)
                logger.debug("Fetched and cached user profile: ${userProfile.username}")
                emit(userProfile)
            } catch (e: Exception) {
                logger.error("Failed to fetch user profile: ${e.message}")

                val stale = cacheManager.getStale<UserProfile>(CACHE_KEY)
                if (stale != null) {
                    logger.debug("Using stale cached profile as fallback")
                    emit(stale)
                } else {
                    emit(null)
                }
            }
        }.flowOn(Dispatchers.IO)

    override fun getVersionName(): String = BuildKonfig.VERSION_NAME

    override suspend fun logout() {
        tokenStore.clear()
        cacheManager.clearAll()
    }

    override fun observeCacheSize(): Flow<Long> =
        flow {
            val sizeBytes = fileLocationsProvider.getCacheSizeBytes()
            emit(sizeBytes)
        }.flowOn(Dispatchers.IO)

    override suspend fun clearCache() {
        fileLocationsProvider.clearCacheFiles()
        cacheManager.clearAll()
        logger.debug("Cache cleared successfully")
    }
}
