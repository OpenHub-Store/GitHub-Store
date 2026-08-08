package zed.rainxch.feed.presentation

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi
import zed.rainxch.core.domain.model.repository.FeedCategory

sealed interface FeedAction {
    data object OnRefresh : FeedAction
    data object OnRetry : FeedAction
    data object OnLoadMore : FeedAction
    data object OnSearchClick : FeedAction
    data object OnProfileClick : FeedAction
    data object OnPlatformPickerOpen : FeedAction
    data object OnPlatformPickerDismiss : FeedAction
    data object OnResetFilters : FeedAction
    data class OnPlatformSelected(val platform: DiscoveryPlatform) : FeedAction
    data class OnCategorySelected(val category: FeedCategory) : FeedAction
    data class OnRepoClick(val repo: GithubRepoSummaryUi) : FeedAction
    data class OnShareClick(val repo: GithubRepoSummaryUi) : FeedAction
    data class OnHideRepository(val repo: GithubRepoSummaryUi) : FeedAction
    data class OnMarkAsSeen(val repo: GithubRepoSummaryUi) : FeedAction
    data class OnMarkAsUnseen(val repoId: Long) : FeedAction
    data object OnToggleLayoutType : FeedAction
}
