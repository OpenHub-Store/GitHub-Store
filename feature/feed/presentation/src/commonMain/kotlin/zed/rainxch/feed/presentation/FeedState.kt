package zed.rainxch.feed.presentation

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.core.domain.model.repository.FeedCategory

enum class FeedLayoutType {
    LIST,
    GRID
}

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
