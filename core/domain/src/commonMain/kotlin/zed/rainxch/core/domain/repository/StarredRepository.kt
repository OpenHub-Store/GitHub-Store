package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.StarredRepository

interface StarredRepository {
    fun getAllStarred(): Flow<List<StarredRepository>>

    suspend fun isStarred(repoId: Long): Boolean

    suspend fun syncStarredRepos(forceRefresh: Boolean = false): Result<Unit>

    suspend fun getLastSyncTime(): Long?

    suspend fun needsSync(): Boolean
}
