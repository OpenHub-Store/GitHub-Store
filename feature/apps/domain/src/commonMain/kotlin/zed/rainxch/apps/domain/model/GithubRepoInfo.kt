package zed.rainxch.apps.domain.model

data class GithubRepoInfo(
    val id: Long,
    val name: String,
    val owner: String,
    val ownerAvatarUrl: String,
    val description: String?,
    val language: String?,
    val htmlUrl: String,
    val latestReleaseTag: String?,
)
