package zed.rainxch.devprofile.domain.model

data class DeveloperRepository(
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val htmlUrl: String,
    val stargazersCount: Int,
    val forksCount: Int,
    val openIssuesCount: Int,
    val language: String?,
    val hasReleases: Boolean,
    val hasInstallableAssets: Boolean,
    val isInstalled: Boolean = false,
    val isFavorite: Boolean = false,
    val latestVersion: String? = null,
    val updatedAt: String,
    val pushedAt: String?,
)
