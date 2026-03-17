package zed.rainxch.apps.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.apps.presentation.model.GithubAssetUi
import zed.rainxch.apps.presentation.model.GithubRepoInfoUi
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.apps.presentation.model.UpdateAllProgress
import zed.rainxch.core.domain.model.DeviceApp
import zed.rainxch.core.domain.model.GithubAsset

data class AppsState(
    val apps: ImmutableList<AppItem> = persistentListOf(),
    val filteredApps: ImmutableList<AppItem> = persistentListOf(),
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
    val deviceApps: ImmutableList<DeviceAppUi> = persistentListOf(),
    val deviceAppSearchQuery: String = "",
    val selectedDeviceApp: DeviceAppUi? = null,
    val repoUrl: String = "",
    val isValidatingRepo: Boolean = false,
    val repoValidationError: String? = null,
    val linkValidationStatus: String? = null,
    val linkInstallableAssets: ImmutableList<GithubAssetUi> = persistentListOf(),
    val linkSelectedAsset: GithubAssetUi? = null,
    val linkDownloadProgress: Int? = null,
    val fetchedRepoInfo: GithubRepoInfoUi? = null,
    // Export/Import
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    // Uninstall confirmation
    val appPendingUninstall: InstalledAppUi? = null,
) {
    val filteredDeviceApps: ImmutableList<DeviceAppUi>
        get() =
            if (deviceAppSearchQuery.isBlank()) {
                deviceApps.toImmutableList()
            } else {
                deviceApps
                    .filter {
                        it.appName.contains(deviceAppSearchQuery, ignoreCase = true) ||
                            it.packageName.contains(deviceAppSearchQuery, ignoreCase = true)
                    }.toImmutableList()
            }
}

enum class LinkStep {
    PickApp,
    EnterUrl,
    PickAsset,
}
