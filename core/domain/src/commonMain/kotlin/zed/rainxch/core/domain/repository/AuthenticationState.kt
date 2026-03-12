package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface AuthenticationState {
    fun isUserLoggedIn(): Flow<Boolean>

    suspend fun isCurrentlyUserLoggedIn(): Boolean

    val sessionExpiredEvent: SharedFlow<Unit>

    suspend fun notifySessionExpired()
}
