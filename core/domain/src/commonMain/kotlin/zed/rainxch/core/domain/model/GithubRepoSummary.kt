package zed.rainxch.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GithubRepoSummary(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: GithubUser,
    val description: String?,
    val defaultBranch: String,
    val htmlUrl: String,
    val stargazersCount: Int,
    val forksCount: Int,
    val language: String?,
    val topics: List<String>?,
    val releasesUrl: String,
    val updatedAt: String,
    val isFork: Boolean = false,
    val availablePlatforms: List<String> = emptyList(),
)
