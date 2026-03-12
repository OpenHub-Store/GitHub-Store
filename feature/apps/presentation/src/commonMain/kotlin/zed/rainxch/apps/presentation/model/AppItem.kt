package zed.rainxch.apps.presentation.model

import zed.rainxch.core.domain.model.InstalledApp

data class AppItem(
    val installedApp: InstalledApp,
    val updateState: UpdateState = UpdateState.Idle,
    val downloadProgress: Int? = null,
    val error: String? = null,
)
