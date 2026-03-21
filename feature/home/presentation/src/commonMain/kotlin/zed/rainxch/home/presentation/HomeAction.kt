package zed.rainxch.home.presentation

import zed.rainxch.core.domain.model.DiscoveryPlatform
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.domain.model.TopicCategory

sealed interface HomeAction {
    data object Refresh : HomeAction

    data object Retry : HomeAction

    data object LoadMore : HomeAction

    data object OnSearchClick : HomeAction

    data object OnSettingsClick : HomeAction

    data object OnAppsClick : HomeAction

    data object OnTogglePlatformPopup : HomeAction

    data class OnShareClick(
        val repo: GithubRepoSummaryUi,
    ) : HomeAction

    data class SwitchCategory(
        val category: HomeCategory,
    ) : HomeAction

    data class SwitchTopic(
        val topic: TopicCategory?,
    ) : HomeAction

    data class SwitchFilterPlatform(
        val platform: DiscoveryPlatform,
    ) : HomeAction

    data class OnRepositoryClick(
        val repo: GithubRepoSummaryUi,
    ) : HomeAction

    data class OnRepositoryDeveloperClick(
        val username: String,
    ) : HomeAction
}
