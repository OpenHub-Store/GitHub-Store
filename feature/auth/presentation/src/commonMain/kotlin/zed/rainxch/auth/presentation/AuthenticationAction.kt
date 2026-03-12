package zed.rainxch.auth.presentation

import zed.rainxch.core.domain.model.GithubDeviceStart

sealed interface AuthenticationAction {
    data object StartLogin : AuthenticationAction

    data class CopyCode(
        val start: GithubDeviceStart,
    ) : AuthenticationAction

    data class OpenGitHub(
        val start: GithubDeviceStart,
    ) : AuthenticationAction

    data object MarkLoggedOut : AuthenticationAction

    data object MarkLoggedIn : AuthenticationAction

    data class OnInfo(
        val message: String,
    ) : AuthenticationAction

    data object SkipLogin : AuthenticationAction
}
