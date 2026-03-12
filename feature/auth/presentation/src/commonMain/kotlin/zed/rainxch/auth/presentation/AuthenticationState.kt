package zed.rainxch.auth.presentation

import zed.rainxch.auth.presentation.model.AuthLoginState

data class AuthenticationState(
    val loginState: AuthLoginState = AuthLoginState.LoggedOut,
    val copied: Boolean = false,
    val info: String? = null,
)
