package zed.rainxch.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.network.ProxyManager
import zed.rainxch.core.domain.model.ProxyConfig
import zed.rainxch.core.domain.repository.ProxyRepository

class ProxyRepositoryImpl(
    private val preferences: DataStore<Preferences>,
) : ProxyRepository {
    private val proxyTypeKey = stringPreferencesKey("proxy_type")
    private val proxyHostKey = stringPreferencesKey("proxy_host")
    private val proxyPortKey = intPreferencesKey("proxy_port")
    private val proxyUsernameKey = stringPreferencesKey("proxy_username")
    private val proxyPasswordKey = stringPreferencesKey("proxy_password")

    override fun getProxyConfig(): Flow<ProxyConfig> =
        preferences.data.map { prefs ->
            when (prefs[proxyTypeKey]) {
                "system" -> {
                    ProxyConfig.System
                }

                "http" -> {
                    val host = prefs[proxyHostKey]?.takeIf { it.isNotBlank() }
                    val port = prefs[proxyPortKey]?.takeIf { it in 1..65535 }
                    if (host != null && port != null) {
                        ProxyConfig.Http(
                            host = host,
                            port = port,
                            username = prefs[proxyUsernameKey],
                            password = prefs[proxyPasswordKey],
                        )
                    } else {
                        ProxyConfig.None
                    }
                }

                "socks" -> {
                    val host = prefs[proxyHostKey]?.takeIf { it.isNotBlank() }
                    val port = prefs[proxyPortKey]?.takeIf { it in 1..65535 }
                    if (host != null && port != null) {
                        ProxyConfig.Socks(
                            host = host,
                            port = port,
                            username = prefs[proxyUsernameKey],
                            password = prefs[proxyPasswordKey],
                        )
                    } else {
                        ProxyConfig.None
                    }
                }

                else -> {
                    ProxyConfig.System
                }
            }
        }

    override suspend fun setProxyConfig(config: ProxyConfig) {
        // Persist first so config survives crashes, then apply in-memory
        preferences.edit { prefs ->
            when (config) {
                is ProxyConfig.None -> {
                    prefs[proxyTypeKey] = "none"
                    prefs.remove(proxyHostKey)
                    prefs.remove(proxyPortKey)
                    prefs.remove(proxyUsernameKey)
                    prefs.remove(proxyPasswordKey)
                }

                is ProxyConfig.System -> {
                    prefs[proxyTypeKey] = "system"
                    prefs.remove(proxyHostKey)
                    prefs.remove(proxyPortKey)
                    prefs.remove(proxyUsernameKey)
                    prefs.remove(proxyPasswordKey)
                }

                is ProxyConfig.Http -> {
                    prefs[proxyTypeKey] = "http"
                    prefs[proxyHostKey] = config.host
                    prefs[proxyPortKey] = config.port
                    if (config.username != null) {
                        prefs[proxyUsernameKey] = config.username!!
                    } else {
                        prefs.remove(proxyUsernameKey)
                    }
                    if (config.password != null) {
                        prefs[proxyPasswordKey] = config.password!!
                    } else {
                        prefs.remove(proxyPasswordKey)
                    }
                }

                is ProxyConfig.Socks -> {
                    prefs[proxyTypeKey] = "socks"
                    prefs[proxyHostKey] = config.host
                    prefs[proxyPortKey] = config.port
                    if (config.username != null) {
                        prefs[proxyUsernameKey] = config.username!!
                    } else {
                        prefs.remove(proxyUsernameKey)
                    }
                    if (config.password != null) {
                        prefs[proxyPasswordKey] = config.password!!
                    } else {
                        prefs.remove(proxyPasswordKey)
                    }
                }
            }
        }
        applyToProxyManager(config)
    }

    private fun applyToProxyManager(config: ProxyConfig) {
        when (config) {
            is ProxyConfig.None -> {
                ProxyManager.setNoProxy()
            }

            is ProxyConfig.System -> {
                ProxyManager.setSystemProxy()
            }

            is ProxyConfig.Http -> {
                ProxyManager.setHttpProxy(
                    host = config.host,
                    port = config.port,
                    username = config.username,
                    password = config.password,
                )
            }

            is ProxyConfig.Socks -> {
                ProxyManager.setSocksProxy(
                    host = config.host,
                    port = config.port,
                    username = config.username,
                    password = config.password,
                )
            }
        }
    }
}
