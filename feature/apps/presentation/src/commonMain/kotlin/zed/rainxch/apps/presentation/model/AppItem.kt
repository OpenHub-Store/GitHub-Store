package zed.rainxch.apps.presentation.model

data class AppItem(
    val installedApp: InstalledAppUi,
    val updateState: UpdateState = UpdateState.Idle,
    val downloadProgress: Int? = null,
    val error: String? = null,
)
