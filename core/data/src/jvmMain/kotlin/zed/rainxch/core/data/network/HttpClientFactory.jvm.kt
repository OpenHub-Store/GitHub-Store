package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.Url
import zed.rainxch.core.domain.model.ProxyConfig
import java.net.ProxySelector
import java.net.URI

actual fun createPlatformHttpClient(proxyConfig: ProxyConfig): HttpClient =
    HttpClient(OkHttp) {
        engine {
            proxy =
                when (proxyConfig) {
                    is ProxyConfig.None -> {
                        null
                    }

                    is ProxyConfig.System -> {
                        val systemProxy =
                            ProxySelector
                                .getDefault()
                                ?.select(URI("https://api.github.com"))
                                ?.firstOrNull { it.type() != java.net.Proxy.Type.DIRECT }

                        if (systemProxy != null) {
                            val addr = systemProxy.address() as? java.net.InetSocketAddress
                            if (addr != null) {
                                when (systemProxy.type()) {
                                    java.net.Proxy.Type.HTTP -> {
                                        ProxyBuilder.http(Url("http://${addr.hostString}:${addr.port}"))
                                    }

                                    java.net.Proxy.Type.SOCKS -> {
                                        ProxyBuilder.socks(addr.hostString, addr.port)
                                    }

                                    else -> {
                                        null
                                    }
                                }
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    is ProxyConfig.Http -> {
                        ProxyBuilder.http(Url("http://${proxyConfig.host}:${proxyConfig.port}"))
                    }

                    is ProxyConfig.Socks -> {
                        ProxyBuilder.socks(proxyConfig.host, proxyConfig.port)
                    }
                }
        }
    }
