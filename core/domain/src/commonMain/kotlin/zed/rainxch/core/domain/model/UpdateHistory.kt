package zed.rainxch.core.domain.model

data class UpdateHistory(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val repoOwner: String,
    val repoName: String,
    val fromVersion: String,
    val toVersion: String,
    val updatedAt: Long,
    val updateSource: InstallSource,
    val success: Boolean = true,
    val errorMessage: String? = null,
)
