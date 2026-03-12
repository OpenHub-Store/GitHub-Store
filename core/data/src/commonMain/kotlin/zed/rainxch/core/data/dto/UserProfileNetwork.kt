package zed.rainxch.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileNetwork(
    @SerialName("id") val id: Long,
    @SerialName("login") val login: String,
    @SerialName("name") val name: String? = null,
    @SerialName("bio") val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("followers") val followers: Int,
    @SerialName("following") val following: Int,
    @SerialName("public_repos") val publicRepos: Int,
    @SerialName("location") val location: String? = null,
    @SerialName("company") val company: String? = null,
    @SerialName("blog") val blog: String? = null,
    @SerialName("twitter_username") val twitterUsername: String? = null,
)
