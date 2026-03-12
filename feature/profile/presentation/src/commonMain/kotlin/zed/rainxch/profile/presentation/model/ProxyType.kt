package zed.rainxch.profile.presentation.model

import zed.rainxch.core.domain.model.ProxyConfig

enum class ProxyType {
    NONE,
    SYSTEM,
    HTTP,
    SOCKS,
    ;

    companion object {
        fun fromConfig(config: ProxyConfig): ProxyType =
            when (config) {
                is ProxyConfig.None -> NONE
                is ProxyConfig.System -> SYSTEM
                is ProxyConfig.Http -> HTTP
                is ProxyConfig.Socks -> SOCKS
            }
    }
}
