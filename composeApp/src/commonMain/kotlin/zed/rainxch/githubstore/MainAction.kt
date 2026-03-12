package zed.rainxch.githubstore

sealed interface MainAction {
    data object DismissRateLimitDialog : MainAction

    data object DismissSessionExpiredDialog : MainAction
}
