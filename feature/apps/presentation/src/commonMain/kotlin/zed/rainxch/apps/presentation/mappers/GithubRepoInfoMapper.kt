package zed.rainxch.apps.presentation.mappers

import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.apps.presentation.model.GithubRepoInfoUi

fun GithubRepoInfo.toUi(): GithubRepoInfoUi =
    GithubRepoInfoUi(
        id = id,
        name = name,
        owner = owner,
        ownerAvatarUrl = ownerAvatarUrl,
        description = description,
        language = language,
        htmlUrl = htmlUrl,
        latestReleaseTag = latestReleaseTag,
    )

fun GithubRepoInfoUi.toDomain(): GithubRepoInfo =
    GithubRepoInfo(
        id = id,
        name = name,
        owner = owner,
        ownerAvatarUrl = ownerAvatarUrl,
        description = description,
        language = language,
        htmlUrl = htmlUrl,
        latestReleaseTag = latestReleaseTag,
    )
