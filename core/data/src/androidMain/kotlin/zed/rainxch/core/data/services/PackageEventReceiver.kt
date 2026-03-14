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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.PackageMonitor

/**
 * Listens for package install/replace/remove broadcasts to update tracked app state.
 *
 * Registered both statically (manifest — works when process is dead, e.g. after
 * Shizuku silent install) and dynamically (GithubStoreApp — immediate in-process delivery).
 *
 * Uses [KoinComponent] for the no-arg constructor path (manifest-registered).
 * The constructor with explicit dependencies is used for dynamic registration.
 */
class PackageEventReceiver() : BroadcastReceiver(), KoinComponent {
    private val installedAppsRepositoryKoin: InstalledAppsRepository by inject()
    private val packageMonitorKoin: PackageMonitor by inject()

    // Explicitly provided dependencies (dynamic registration path)
    private var explicitRepository: InstalledAppsRepository? = null
    private var explicitMonitor: PackageMonitor? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    constructor(
        installedAppsRepository: InstalledAppsRepository,
        packageMonitor: PackageMonitor,
    ) : this() {
        this.explicitRepository = installedAppsRepository
        this.explicitMonitor = packageMonitor
    }

    private fun getRepository(): InstalledAppsRepository =
        explicitRepository ?: installedAppsRepositoryKoin

    private fun getMonitor(): PackageMonitor =
        explicitMonitor ?: packageMonitorKoin

    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        val packageName = intent?.data?.schemeSpecificPart ?: return

        Logger.d { "PackageEventReceiver: ${intent.action} for $packageName" }

        try {
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
        } catch (e: Exception) {
            Logger.e { "PackageEventReceiver: Failed to handle ${intent.action}: ${e.message}" }
        }
    }

    private suspend fun onPackageInstalled(packageName: String) {
        try {
            val repo = getRepository()
            val monitor = getMonitor()
            val app = repo.getAppByPackage(packageName) ?: return

            if (app.isPendingInstall) {
                val systemInfo = monitor.getInstalledPackageInfo(packageName)
                if (systemInfo != null) {
                    val expectedVersionCode = app.latestVersionCode ?: 0L
                    val wasActuallyUpdated =
                        expectedVersionCode > 0L &&
                            systemInfo.versionCode >= expectedVersionCode

                    if (wasActuallyUpdated) {
                        repo.updateAppVersion(
                            packageName = packageName,
                            newTag = app.latestVersion ?: systemInfo.versionName,
                            newAssetName = app.latestAssetName ?: "",
                            newAssetUrl = app.latestAssetUrl ?: "",
                            newVersionName = systemInfo.versionName,
                            newVersionCode = systemInfo.versionCode,
                        )
                        repo.updatePendingStatus(packageName, false)
                        Logger.i { "Update confirmed via broadcast: $packageName (v${systemInfo.versionName})" }
                    } else {
                        repo.updateApp(
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
                    repo.updatePendingStatus(packageName, false)
                    Logger.i { "Resolved pending install via broadcast (no system info): $packageName" }
                }
            } else {
                val systemInfo = monitor.getInstalledPackageInfo(packageName)
                if (systemInfo != null) {
                    repo.updateApp(
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
            getRepository().deleteInstalledApp(packageName)
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
