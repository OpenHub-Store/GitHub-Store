package zed.rainxch.home.data.mappers

import zed.rainxch.core.domain.model.GithubRepoSummary
import zed.rainxch.core.domain.model.GithubUser
import zed.rainxch.home.data.dto.CachedGithubRepoSummary

fun CachedGithubRepoSummary.toGithubRepoSummary(): GithubRepoSummary =
    GithubRepoSummary(
        id = id,
        name = name,
        fullName = fullName,
        owner =
            GithubUser(
                id = 0,
                login = owner.login,
                avatarUrl = owner.avatarUrl,
                htmlUrl = "https://github.com/${owner.login}",
            ),
        description = description,
        defaultBranch = defaultBranch,
        htmlUrl = htmlUrl,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        language = language,
        topics = topics,
        releasesUrl = releasesUrl,
        updatedAt = latestReleaseDate ?: updatedAt,
    )
