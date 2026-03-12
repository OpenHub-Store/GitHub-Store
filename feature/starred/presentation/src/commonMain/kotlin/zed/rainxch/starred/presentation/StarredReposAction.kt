package zed.rainxch.starred.presentation

import zed.rainxch.starred.presentation.model.StarredRepositoryUi

sealed interface StarredReposAction {
    data object OnNavigateBackClick : StarredReposAction

    data object OnRefresh : StarredReposAction

    data object OnRetrySync : StarredReposAction

    data object OnSignInClick : StarredReposAction

    data object OnDismissError : StarredReposAction

    data class OnRepositoryClick(
        val repository: StarredRepositoryUi,
    ) : StarredReposAction

    data class OnDeveloperProfileClick(
        val username: String,
    ) : StarredReposAction

    data class OnToggleFavorite(
        val repository: StarredRepositoryUi,
    ) : StarredReposAction
}
