package zed.rainxch.auth.presentation

sealed interface AuthenticationEvents {
    data object OnNavigateToMain : AuthenticationEvents
}
