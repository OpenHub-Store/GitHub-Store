package zed.rainxch.profile.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.profile.domain.model.UserProfile

interface ProfileRepository {
    val isUserLoggedIn: Flow<Boolean>

    fun getUser(): Flow<UserProfile?>

    fun getVersionName(): String

    suspend fun logout()

    fun observeCacheSize(): Flow<Long>

    suspend fun clearCache()
}
