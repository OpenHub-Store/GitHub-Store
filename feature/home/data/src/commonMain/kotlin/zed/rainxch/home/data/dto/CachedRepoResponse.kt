package zed.rainxch.home.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CachedRepoResponse(
    val category: String? = null,
    val platform: String,
    val lastUpdated: String,
    val totalCount: Int,
    val repositories: List<CachedGithubRepoSummary>,
)
