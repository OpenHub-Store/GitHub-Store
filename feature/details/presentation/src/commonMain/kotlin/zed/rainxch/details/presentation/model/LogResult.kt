package zed.rainxch.details.presentation.model

sealed class LogResult {
    data object DownloadStarted : LogResult()

    data object UpdateStarted : LogResult()

    data object Downloaded : LogResult()

    data object InstallStarted : LogResult()

    data object Installed : LogResult()

    data object Updated : LogResult()

    data object Cancelled : LogResult()

    data object PreparingForAppManager : LogResult()

    data object OpenedInAppManager : LogResult()

    data object PermissionBlocked : LogResult()

    data object OpenedInExternalInstaller : LogResult()

    data class Error(
        val message: String?,
    ) : LogResult()

    data class Info(
        val message: String,
    ) : LogResult()
}
