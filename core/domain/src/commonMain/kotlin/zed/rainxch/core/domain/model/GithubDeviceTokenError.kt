package zed.rainxch.core.domain.model

data class GithubDeviceTokenError(
    val error: String,
    val errorDescription: String? = null,
)
