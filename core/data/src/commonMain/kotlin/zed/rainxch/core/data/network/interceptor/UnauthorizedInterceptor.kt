package zed.rainxch.core.data.network.interceptor

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.repository.AuthenticationState

class UnauthorizedInterceptor(
    private val authenticationState: AuthenticationState,
    private val scope: CoroutineScope,
) {
    class Config {
        var authenticationState: AuthenticationState? = null
        var scope: CoroutineScope? = null
    }

    companion object Plugin : HttpClientPlugin<Config, UnauthorizedInterceptor> {
        override val key: AttributeKey<UnauthorizedInterceptor> =
            AttributeKey("UnauthorizedInterceptor")

        override fun prepare(block: Config.() -> Unit): UnauthorizedInterceptor {
            val config = Config().apply(block)
            return UnauthorizedInterceptor(
                authenticationState =
                    requireNotNull(config.authenticationState) {
                        "AuthenticationState must be provided"
                    },
                scope =
                    requireNotNull(config.scope) {
                        "CoroutineScope must be provided"
                    },
            )
        }

        override fun install(
            plugin: UnauthorizedInterceptor,
            scope: HttpClient,
        ) {
            scope.receivePipeline.intercept(HttpReceivePipeline.After) {
                if (subject.status.value == 401) {
                    plugin.scope.launch {
                        plugin.authenticationState.notifySessionExpired()
                    }
                }
                proceedWith(subject)
            }
        }
    }
}
