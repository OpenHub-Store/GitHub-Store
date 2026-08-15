package zed.rainxch.feed.presentation

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.core.domain.model.repository.FeedCategory

/**
 * Represents the available layout presentations for the feed list.
 */
enum class FeedLayoutType {
    /** Renders feed repositories in a list layout. */
    LIST,
    /** Renders feed repositories in a grid layout. */
    GRID
}

/**
 * Represents the UI state of the feed screen.
 *
 * @property repos The list of repository card UI models currently displayed.
 * @property categories The list of categories available for filtering the feed.
 * @property selectedCategory The currently selected category filter.
 * @property selectedPlatform The currently selected platform filter.
 * @property isPlatformPickerVisible Whether the platform selection modal/picker is visible.
 * @property isLoading Whether the initial loading state is active.
 * @property isRefreshing Whether a swipe-to-refresh reload is in progress.
 * @property isLoadingMore Whether pagination is loading more items at the bottom.
 * @property hasMore Whether there are more pages of items to load.
 * @property isOffline Whether the data was loaded from offline cache or the system is offline.
 * @property errorMessage The error message to display if loading failed, or null.
 * @property layoutType The active layout presentation type (LIST or GRID).
 */
@Immutable
data class FeedState(
    val repos: ImmutableList<DiscoveryRepositoryUi> = persistentListOf(),
    val categories: ImmutableList<FeedCategory> = FeedCategory.entries.toImmutableList(),
    val selectedCategory: FeedCategory = FeedCategory.All,
    val selectedPlatform: DiscoveryPlatform = DiscoveryPlatform.All,
    val isPlatformPickerVisible: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val layoutType: FeedLayoutType = FeedLayoutType.LIST,
)
