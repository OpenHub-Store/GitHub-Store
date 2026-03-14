package zed.rainxch.apps.presentation

import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.model.UpdateAllProgress
import zed.rainxch.core.domain.model.DeviceApp
import zed.rainxch.core.domain.model.InstalledApp

data class AppsState(
    val apps: List<AppItem> = emptyList(),
    val filteredApps: List<AppItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isUpdatingAll: Boolean = false,
    val updateAllProgress: UpdateAllProgress? = null,
    val updateAllButtonEnabled: Boolean = true,
    val isCheckingForUpdates: Boolean = false,
    val lastCheckedTimestamp: Long? = null,
    val isRefreshing: Boolean = false,
    // Link app to repo
    val showLinkSheet: Boolean = false,
    val linkStep: LinkStep = LinkStep.PickApp,
    val deviceApps: List<DeviceApp> = emptyList(),
    val deviceAppSearchQuery: String = "",
    val selectedDeviceApp: DeviceApp? = null,
    val repoUrl: String = "",
    val isValidatingRepo: Boolean = false,
    val repoValidationError: String? = null,
    val fetchedRepoInfo: GithubRepoInfo? = null,
    // Export/Import
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    // Uninstall confirmation
    val appPendingUninstall: InstalledApp? = null,
) {
    val filteredDeviceApps: List<DeviceApp>
        get() = if (deviceAppSearchQuery.isBlank()) {
            deviceApps
        } else {
            deviceApps.filter {
                it.appName.contains(deviceAppSearchQuery, ignoreCase = true) ||
                    it.packageName.contains(deviceAppSearchQuery, ignoreCase = true)
            }
        }
}

enum class LinkStep {
    PickApp,
    EnterUrl,
}
