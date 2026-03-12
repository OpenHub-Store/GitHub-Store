package zed.rainxch.core.domain.system

import zed.rainxch.core.domain.model.SystemPackageInfo

interface PackageMonitor {
    suspend fun isPackageInstalled(packageName: String): Boolean

    suspend fun getInstalledPackageInfo(packageName: String): SystemPackageInfo?

    suspend fun getAllInstalledPackageNames(): Set<String>
}
