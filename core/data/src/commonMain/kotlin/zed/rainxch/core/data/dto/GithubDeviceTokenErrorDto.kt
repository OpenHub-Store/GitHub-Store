package zed.rainxch.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubDeviceTokenErrorDto(
    @SerialName("error") val error: String,
    @SerialName("error_description") val errorDescription: String? = null,
)
