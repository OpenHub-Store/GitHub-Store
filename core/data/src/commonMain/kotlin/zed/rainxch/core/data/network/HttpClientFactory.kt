package zed.rainxch.core.data.network

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.network.interceptor.RateLimitInterceptor
import zed.rainxch.core.data.network.interceptor.UnauthorizedInterceptor
import zed.rainxch.core.domain.model.ProxyConfig
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.core.domain.repository.AuthenticationState
import zed.rainxch.core.domain.repository.RateLimitRepository
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

expect fun createPlatformHttpClient(proxyConfig: ProxyConfig): HttpClient

fun createGitHubHttpClient(
    tokenStore: TokenStore,
    rateLimitRepository: RateLimitRepository,
    authenticationState: AuthenticationState? = null,
    scope: CoroutineScope? = null,
    proxyConfig: ProxyConfig = ProxyConfig.System,
): HttpClient {
    val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    return createPlatformHttpClient(proxyConfig).config {
        install(RateLimitInterceptor) {
            this.rateLimitRepository = rateLimitRepository
        }

        if (authenticationState != null && scope != null) {
            install(UnauthorizedInterceptor) {
                this.authenticationState = authenticationState
                this.scope = scope
            }
        }

        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }

        install(HttpRequestRetry) {
            maxRetries = 3
            retryIf { _, response ->
                val code = response.status.value
                if (code == 403) {
                    val remaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
                    if (remaining == 0) return@retryIf false
                }
                code in 500..<600
            }
            retryOnExceptionIf { _, cause ->
                cause is HttpRequestTimeoutException ||
                    cause is UnresolvedAddressException ||
                    cause is IOException
            }
            exponentialDelay()
        }

        expectSuccess = false

        defaultRequest {
            url("https://api.github.com")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.UserAgent, "GithubStore/1.0 (KMP)")

            val token =
                tokenStore
                    .blockingCurrentToken()
                    ?.accessToken
                    ?.trim()
                    .orEmpty()
            if (token.isNotEmpty()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }
}

suspend inline fun <reified T> HttpClient.executeRequest(crossinline block: suspend HttpClient.() -> HttpResponse): Result<T> =
    try {
        val response = block()

        if (response.status.isSuccess()) {
            Result.success(response.body<T>())
        } else {
            Result.failure(
                Exception("HTTP ${response.status.value}: ${response.status.description}"),
            )
        }
    } catch (e: RateLimitException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
