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
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.ExternalLinkState
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.system.SystemInstallSerializer
import zed.rainxch.core.domain.model.installation.resolvePendingFromSystem
import zed.rainxch.core.domain.utils.VersionVerdict
import zed.rainxch.core.domain.utils.resolveExternalInstallVerdict

class PackageEventReceiver() :
    BroadcastReceiver(),
    KoinComponent {
    private val installedAppsRepositoryKoin: InstalledAppsRepository by inject()
    private val packageMonitorKoin: PackageMonitor by inject()
    private val appScopeKoin: CoroutineScope by inject()
    private val externalImportRepositoryKoin: ExternalImportRepository by inject()
    private val externalLinkDaoKoin: ExternalLinkDao by inject()
    private val systemInstallSerializerKoin: SystemInstallSerializer by inject()

    private var explicitRepository: InstalledAppsRepository? = null
    private var explicitMonitor: PackageMonitor? = null
    private var explicitExternalImport: ExternalImportRepository? = null
    private var explicitExternalLinkDao: ExternalLinkDao? = null
    private var explicitAppScope: CoroutineScope? = null
    private var explicitSystemInstallSerializer: SystemInstallSerializer? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    constructor(
        installedAppsRepository: InstalledAppsRepository,
        packageMonitor: PackageMonitor,
        externalImportRepository: ExternalImportRepository,
        externalLinkDao: ExternalLinkDao,
        appScope: CoroutineScope,
        systemInstallSerializer: SystemInstallSerializer,
    ) : this() {
        this.explicitRepository = installedAppsRepository
        this.explicitMonitor = packageMonitor
        this.explicitExternalImport = externalImportRepository
        this.explicitExternalLinkDao = externalLinkDao
        this.explicitAppScope = appScope
        this.explicitSystemInstallSerializer = systemInstallSerializer
    }

    private fun getRepository(): InstalledAppsRepository = explicitRepository ?: installedAppsRepositoryKoin

    private fun getMonitor(): PackageMonitor = explicitMonitor ?: packageMonitorKoin

    private fun getExternalImport(): ExternalImportRepository =
        explicitExternalImport ?: externalImportRepositoryKoin

    private fun getExternalLinkDao(): ExternalLinkDao =
        explicitExternalLinkDao ?: externalLinkDaoKoin

    private fun getSystemInstallSerializer(): SystemInstallSerializer =
        explicitSystemInstallSerializer ?: systemInstallSerializerKoin

    private fun getBackstopScope(): CoroutineScope =

        explicitAppScope ?: runCatching { appScopeKoin }.getOrElse { scope }

    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {

        val packageName = intent?.data?.schemeSpecificPart
            ?: if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                context?.packageName
            } else {
                null
            }
            ?: return

        Logger.d { "PackageEventReceiver: ${intent?.action} for $packageName" }

        try {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                -> {
                    scope.launch { onPackageInstalled(packageName) }
                }

                Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                    scope.launch { onPackageRemoved(packageName) }
                }
            }
        } catch (e: Exception) {
            Logger.e { "PackageEventReceiver: Failed to handle ${intent?.action}: ${e.message}" }
        }
    }

    private suspend fun clearParkedInstall(
        repo: InstalledAppsRepository,
        packageName: String,
        parkedFilePath: String?,
    ) {
        runCatching {
            repo.setPendingInstallFilePath(packageName = packageName, path = null)
        }.onFailure {
            Logger.w(it) { "Failed to clear parked install metadata for $packageName" }
        }
        if (parkedFilePath != null) {
            runCatching { java.io.File(parkedFilePath).takeIf { it.exists() }?.delete() }
                .onFailure {
                    Logger.w(it) { "Failed to delete parked APK at $parkedFilePath" }
                }
        }
    }

    private suspend fun onPackageInstalled(packageName: String) {

        getSystemInstallSerializer().markCompleted(packageName)

        try {
            val repo = getRepository()
            val monitor = getMonitor()
            val app = repo.getAppByPackage(packageName)

            if (app != null) {
                if (app.isPendingInstall) {
                    val systemInfo = monitor.getInstalledPackageInfo(packageName)
                    if (systemInfo != null) {
                        val expectedVersionCode = app.latestVersionCode ?: 0L
                        val versionCodeMatchesTarget =
                            expectedVersionCode > 0L &&
                                systemInfo.versionCode >= expectedVersionCode

                        val versionNameChanged =
                            !systemInfo.versionName.isNullOrBlank() &&
                                systemInfo.versionName != app.installedVersionName
                        val wasActuallyUpdated =
                            versionCodeMatchesTarget ||
                                (expectedVersionCode <= 0L && versionNameChanged)

                        val installedTag =
                            app.pendingInstallVersion
                                ?: app.latestVersion
                                ?: systemInfo.versionName
                        if (wasActuallyUpdated) {
                            repo.updateAppVersion(
                                packageName = packageName,
                                newTag = installedTag,
                                newAssetName = app.latestAssetName ?: "",
                                newAssetUrl = app.latestAssetUrl ?: "",
                                newVersionName = systemInfo.versionName,
                                newVersionCode = systemInfo.versionCode,
                                signingFingerprint = app.signingFingerprint,
                            )
                            repo.updatePendingStatus(packageName, false)
                            Logger.i { "Update confirmed via broadcast: $packageName (v${systemInfo.versionName}, tag=$installedTag)" }
                        } else {

                            repo.updateApp(
                                app.resolvePendingFromSystem(
                                    resolvedTag = installedTag,
                                    versionName = systemInfo.versionName,
                                    versionCode = systemInfo.versionCode,
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

                    clearParkedInstall(repo, packageName, app.pendingInstallFilePath)
                } else {
                    handleExternalInstall(packageName, app, repo, monitor)
                }
            }
        } catch (e: Exception) {
            Logger.e { "PackageEventReceiver error for $packageName: ${e.message}" }
        }

        getBackstopScope().launch {
            runCatching {
                val rescan = shouldRescan(packageName)
                if (rescan) {
                    getExternalImport().runDeltaScan(setOf(packageName))
                }
            }.onFailure {
                Logger.w(it) { "Delta scan failed for $packageName" }
            }
        }
    }

    private suspend fun shouldRescan(packageName: String): Boolean {
        val tracked = runCatching { getRepository().getAppByPackage(packageName) }
            .getOrNull()
        if (tracked != null) return false
        val link = runCatching { getExternalLinkDao().get(packageName) }.getOrNull()
        val state = link?.state ?: return true
        return state != ExternalLinkState.MATCHED.name &&
            state != ExternalLinkState.NEVER_ASK.name
    }

    private suspend fun handleExternalInstall(
        packageName: String,
        app: zed.rainxch.core.domain.model.installation.InstalledApp,
        repo: InstalledAppsRepository,
        monitor: PackageMonitor,
    ) {
        val systemInfo = monitor.getInstalledPackageInfo(packageName) ?: return
        val versionChanged =
            systemInfo.versionCode != app.installedVersionCode ||
                systemInfo.versionName != app.installedVersionName
        if (!versionChanged) {
            Logger.d {
                "Broadcast touch with no version change: $packageName (v${systemInfo.versionName})"
            }
            return
        }

        val verdict =
            resolveExternalInstallVerdict(
                app = app,
                newVersionName = systemInfo.versionName,
                newVersionCode = systemInfo.versionCode,
            )

        val newIsUpdateAvailable =
            when (verdict) {
                VersionVerdict.UP_TO_DATE -> false
                VersionVerdict.UPDATE_AVAILABLE -> true

                VersionVerdict.UNKNOWN -> app.isUpdateAvailable
            }

        repo.updateInstalledVersion(
            packageName = packageName,
            installedVersion = app.installedVersion,
            installedVersionName = systemInfo.versionName,
            installedVersionCode = systemInfo.versionCode,
            isUpdateAvailable = newIsUpdateAvailable,
        )

        Logger.i {
            "External version change via broadcast: $packageName " +
                "DB v${app.installedVersionName}(${app.installedVersionCode}) → " +
                "System v${systemInfo.versionName}(${systemInfo.versionCode}), " +
                "verdict=$verdict, updateAvailable=$newIsUpdateAvailable"
        }

        getBackstopScope().launch {
            try {
                repo.checkForUpdates(packageName)
                Logger.d {
                    "External-install re-validation completed for $packageName"
                }
            } catch (e: Exception) {
                Logger.w {
                    "External-install re-validation failed for $packageName: ${e.message}"
                }
            }
        }
    }

    private suspend fun onPackageRemoved(packageName: String) {

        getSystemInstallSerializer().markCompleted(packageName)

        try {
            getRepository().deleteInstalledApp(packageName)
            runCatching { getExternalImport().unlink(packageName) }
                .onFailure { initialError ->
                    Logger.w(initialError) { "External link cleanup failed for $packageName; scheduling retry" }

                    getBackstopScope().launch {
                        kotlinx.coroutines.delay(UNLINK_RETRY_DELAY_MS)
                        runCatching { getExternalImport().unlink(packageName) }
                            .onSuccess {
                                Logger.i { "External link cleanup retry succeeded for $packageName" }

                                runCatching {
                                    if (shouldRescan(packageName)) {
                                        getExternalImport().runDeltaScan(setOf(packageName))
                                    }
                                }.onFailure { e ->
                                    Logger.w(e) { "Post-retry delta scan failed for $packageName" }
                                }
                            }
                            .onFailure { retryError ->
                                Logger.w(retryError) {
                                    "External link cleanup final failure for $packageName; " +
                                        "row may persist until next periodic scan"
                                }
                            }
                    }
                }
            Logger.i { "Removed uninstalled app via broadcast: $packageName" }
        } catch (e: Exception) {
            Logger.e { "PackageEventReceiver remove error for $packageName: ${e.message}" }
        }
    }

    companion object {
        private const val UNLINK_RETRY_DELAY_MS: Long = 1_000

        fun createIntentFilter(): IntentFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
                addDataScheme("package")
            }
    }
}
