package zed.rainxch.apps.presentation

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
}
