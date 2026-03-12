package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.local.db.entities.FavoriteRepoEntity
import zed.rainxch.core.domain.model.FavoriteRepo

fun FavoriteRepo.toEntity(): FavoriteRepoEntity =
    FavoriteRepoEntity(
        repoId = repoId,
        repoName = repoName,
        repoOwner = repoOwner,
        repoOwnerAvatarUrl = repoOwnerAvatarUrl,
        repoDescription = repoDescription,
        primaryLanguage = primaryLanguage,
        repoUrl = repoUrl,
        isInstalled = isInstalled,
        installedPackageName = installedPackageName,
        latestVersion = latestVersion,
        latestReleaseUrl = latestReleaseUrl,
        addedAt = addedAt,
        lastSyncedAt = lastSyncedAt,
    )

fun FavoriteRepoEntity.toDomain(): FavoriteRepo =
    FavoriteRepo(
        repoId = repoId,
        repoName = repoName,
        repoOwner = repoOwner,
        repoOwnerAvatarUrl = repoOwnerAvatarUrl,
        repoDescription = repoDescription,
        primaryLanguage = primaryLanguage,
        repoUrl = repoUrl,
        isInstalled = isInstalled,
        installedPackageName = installedPackageName,
        latestVersion = latestVersion,
        latestReleaseUrl = latestReleaseUrl,
        addedAt = addedAt,
        lastSyncedAt = lastSyncedAt,
    )
