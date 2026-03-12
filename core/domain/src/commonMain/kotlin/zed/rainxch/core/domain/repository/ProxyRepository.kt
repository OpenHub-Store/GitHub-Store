package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.ProxyConfig

interface ProxyRepository {
    fun getProxyConfig(): Flow<ProxyConfig>

    suspend fun setProxyConfig(config: ProxyConfig)
}
