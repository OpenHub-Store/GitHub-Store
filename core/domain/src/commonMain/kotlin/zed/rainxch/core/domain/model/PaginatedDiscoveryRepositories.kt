package zed.rainxch.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedDiscoveryRepositories(
    val repos: List<GithubRepoSummary>,
    val hasMore: Boolean,
    val nextPageIndex: Int,
    val totalCount: Int? = null,
)
