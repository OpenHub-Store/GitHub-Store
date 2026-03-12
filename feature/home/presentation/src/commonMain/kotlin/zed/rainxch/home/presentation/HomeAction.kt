package zed.rainxch.home.presentation

import zed.rainxch.core.domain.model.GithubRepoSummary
import zed.rainxch.home.domain.model.HomeCategory

sealed interface HomeAction {
    data object Refresh : HomeAction

    data object Retry : HomeAction

    data object LoadMore : HomeAction

    data object OnSearchClick : HomeAction

    data object OnSettingsClick : HomeAction

    data object OnAppsClick : HomeAction

    data class OnShareClick(
        val repo: GithubRepoSummary,
    ) : HomeAction

    data class SwitchCategory(
        val category: HomeCategory,
    ) : HomeAction

    data class OnRepositoryClick(
        val repo: GithubRepoSummary,
    ) : HomeAction

    data class OnRepositoryDeveloperClick(
        val username: String,
    ) : HomeAction
}
