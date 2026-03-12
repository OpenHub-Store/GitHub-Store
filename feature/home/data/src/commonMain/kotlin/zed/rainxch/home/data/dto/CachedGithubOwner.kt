package zed.rainxch.home.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CachedGithubOwner(
    val login: String,
    val avatarUrl: String,
)
