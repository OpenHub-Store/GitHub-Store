package zed.rainxch.home.presentation

import zed.rainxch.core.domain.model.InstalledApp
import zed.rainxch.core.presentation.model.DiscoveryRepository
import zed.rainxch.home.domain.model.HomeCategory

data class HomeState(
    val repos: List<DiscoveryRepository> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val hasMorePages: Boolean = true,
    val currentCategory: HomeCategory = HomeCategory.TRENDING,
    val isAppsSectionVisible: Boolean = false,
    val isUpdateAvailable: Boolean = false,
)
