package zed.rainxch.auth.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.GithubDeviceStart
import zed.rainxch.core.domain.model.GithubDeviceTokenSuccess

interface AuthenticationRepository {
    val accessTokenFlow: Flow<String?>

    suspend fun startDeviceFlow(): GithubDeviceStart

    suspend fun awaitDeviceToken(start: GithubDeviceStart): GithubDeviceTokenSuccess
}
