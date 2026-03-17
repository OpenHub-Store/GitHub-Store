package zed.rainxch.apps.presentation.model

data class GithubRepoInfoUi(
    val id: Long,
    val name: String,
    val owner: String,
    val ownerAvatarUrl: String,
    val description: String?,
    val language: String?,
    val htmlUrl: String,
    val latestReleaseTag: String?,
)
