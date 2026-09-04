package zed.rainxch.githubstore.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.data.network.ProxyManager
import zed.rainxch.core.data.services.DownloadNotificationObserver
import zed.rainxch.core.data.services.PackageEventReceiver
import zed.rainxch.core.data.services.UpdateScheduler
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.ProxyRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.system.SystemInstallSerializer
import zed.rainxch.githubstore.app.di.initKoin

class GithubStoreApp : Application() {
    private var packageEventReceiver: PackageEventReceiver? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val proxyRepository: ProxyRepository by inject()
    private val logger: KomiStoreLogger by inject()

    override fun onCreate() {
        super.onCreate()

        initKoin {
            androidContext(this@GithubStoreApp)
        }

        appScope.launch {
            runCatching {
                createNotificationChannels()
            }.onFailure {
                logger.warn("$it - Notification-channel creation failed")
            }
        }

        appScope.launch {
            runCatching {
                registerPackageEventReceiver()
            }.onFailure {
                logger.warn("$it - Dynamic PackageEventReceiver registration failed")
            }
        }

        appScope.launch {
            runCatching {
                startDownloadNotificationObserver()
            }.onFailure {
                logger.warn("$it - Download notification observer start failed")
            }
        }

        scheduleBackgroundUpdateChecks()
        registerSelfAsInstalledApp()
        scheduleInitialExternalScan()
        scheduleSigningSeedSync()
        bootstrapProxy()
    }

    private fun bootstrapProxy() {
        runCatching {
            ProxyManager.bootstrap(
                repository = proxyRepository,
                appScope = appScope,
            )
        }.onFailure {
            logger.warn("$it - Proxy bootstrap failed")
        }
    }

    private fun scheduleInitialExternalScan() {
        appScope.launch {
            runCatching {
                get<ExternalImportRepository>().scheduleInitialScanIfNeeded()
            }.onFailure {
                logger.warn("$it - Initial external scan scheduling failed")
            }
        }
    }

    private fun scheduleSigningSeedSync() {
        appScope.launch {
            runCatching {
                get<ExternalImportRepository>().syncSigningFingerprintSeed()
            }.onFailure {
                logger.warn("$it - Signing seed sync failed")
            }
        }
    }

    private fun startDownloadNotificationObserver() {
        get<DownloadNotificationObserver>().start(get<CoroutineScope>())
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val updatesChannel =
            NotificationChannel(
                UPDATES_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications when app updates are available"
            }
        notificationManager.createNotificationChannel(updatesChannel)

        val serviceChannel =
            NotificationChannel(
                UPDATE_SERVICE_CHANNEL_ID,
                "Update Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background update check and auto-update progress"
                setShowBadge(false)
            }
        notificationManager.createNotificationChannel(serviceChannel)

        val downloadsChannel =
            NotificationChannel(
                DOWNLOADS_CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Live progress for in-flight downloads"
                setShowBadge(false)
            }
        notificationManager.createNotificationChannel(downloadsChannel)
    }

    private fun registerPackageEventReceiver() {
        val receiver =
            PackageEventReceiver(
                installedAppsRepository = get<InstalledAppsRepository>(),
                packageMonitor = get<PackageMonitor>(),
                externalImportRepository = get<ExternalImportRepository>(),
                externalLinkDao = get<ExternalLinkDao>(),
                appScope = get<CoroutineScope>(),
                systemInstallSerializer = get<SystemInstallSerializer>(),
            )
        val filter = PackageEventReceiver.createIntentFilter()

        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        packageEventReceiver = receiver
    }

    private fun scheduleBackgroundUpdateChecks() {
        appScope.launch {
            try {
                val tweaks = get<TweaksRepository>()
                val enabled = tweaks.getUpdateCheckEnabled().first()
                if (!enabled) {
                    UpdateScheduler.cancel(this@GithubStoreApp)
                    Logger.i { "Background update check disabled — skipping schedule" }
                    return@launch
                }
                val intervalHours = tweaks.getUpdateCheckInterval().first()
                UpdateScheduler.schedule(
                    context = this@GithubStoreApp,
                    intervalHours = intervalHours,
                )
            } catch (e: Exception) {
                Logger.e(e) { "Failed to schedule background update checks" }
            }
        }
    }

    private fun registerSelfAsInstalledApp() {
        appScope.launch {
            try {
                val repo = get<InstalledAppsRepository>()
                val selfPackageName = packageName
                val existing = repo.getAppByPackage(selfPackageName)

                if (existing != null) {
                    if (existing.isPendingInstall) {
                        resolveSelfPendingInstall(existing, repo)
                    } else {
                        normalizeSelfInstalledVersion(existing, repo)
                    }
                    return@launch
                }

                val packageMonitor = get<PackageMonitor>()
                val systemInfo = packageMonitor.getInstalledPackageInfo(selfPackageName)
                if (systemInfo == null) {
                    logger.warn("GithubStoreApp: Skip self-registration, package info missing for $selfPackageName")
                    return@launch
                }

                val now = System.currentTimeMillis()
                val versionName = systemInfo.versionName
                val versionCode = systemInfo.versionCode

                val selfApp =
                    InstalledApp(
                        packageName = selfPackageName,
                        repoId = SELF_REPO_ID,
                        repoName = SELF_REPO_NAME,
                        repoOwner = SELF_REPO_OWNER,
                        repoOwnerAvatarUrl = SELF_AVATAR_URL,
                        repoDescription = "A cross-platform app store for GitHub releases",
                        primaryLanguage = "Kotlin",
                        repoUrl = "https://github.com/$SELF_REPO_OWNER/$SELF_REPO_NAME",
                        installedVersion = versionName,
                        installedAssetName = null,
                        installedAssetUrl = null,
                        latestVersion = null,
                        latestAssetName = null,
                        latestAssetUrl = null,
                        latestAssetSize = null,
                        appName = "Komi Store",
                        installSource = InstallSource.THIS_APP,
                        installedAt = now,
                        lastCheckedAt = 0L,
                        lastUpdatedAt = now,
                        isUpdateAvailable = false,
                        updateCheckEnabled = true,
                        releaseNotes = null,
                        systemArchitecture = "",
                        fileExtension = "apk",
                        isPendingInstall = false,
                        installedVersionName = versionName,
                        installedVersionCode = versionCode,
                        signingFingerprint = SELF_SHA256_FINGERPRINT,
                    )

                repo.saveInstalledApp(selfApp)
                Logger.i("Komi Store App: App added")
            } catch (e: Exception) {
                Logger.e(e) { "Komi Store App: Failed to register self as installed app" }
            }
        }
    }

    private suspend fun normalizeSelfInstalledVersion(
        existing: InstalledApp,
        repo: InstalledAppsRepository,
    ) {
        val latestTag = existing.latestVersion ?: return
        if (existing.installedVersion == latestTag) return
        try {
            val packageMonitor = get<PackageMonitor>()
            val systemInfo = packageMonitor.getInstalledPackageInfo(packageName) ?: return
            if (systemInfo.versionCode != existing.installedVersionCode) return
            repo.updateApp(
                existing.copy(
                    installedVersion = latestTag,
                    isUpdateAvailable = false,
                ),
            )
            Logger.i { "Normalized stale self installedVersion tag to $latestTag" }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to normalize self installedVersion tag" }
        }
    }

    private suspend fun resolveSelfPendingInstall(
        existing: InstalledApp,
        repo: InstalledAppsRepository,
    ) {
        try {
            val packageMonitor = get<PackageMonitor>()
            val systemInfo = packageMonitor.getInstalledPackageInfo(packageName)
            if (systemInfo != null) {
                val latestVersionCode = existing.latestVersionCode ?: 0L

                val resolvedTag = existing.latestVersion ?: systemInfo.versionName
                repo.updateApp(
                    existing.copy(
                        isPendingInstall = false,
                        installedVersion = resolvedTag,
                        installedVersionName = systemInfo.versionName,
                        installedVersionCode = systemInfo.versionCode,
                        isUpdateAvailable = latestVersionCode > systemInfo.versionCode,
                    ),
                )
                Logger.i {
                    "Resolved self-update pending install: ${systemInfo.versionName} (code=${systemInfo.versionCode}, tag=$resolvedTag)"
                }
            } else {
                repo.updatePendingStatus(packageName, false)
                Logger.i { "Resolved self-update pending install (no system info)" }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to resolve self-update pending install" }
        }
    }

    companion object {
        private const val SELF_REPO_ID = 1101281251L
        private const val SELF_SHA256_FINGERPRINT =
            @Suppress("ktlint:standard:max-line-length")
            "B7:F2:8E:19:8E:48:C1:93:B0:38:C6:5D:92:DD:F7:BC:07:7B:0D:B5:9E:BC:9B:25:0A:6D:AC:48:C1:18:03:CA"
        private const val SELF_REPO_OWNER = "komi-store"
        private const val SELF_REPO_NAME = "komi-store"
        private const val SELF_AVATAR_URL =
            @Suppress("ktlint:standard:max-line-length")
            "https://raw.githubusercontent.com/komi-store/komi-store/refs/heads/main/media-resources/app_icon.png"
        const val UPDATES_CHANNEL_ID = "app_updates"
        const val UPDATE_SERVICE_CHANNEL_ID = "update_service"
        const val DOWNLOADS_CHANNEL_ID = "app_downloads"
    }
}
