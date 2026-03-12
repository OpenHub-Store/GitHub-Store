package zed.rainxch.core.data.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import okhttp3.Credentials
import zed.rainxch.core.domain.model.ProxyConfig
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector

actual fun createPlatformHttpClient(proxyConfig: ProxyConfig): HttpClient {
    Authenticator.setDefault(null)

    return HttpClient(OkHttp) {
        engine {
            when (proxyConfig) {
                is ProxyConfig.None -> {
                    proxy = Proxy.NO_PROXY
                }

                is ProxyConfig.System -> {
                    config {
                        proxySelector(ProxySelector.getDefault())
                    }
                }

                is ProxyConfig.Http -> {
                    proxy =
                        Proxy(
                            Proxy.Type.HTTP,
                            InetSocketAddress(proxyConfig.host, proxyConfig.port),
                        )
                    if (proxyConfig.username != null) {
                        config {
                            proxyAuthenticator { _, response ->
                                response.request
                                    .newBuilder()
                                    .header(
                                        "Proxy-Authorization",
                                        Credentials.basic(
                                            proxyConfig.username!!,
                                            proxyConfig.password.orEmpty(),
                                        ),
                                    ).build()
                            }
                        }
                    }
                }

                is ProxyConfig.Socks -> {
                    proxy =
                        Proxy(
                            Proxy.Type.SOCKS,
                            InetSocketAddress(proxyConfig.host, proxyConfig.port),
                        )

                    if (proxyConfig.username != null) {
                        Authenticator.setDefault(
                            object : Authenticator() {
                                override fun getPasswordAuthentication(): PasswordAuthentication? {
                                    if (requestingHost == proxyConfig.host &&
                                        requestingPort == proxyConfig.port
                                    ) {
                                        return PasswordAuthentication(
                                            proxyConfig.username,
                                            proxyConfig.password.orEmpty().toCharArray(),
                                        )
                                    }
                                    return null
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
