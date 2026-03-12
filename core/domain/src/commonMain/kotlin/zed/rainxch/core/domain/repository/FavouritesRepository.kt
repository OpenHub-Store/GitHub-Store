package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.FavoriteRepo

interface FavouritesRepository {
    fun getAllFavorites(): Flow<List<FavoriteRepo>>

    fun isFavorite(repoId: Long): Flow<Boolean>

    suspend fun isFavoriteSync(repoId: Long): Boolean

    suspend fun toggleFavorite(repo: FavoriteRepo)

    suspend fun updateFavoriteInstallStatus(
        repoId: Long,
        installed: Boolean,
        packageName: String?,
    )
}
