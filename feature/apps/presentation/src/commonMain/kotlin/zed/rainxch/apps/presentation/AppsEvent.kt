package zed.rainxch.apps.presentation

import zed.rainxch.apps.domain.model.ImportResult

sealed interface AppsEvent {
    data class ShowError(
        val message: String,
    ) : AppsEvent

    data class ShowSuccess(
        val message: String,
    ) : AppsEvent

    data class NavigateToRepo(
        val repoId: Long,
    ) : AppsEvent

    data class AppLinkedSuccessfully(
        val appName: String,
    ) : AppsEvent

    data class ImportComplete(
        val result: ImportResult,
    ) : AppsEvent
}
