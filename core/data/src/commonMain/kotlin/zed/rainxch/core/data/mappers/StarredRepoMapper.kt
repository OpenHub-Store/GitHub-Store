package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.local.db.entities.StarredRepositoryEntity
import zed.rainxch.core.domain.model.StarredRepository

fun StarredRepository.toEntity(): StarredRepositoryEntity =
    StarredRepositoryEntity(
        repoId = repoId,
        repoName = repoName,
        repoOwner = repoOwner,
        repoOwnerAvatarUrl = repoOwnerAvatarUrl,
        repoDescription = repoDescription,
        primaryLanguage = primaryLanguage,
        repoUrl = repoUrl,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
        isInstalled = isInstalled,
        installedPackageName = installedPackageName,
        latestVersion = latestVersion,
        latestReleaseUrl = latestReleaseUrl,
        starredAt = starredAt,
        addedAt = addedAt,
        lastSyncedAt = lastSyncedAt,
    )

fun StarredRepositoryEntity.toDomain(): StarredRepository =
    StarredRepository(
        repoId = repoId,
        repoName = repoName,
        repoOwner = repoOwner,
        repoOwnerAvatarUrl = repoOwnerAvatarUrl,
        repoDescription = repoDescription,
        primaryLanguage = primaryLanguage,
        repoUrl = repoUrl,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
        isInstalled = isInstalled,
        installedPackageName = installedPackageName,
        latestVersion = latestVersion,
        latestReleaseUrl = latestReleaseUrl,
        starredAt = starredAt,
        addedAt = addedAt,
        lastSyncedAt = lastSyncedAt,
    )
