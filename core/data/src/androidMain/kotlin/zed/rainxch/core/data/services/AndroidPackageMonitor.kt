package zed.rainxch.core.data.services

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.model.SystemPackageInfo
import zed.rainxch.core.domain.system.PackageMonitor

class AndroidPackageMonitor(
    context: Context,
) : PackageMonitor {
    private val packageManager: PackageManager = context.packageManager

    override suspend fun isPackageInstalled(packageName: String): Boolean = getInstalledPackageInfo(packageName) != null

    override suspend fun getInstalledPackageInfo(packageName: String): SystemPackageInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val packageInfo =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0)
                    }

                val versionCode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }

                SystemPackageInfo(
                    packageName = packageInfo.packageName,
                    versionName = packageInfo.versionName ?: "unknown",
                    versionCode = versionCode,
                    isInstalled = true,
                )
            }.getOrNull()
        }

    override suspend fun getAllInstalledPackageNames(): Set<String> =
        withContext(Dispatchers.IO) {
            val packages =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledPackages(0)
                }

            packages.map { it.packageName }.toSet()
        }
}
