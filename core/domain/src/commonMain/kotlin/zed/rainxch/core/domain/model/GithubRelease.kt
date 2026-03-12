package zed.rainxch.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    val id: Long,
    val tagName: String,
    val name: String?,
    val author: GithubUser,
    val publishedAt: String,
    val description: String?,
    val assets: List<GithubAsset>,
    val tarballUrl: String,
    val zipballUrl: String,
    val htmlUrl: String,
    val isPrerelease: Boolean = false,
)
