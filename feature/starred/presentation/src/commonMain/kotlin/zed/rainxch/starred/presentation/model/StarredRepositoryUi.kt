package zed.rainxch.starred.presentation.model

data class StarredRepositoryUi(
    val repoId: Long,
    val repoName: String,
    val repoOwner: String,
    val repoOwnerAvatarUrl: String,
    val repoDescription: String?,
    val primaryLanguage: String?,
    val repoUrl: String,
    val stargazersCount: Int,
    val forksCount: Int,
    val openIssuesCount: Int,
    val isInstalled: Boolean,
    val isFavorite: Boolean = false,
    val latestRelease: String?,
    val latestReleaseUrl: String?,
    val starredAt: Long?,
)
