package zed.rainxch.core.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zed.rainxch.core.domain.model.ProxyConfig

object ProxyManager {
    private val _proxyConfig = MutableStateFlow<ProxyConfig>(ProxyConfig.System)
    val currentProxyConfig: StateFlow<ProxyConfig> = _proxyConfig.asStateFlow()

    fun setNoProxy() {
        _proxyConfig.value = ProxyConfig.None
    }

    fun setSystemProxy() {
        _proxyConfig.value = ProxyConfig.System
    }

    fun setHttpProxy(
        host: String,
        port: Int,
        username: String? = null,
        password: String? = null,
    ) {
        _proxyConfig.value = ProxyConfig.Http(host, port, username, password)
    }

    fun setSocksProxy(
        host: String,
        port: Int,
        username: String? = null,
        password: String? = null,
    ) {
        _proxyConfig.value = ProxyConfig.Socks(host, port, username, password)
    }
}
