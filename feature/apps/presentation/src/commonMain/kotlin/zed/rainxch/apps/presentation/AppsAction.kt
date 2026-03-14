package zed.rainxch.apps.presentation

import zed.rainxch.core.domain.model.DeviceApp
import zed.rainxch.core.domain.model.InstalledApp

sealed interface AppsAction {
    data object OnNavigateBackClick : AppsAction

    data class OnSearchChange(
        val query: String,
    ) : AppsAction

    data class OnOpenApp(
        val app: InstalledApp,
    ) : AppsAction

    data class OnUpdateApp(
        val app: InstalledApp,
    ) : AppsAction

    data class OnCancelUpdate(
        val packageName: String,
    ) : AppsAction

    data object OnUpdateAll : AppsAction

    data object OnCancelUpdateAll : AppsAction

    data object OnCheckAllForUpdates : AppsAction

    data object OnRefresh : AppsAction

    data class OnNavigateToRepo(
        val repoId: Long,
    ) : AppsAction

    data class OnUninstallApp(
        val app: InstalledApp,
    ) : AppsAction

    // Uninstall confirmation
    data class OnUninstallConfirmed(val app: InstalledApp) : AppsAction
    data object OnDismissUninstallDialog : AppsAction

    // Link app to repo
    data object OnAddByLinkClick : AppsAction
    data object OnDismissLinkSheet : AppsAction
    data class OnDeviceAppSearchChange(val query: String) : AppsAction
    data class OnDeviceAppSelected(val app: DeviceApp) : AppsAction
    data class OnRepoUrlChanged(val url: String) : AppsAction
    data object OnValidateAndLinkRepo : AppsAction
    data object OnBackToAppPicker : AppsAction

    // Export/Import
    data object OnExportApps : AppsAction
    data object OnImportApps : AppsAction
}
