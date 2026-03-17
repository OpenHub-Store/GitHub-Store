package zed.rainxch.apps.presentation.mappers

import zed.rainxch.apps.presentation.model.GithubAssetUi
import zed.rainxch.apps.presentation.model.GithubUserUi
import zed.rainxch.core.domain.model.GithubAsset

fun GithubAsset.toUi(): GithubAssetUi {
    return GithubAssetUi(
        id = id,
        name = name,
        contentType = contentType,
        size = size,
        downloadUrl = downloadUrl,
        uploader = GithubUserUi(
            id = uploader.id,
            login = uploader.login,
            avatarUrl = uploader.avatarUrl,
            htmlUrl = uploader.htmlUrl,
        ),
    )
}
