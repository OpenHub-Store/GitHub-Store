package zed.rainxch.core.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.PackageMonitor

class PackageEventReceiver(
    private val installedAppsRepository: InstalledAppsRepository,
    private val packageMonitor: PackageMonitor,
) : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        val packageName = intent?.data?.schemeSpecificPart ?: return

        Logger.d { "PackageEventReceiver: ${intent.action} for $packageName" }

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            -> {
                scope.launch { onPackageInstalled(packageName) }
            }

            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                scope.launch { onPackageRemoved(packageName) }
            }
        }
    }

    private suspend fun onPackageInstalled(packageName: String) {
        try {
            val app = installedAppsRepository.getAppByPackage(packageName) ?: return

            if (app.isPendingInstall) {
                val systemInfo = packageMonitor.getInstalledPackageInfo(packageName)
                if (systemInfo != null) {
                    val expectedVersionCode = app.latestVersionCode ?: 0L
                    val wasActuallyUpdated =
                        expectedVersionCode > 0L &&
                            systemInfo.versionCode >= expectedVersionCode

                    if (wasActuallyUpdated) {
                        installedAppsRepository.updateAppVersion(
                            packageName = packageName,
                            newTag = app.latestVersion ?: systemInfo.versionName,
                            newAssetName = app.latestAssetName ?: "",
                            newAssetUrl = app.latestAssetUrl ?: "",
                            newVersionName = systemInfo.versionName,
                            newVersionCode = systemInfo.versionCode,
                        )
                        installedAppsRepository.updatePendingStatus(packageName, false)
                        Logger.i { "Update confirmed via broadcast: $packageName (v${systemInfo.versionName})" }
                    } else {
                        installedAppsRepository.updateApp(
                            app.copy(
                                isPendingInstall = false,
                                installedVersionName = systemInfo.versionName,
                                installedVersionCode = systemInfo.versionCode,
                                isUpdateAvailable =
                                    (
                                        app.latestVersionCode
                                            ?: 0L
                                    ) > systemInfo.versionCode,
                            ),
                        )
                        Logger.i {
                            "Package replaced but not updated to target: $packageName " +
                                "(system: v${systemInfo.versionName}/${systemInfo.versionCode}, " +
                                "target: v${app.latestVersionName}/${app.latestVersionCode})"
                        }
                    }
                } else {
                    installedAppsRepository.updatePendingStatus(packageName, false)
                    Logger.i { "Resolved pending install via broadcast (no system info): $packageName" }
                }
            } else {
                val systemInfo = packageMonitor.getInstalledPackageInfo(packageName)
                if (systemInfo != null) {
                    installedAppsRepository.updateApp(
                        app.copy(
                            installedVersionName = systemInfo.versionName,
                            installedVersionCode = systemInfo.versionCode,
                        ),
                    )
                    Logger.d { "Updated version info via broadcast: $packageName (v${systemInfo.versionName})" }
                }
            }
        } catch (e: Exception) {
            Logger.e { "PackageEventReceiver error for $packageName: ${e.message}" }
        }
    }

    private suspend fun onPackageRemoved(packageName: String) {
        try {
            installedAppsRepository.deleteInstalledApp(packageName)
            Logger.i { "Removed uninstalled app via broadcast: $packageName" }
        } catch (e: Exception) {
            Logger.e { "PackageEventReceiver remove error for $packageName: ${e.message}" }
        }
    }

    companion object {
        fun createIntentFilter(): IntentFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addDataScheme("package")
            }
    }
}
