package zed.rainxch.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRepoNetworkModel(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("owner") val owner: GithubOwnerNetworkModel,
    @SerialName("description") val description: String? = null,
    @SerialName("default_branch") val defaultBranch: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("stargazers_count") val stargazersCount: Int,
    @SerialName("forks_count") val forksCount: Int,
    @SerialName("language") val language: String? = null,
    @SerialName("topics") val topics: List<String>? = null,
    @SerialName("releases_url") val releasesUrl: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("fork") val fork: Boolean = false,
)
