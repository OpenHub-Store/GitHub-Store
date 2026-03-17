package zed.rainxch.apps.presentation.model

import kotlinx.serialization.Serializable

data class GithubUserUi(
    val id: Long,
    val login: String,
    val avatarUrl: String,
    val htmlUrl: String,
)
