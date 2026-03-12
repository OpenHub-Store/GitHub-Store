package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.InstalledApp

interface AppLauncher {
    suspend fun launchApp(installedApp: InstalledApp): Result<Unit>

    suspend fun canLaunchApp(installedApp: InstalledApp): Boolean
}
