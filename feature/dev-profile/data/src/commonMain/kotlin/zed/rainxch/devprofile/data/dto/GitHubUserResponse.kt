package zed.rainxch.devprofile.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUserResponse(
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String,
    val bio: String? = null,
    val company: String? = null,
    val location: String? = null,
    val email: String? = null,
    val blog: String? = null,
    @SerialName("twitter_username") val twitterUsername: String? = null,
    @SerialName("public_repos") val publicRepos: Int,
    @SerialName("public_gists") val publicGists: Int,
    val followers: Int,
    val following: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String,
)
