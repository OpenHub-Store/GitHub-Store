package zed.rainxch.details.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RepoStats(
    val stars: Int,
    val forks: Int,
    val openIssues: Int,
)
