package zed.rainxch.starred.presentation.mappers

import zed.rainxch.core.domain.model.StarredRepository
import zed.rainxch.starred.presentation.model.StarredRepositoryUi

fun StarredRepository.toStarredRepositoryUi(isFavorite: Boolean = false) =
    StarredRepositoryUi(
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
        isFavorite = isFavorite,
        latestRelease = latestVersion,
        latestReleaseUrl = latestReleaseUrl,
        starredAt = starredAt,
    )
