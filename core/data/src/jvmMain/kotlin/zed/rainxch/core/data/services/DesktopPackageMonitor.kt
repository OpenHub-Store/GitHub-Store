package zed.rainxch.core.data.services

import zed.rainxch.core.domain.model.DeviceApp
import zed.rainxch.core.domain.model.SystemPackageInfo
import zed.rainxch.core.domain.system.PackageMonitor

class DesktopPackageMonitor : PackageMonitor {
    override suspend fun isPackageInstalled(packageName: String): Boolean = false

    override suspend fun getInstalledPackageInfo(packageName: String): SystemPackageInfo? = null

    override suspend fun getAllInstalledPackageNames(): Set<String> = setOf()

    override suspend fun getAllInstalledApps(): List<DeviceApp> = emptyList()
}
