package zed.rainxch.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.local.db.dao.FavoriteRepoDao
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.domain.model.FavoriteRepo
import zed.rainxch.core.domain.repository.FavouritesRepository

class FavouritesRepositoryImpl(
    private val favoriteRepoDao: FavoriteRepoDao,
    private val installedAppsDao: InstalledAppDao,
) : FavouritesRepository {
    override fun getAllFavorites(): Flow<List<FavoriteRepo>> =
        favoriteRepoDao
            .getAllFavorites()
            .map { favoriteRepos ->
                favoriteRepos.map { favoriteRepo -> favoriteRepo.toDomain() }
            }

    override fun isFavorite(repoId: Long): Flow<Boolean> = favoriteRepoDao.isFavorite(repoId)

    override suspend fun isFavoriteSync(repoId: Long): Boolean = favoriteRepoDao.isFavoriteSync(repoId)

    suspend fun addFavorite(repo: FavoriteRepo) {
        val installedApp = installedAppsDao.getAppByRepoId(repo.repoId)
        favoriteRepoDao.insertFavorite(
            repo
                .toEntity()
                .copy(
                    isInstalled = installedApp != null,
                    installedPackageName = installedApp?.packageName,
                ),
        )
    }

    override suspend fun toggleFavorite(repo: FavoriteRepo) {
        if (favoriteRepoDao.isFavoriteSync(repo.repoId)) {
            favoriteRepoDao.deleteFavoriteById(repo.repoId)
        } else {
            addFavorite(repo)
        }
    }

    override suspend fun updateFavoriteInstallStatus(
        repoId: Long,
        installed: Boolean,
        packageName: String?,
    ) {
        favoriteRepoDao.updateInstallStatus(repoId, installed, packageName)
    }
}
