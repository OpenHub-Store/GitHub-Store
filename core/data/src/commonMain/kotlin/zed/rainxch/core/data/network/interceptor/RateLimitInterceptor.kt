package zed.rainxch.core.data.network.interceptor

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.Headers
import io.ktor.util.AttributeKey
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.core.domain.model.RateLimitInfo
import zed.rainxch.core.domain.repository.RateLimitRepository

class RateLimitInterceptor(
    private val rateLimitRepository: RateLimitRepository,
) {
    class Config {
        var rateLimitRepository: RateLimitRepository? = null
    }

    companion object Plugin : HttpClientPlugin<Config, RateLimitInterceptor> {
        override val key: AttributeKey<RateLimitInterceptor> =
            AttributeKey("RateLimitInterceptor")

        override fun prepare(block: Config.() -> Unit): RateLimitInterceptor {
            val config = Config().apply(block)
            return RateLimitInterceptor(
                rateLimitRepository =
                    requireNotNull(config.rateLimitRepository) {
                        "RateLimitRepository must be provided"
                    },
            )
        }

        override fun install(
            plugin: RateLimitInterceptor,
            scope: HttpClient,
        ) {
            scope.receivePipeline.intercept(HttpReceivePipeline.State) {
                val response = subject

                parseRateLimitFromHeaders(response.headers)?.let { rateLimitInfo ->
                    plugin.rateLimitRepository.updateRateLimit(rateLimitInfo)

                    if (response.status.value == 403 && rateLimitInfo.isExhausted) {
                        throw RateLimitException(rateLimitInfo)
                    }
                }

                proceedWith(subject)
            }
        }

        private fun parseRateLimitFromHeaders(headers: Headers): RateLimitInfo? {
            return try {
                val limitHeader =
                    headers["X-RateLimit-Limit"]
                        ?: return null.also { Logger.w { "Missing X-RateLimit-Limit" } }
                val limit =
                    limitHeader.toIntOrNull()
                        ?: return null.also { Logger.w { "Malformed X-RateLimit-Limit: $limitHeader" } }
                val remainingHeader =
                    headers["X-RateLimit-Remaining"]
                        ?: return null.also { Logger.w { "Missing X-RateLimit-Remaining" } }
                val remaining =
                    remainingHeader.toIntOrNull()
                        ?: return null.also { Logger.w { "Malformed X-RateLimit-Remaining: $remainingHeader" } }
                val resetHeader =
                    headers["X-RateLimit-Reset"]
                        ?: return null.also { Logger.w { "Missing X-RateLimit-Reset" } }
                val reset =
                    resetHeader.toLongOrNull()
                        ?: return null.also { Logger.w { "Malformed X-RateLimit-Reset: $resetHeader" } }
                val resource = headers["X-RateLimit-Resource"] ?: "core"

                RateLimitInfo(limit, remaining, reset, resource)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to parse rate limit headers" }
                null
            }
        }
    }
}
