package zed.rainxch.search.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubReleaseNetworkModel(
    @SerialName("draft") val draft: Boolean? = null,
    @SerialName("prerelease") val prerelease: Boolean? = null,
    @SerialName("assets") val assets: List<AssetNetworkModel>,
    @SerialName("published_at") val publishedAt: String? = null,
)
