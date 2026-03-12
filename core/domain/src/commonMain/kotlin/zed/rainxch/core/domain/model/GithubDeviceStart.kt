package zed.rainxch.core.domain.model

data class GithubDeviceStart(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String? = null,
    val intervalSec: Int = 5,
    val expiresInSec: Int,
)
