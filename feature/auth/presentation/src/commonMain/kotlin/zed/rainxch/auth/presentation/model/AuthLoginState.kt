package zed.rainxch.auth.presentation.model

import zed.rainxch.core.domain.model.GithubDeviceStart

sealed interface AuthLoginState {
    data object LoggedOut : AuthLoginState

    data class DevicePrompt(
        val start: GithubDeviceStart,
        val remainingSeconds: Int = 0,
    ) : AuthLoginState

    data object Pending : AuthLoginState

    data object LoggedIn : AuthLoginState

    data class Error(
        val message: String,
        val recoveryHint: String? = null,
    ) : AuthLoginState
}
