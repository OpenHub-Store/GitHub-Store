package zed.rainxch.feed.presentation

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi
import zed.rainxch.core.domain.model.repository.FeedCategory

/**
 * Represents actions/events triggered by the user on the feed screen.
 */
sealed interface FeedAction {
    /** Triggered on swipe-to-refresh. */
    data object OnRefresh : FeedAction
    /** Triggered to retry loading the feed after an error. */
    data object OnRetry : FeedAction
    /** Triggered when scrolling close to the bottom to load the next page. */
    data object OnLoadMore : FeedAction
    /** Triggered when the search button is clicked. */
    data object OnSearchClick : FeedAction
    /** Triggered when the user profile button is clicked. */
    data object OnProfileClick : FeedAction
    /** Triggered to open the platform picker. */
    data object OnPlatformPickerOpen : FeedAction
    /** Triggered to close the platform picker. */
    data object OnPlatformPickerDismiss : FeedAction
    /** Triggered to clear all filters. */
    data object OnResetFilters : FeedAction
    /** Triggered when a platform is selected. */
    data class OnPlatformSelected(val platform: DiscoveryPlatform) : FeedAction
    /** Triggered when a category is selected. */
    data class OnCategorySelected(val category: FeedCategory) : FeedAction
    /** Triggered when a repository card is clicked. */
    data class OnRepoClick(val repo: GithubRepoSummaryUi) : FeedAction
    /** Triggered when a repository is shared. */
    data class OnShareClick(val repo: GithubRepoSummaryUi) : FeedAction
    /** Triggered when a repository is hidden. */
    data class OnHideRepository(val repo: GithubRepoSummaryUi) : FeedAction
    /** Triggered when marking a repository as seen. */
    data class OnMarkAsSeen(val repo: GithubRepoSummaryUi) : FeedAction
    /** Triggered when removing a repository from the seen history. */
    data class OnMarkAsUnseen(val repoId: Long) : FeedAction
    /** Triggered when toggling layout presentation between list and grid. */
    data object OnToggleLayoutType : FeedAction
}
