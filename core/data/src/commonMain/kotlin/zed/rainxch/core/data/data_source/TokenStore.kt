package zed.rainxch.core.data.data_source

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto

interface TokenStore {
    fun tokenFlow(): Flow<GithubDeviceTokenSuccessDto?>

    suspend fun currentToken(): GithubDeviceTokenSuccessDto?

    fun blockingCurrentToken(): GithubDeviceTokenSuccessDto?

    suspend fun save(token: GithubDeviceTokenSuccessDto)

    suspend fun clear()

    suspend fun isTokenExpired(): Boolean
}
