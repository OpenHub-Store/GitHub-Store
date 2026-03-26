package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.Url
import zed.rainxch.core.domain.model.ProxyConfig
import java.net.Proxy
import java.net.ProxySelector

actual fun createPlatformHttpClient(proxyConfig: ProxyConfig): HttpClient =
    HttpClient(OkHttp) {
        engine {
            when (proxyConfig) {
                is ProxyConfig.None -> {
                    config {
                        proxy(Proxy.NO_PROXY)
                    }
                }

                is ProxyConfig.System -> {
                    config {
                        proxySelector(ProxySelector.getDefault())
                    }
                }

                is ProxyConfig.Http -> {
                    proxy = ProxyBuilder.http(Url("http://${proxyConfig.host}:${proxyConfig.port}"))
                }

                is ProxyConfig.Socks -> {
                    proxy = ProxyBuilder.socks(proxyConfig.host, proxyConfig.port)
                }
            }
        }
    }
