package zed.rainxch.devprofile.data.mappers

import zed.rainxch.devprofile.data.dto.GitHubUserResponse
import zed.rainxch.devprofile.domain.model.DeveloperProfile

fun GitHubUserResponse.toDomain() =
    DeveloperProfile(
        login = login,
        name = name,
        avatarUrl = avatarUrl,
        bio = bio,
        company = company,
        location = location,
        email = email,
        blog = blog,
        twitterUsername = twitterUsername,
        publicRepos = publicRepos,
        publicGists = publicGists,
        followers = followers,
        following = following,
        createdAt = createdAt,
        updatedAt = updatedAt,
        htmlUrl = htmlUrl,
    )
