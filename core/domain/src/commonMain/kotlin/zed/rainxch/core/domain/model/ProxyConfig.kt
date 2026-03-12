package zed.rainxch.core.domain.model

sealed class ProxyConfig {
    data object None : ProxyConfig()

    data object System : ProxyConfig()

    data class Http(
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null,
    ) : ProxyConfig()

    data class Socks(
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null,
    ) : ProxyConfig()
}
