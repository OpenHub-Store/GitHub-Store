package zed.rainxch.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReleaseNetwork(
    @SerialName("id") val id: Long,
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String? = null,
    @SerialName("draft") val draft: Boolean? = null,
    @SerialName("prerelease") val prerelease: Boolean? = null,
    @SerialName("author") val author: OwnerNetwork,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("tarball_url") val tarballUrl: String,
    @SerialName("zipball_url") val zipballUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("assets") val assets: List<AssetNetwork>,
)
