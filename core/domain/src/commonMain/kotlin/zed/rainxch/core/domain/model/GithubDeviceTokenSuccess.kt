package zed.rainxch.core.domain.model

data class GithubDeviceTokenSuccess(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long? = null,
    val scope: String? = null,
    val refreshToken: String? = null,
    val refreshTokenExpiresIn: Long? = null,
)
