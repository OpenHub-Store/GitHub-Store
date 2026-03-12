package zed.rainxch.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GithubUser(
    val id: Long,
    val login: String,
    val avatarUrl: String,
    val htmlUrl: String,
)
