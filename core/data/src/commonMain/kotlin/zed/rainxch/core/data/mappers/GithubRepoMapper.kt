package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.domain.model.GithubRepoSummary
import zed.rainxch.core.domain.model.GithubUser

fun GithubRepoNetworkModel.toSummary(): GithubRepoSummary =
    GithubRepoSummary(
        id = id,
        name = name,
        fullName = fullName,
        owner =
            GithubUser(
                id = owner.id,
                login = owner.login,
                avatarUrl = owner.avatarUrl,
                htmlUrl = owner.htmlUrl,
            ),
        description = description,
        htmlUrl = htmlUrl,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        language = language,
        topics = topics,
        releasesUrl = releasesUrl,
        updatedAt = updatedAt,
        defaultBranch = defaultBranch,
        isFork = fork,
    )
