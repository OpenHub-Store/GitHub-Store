package zed.rainxch.apps.presentation.model

data class GithubAssetUi(
    val id: Long,
    val name: String,
    val contentType: String,
    val size: Long,
    val downloadUrl: String,
    val uploader: GithubUserUi,
)
