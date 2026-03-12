package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.domain.model.GithubRelease
import zed.rainxch.core.domain.model.GithubUser

fun ReleaseNetwork.toDomain(): GithubRelease =
    GithubRelease(
        id = id,
        tagName = tagName,
        name = name,
        author =
            GithubUser(
                id = author.id,
                login = author.login,
                avatarUrl = author.avatarUrl,
                htmlUrl = author.htmlUrl,
            ),
        publishedAt = publishedAt ?: createdAt ?: "",
        description = body,
        assets = assets.map { it.toDomain() },
        tarballUrl = tarballUrl,
        zipballUrl = zipballUrl,
        htmlUrl = htmlUrl,
        isPrerelease = prerelease == true,
    )
