package zed.rainxch.search.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssetNetworkModel(
    @SerialName("name") val name: String,
)
