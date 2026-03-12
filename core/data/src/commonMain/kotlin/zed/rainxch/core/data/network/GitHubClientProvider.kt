package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.domain.model.ProxyConfig
import zed.rainxch.core.domain.repository.AuthenticationState
import zed.rainxch.core.domain.repository.RateLimitRepository

class GitHubClientProvider(
    private val tokenStore: TokenStore,
    private val rateLimitRepository: RateLimitRepository,
    private val authenticationState: AuthenticationState,
    proxyConfigFlow: StateFlow<ProxyConfig>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    @Volatile
    private var currentClient: HttpClient =
        createGitHubHttpClient(
            tokenStore = tokenStore,
            rateLimitRepository = rateLimitRepository,
            authenticationState = authenticationState,
            scope = scope,
            proxyConfig = proxyConfigFlow.value,
        )

    init {
        proxyConfigFlow
            .drop(1)
            .distinctUntilChanged()
            .onEach { proxyConfig ->
                mutex.withLock {
                    currentClient.close()
                    currentClient =
                        createGitHubHttpClient(
                            tokenStore = tokenStore,
                            rateLimitRepository = rateLimitRepository,
                            authenticationState = authenticationState,
                            scope = scope,
                            proxyConfig = proxyConfig,
                        )
                }
            }.launchIn(scope)
    }

    val client: HttpClient get() = currentClient

    fun close() {
        currentClient.close()
        scope.cancel()
    }
}
