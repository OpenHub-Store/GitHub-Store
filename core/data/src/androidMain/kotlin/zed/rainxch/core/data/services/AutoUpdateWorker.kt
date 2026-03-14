package zed.rainxch.core.data.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import zed.rainxch.core.domain.model.InstalledApp
import zed.rainxch.core.domain.model.InstallerType
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.ThemesRepository
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.data.services.shizuku.ShizukuServiceManager
import zed.rainxch.core.data.services.shizuku.ShizukuStatus

/**
 * Background worker that automatically downloads and silently installs
 * available updates via Shizuku.
 *
 * Only runs when auto-update is enabled AND Shizuku installer is selected and READY.
 * Falls back gracefully: if Shizuku becomes unavailable mid-update, remaining apps
 * are skipped and a notification is shown for manual update.
 */
class AutoUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val installedAppsRepository: InstalledAppsRepository by inject()
    private val installer: Installer by inject()
    private val downloader: Downloader by inject()
    private val themesRepository: ThemesRepository by inject()
    private val shizukuServiceManager: ShizukuServiceManager by inject()

    override suspend fun doWork(): Result =
        try {
            Logger.i { "AutoUpdateWorker: Starting auto-update" }

            // Double-check preferences (they may have changed since scheduling)
            val autoUpdateEnabled = themesRepository.getAutoUpdateEnabled().first()
            val installerType = themesRepository.getInstallerType().first()

            // Refresh Shizuku status directly — don't rely on app-process cached state,
            // since this worker may run in a cold process where listeners weren't initialized.
            shizukuServiceManager.refreshStatus()
            val shizukuReady = shizukuServiceManager.status.value == ShizukuStatus.READY

            if (!autoUpdateEnabled || installerType != InstallerType.SHIZUKU || !shizukuReady) {
                Logger.i { "AutoUpdateWorker: Conditions not met (autoUpdate=$autoUpdateEnabled, installer=$installerType, shizuku=$shizukuReady), skipping" }
                return Result.success()
            }

            val appsWithUpdates = installedAppsRepository.getAppsWithUpdates().first()
            if (appsWithUpdates.isEmpty()) {
                Logger.d { "AutoUpdateWorker: No apps need updating" }
                return Result.success()
            }

            setForeground(createForegroundInfo("Updating apps...", 0, appsWithUpdates.size))

            val successfulApps = mutableListOf<String>()
            val failedApps = mutableListOf<String>()

            appsWithUpdates.forEachIndexed { index, app ->
                setForeground(
                    createForegroundInfo(
                        "Updating ${app.appName}...",
                        index + 1,
                        appsWithUpdates.size,
                    ),
                )

                try {
                    updateApp(app)
                    successfulApps.add(app.appName)
                    Logger.i { "AutoUpdateWorker: Successfully updated ${app.appName}" }
                } catch (e: Exception) {
                    failedApps.add(app.appName)
                    Logger.e { "AutoUpdateWorker: Failed to update ${app.appName}: ${e.message}" }
                    // Clear pending status on failure
                    try {
                        installedAppsRepository.updatePendingStatus(app.packageName, false)
                    } catch (clearEx: Exception) {
                        Logger.e { "AutoUpdateWorker: Failed to clear pending status: ${clearEx.message}" }
                    }
                }
            }

            // Show summary notification
            showSummaryNotification(successfulApps, failedApps)

            Logger.i { "AutoUpdateWorker: Completed. Success: ${successfulApps.size}, Failed: ${failedApps.size}" }
            Result.success()
        } catch (e: Exception) {
            Logger.e { "AutoUpdateWorker: Fatal error: ${e.message}" }
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure()
            }
        }

    private suspend fun updateApp(app: InstalledApp) {
        val assetUrl = app.latestAssetUrl
            ?: throw IllegalStateException("No asset URL for ${app.appName}")
        val assetName = app.latestAssetName
            ?: throw IllegalStateException("No asset name for ${app.appName}")
        val latestVersion = app.latestVersion
            ?: throw IllegalStateException("No latest version for ${app.appName}")

        val ext = assetName.substringAfterLast('.', "").lowercase()

        // Check if we already have a valid downloaded file
        val existingPath = downloader.getDownloadedFilePath(assetName)
        if (existingPath != null) {
            val file = java.io.File(existingPath)
            try {
                val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(existingPath)
                val normalizedExisting = apkInfo?.versionName?.removePrefix("v")?.removePrefix("V") ?: ""
                val normalizedLatest = latestVersion.removePrefix("v").removePrefix("V")
                if (normalizedExisting != normalizedLatest) {
                    file.delete()
                    Logger.d { "AutoUpdateWorker: Deleted mismatched existing file for ${app.appName}" }
                }
            } catch (e: Exception) {
                file.delete()
                Logger.d { "AutoUpdateWorker: Deleted unextractable existing file for ${app.appName}" }
            }
        }

        // Download the APK
        Logger.d { "AutoUpdateWorker: Downloading $assetName for ${app.appName}" }
        downloader.download(assetUrl, assetName).collect { /* consume flow to completion */ }

        val filePath = downloader.getDownloadedFilePath(assetName)
            ?: throw IllegalStateException("Downloaded file not found for ${app.appName}")

        val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(filePath)
            ?: throw IllegalStateException("Failed to extract APK info for ${app.appName}")

        // Mark as pending install
        val currentApp = installedAppsRepository.getAppByPackage(app.packageName)
        if (currentApp != null) {
            installedAppsRepository.updateApp(
                currentApp.copy(
                    isPendingInstall = true,
                    latestVersion = latestVersion,
                    latestAssetName = assetName,
                    latestAssetUrl = assetUrl,
                    latestVersionName = apkInfo.versionName,
                    latestVersionCode = apkInfo.versionCode,
                ),
            )
        }

        // Silent install via Shizuku (ShizukuInstallerWrapper handles the actual Shizuku call)
        Logger.d { "AutoUpdateWorker: Installing ${app.appName} via Shizuku" }
        try {
            installer.install(filePath, ext)
        } catch (e: Exception) {
            installedAppsRepository.updatePendingStatus(app.packageName, false)
            throw e
        }

        Logger.d { "AutoUpdateWorker: Install command completed for ${app.appName}, waiting for system confirmation via broadcast" }
    }

    private fun createForegroundInfo(
        message: String,
        current: Int,
        total: Int,
    ): ForegroundInfo {
        val builder =
            NotificationCompat
                .Builder(applicationContext, UPDATE_SERVICE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("GitHub Store")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)

        if (total > 0) {
            builder.setProgress(total, current, false)
        }

        val notification = builder.build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showSummaryNotification(
        successfulApps: List<String>,
        failedApps: List<String>,
    ) {
        // Check notification permission for API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        if (successfulApps.isEmpty() && failedApps.isEmpty()) return

        val title = when {
            failedApps.isEmpty() -> "${successfulApps.size} app${if (successfulApps.size > 1) "s" else ""} updated"
            successfulApps.isEmpty() -> "Failed to update ${failedApps.size} app${if (failedApps.size > 1) "s" else ""}"
            else -> "${successfulApps.size} updated, ${failedApps.size} failed"
        }

        val text = when {
            failedApps.isEmpty() -> successfulApps.joinToString(", ")
            successfulApps.isEmpty() -> failedApps.joinToString(", ")
            else -> "Updated: ${successfulApps.joinToString(", ")}. Failed: ${failedApps.joinToString(", ")}"
        }

        val launchIntent =
            applicationContext.packageManager
                .getLaunchIntentForPackage(applicationContext.packageName)
                ?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

        val pendingIntent =
            launchIntent?.let {
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val notification =
            NotificationCompat
                .Builder(applicationContext, UPDATES_CHANNEL_ID)
                .setSmallIcon(
                    if (failedApps.isEmpty()) {
                        android.R.drawable.stat_sys_download_done
                    } else {
                        android.R.drawable.stat_notify_error
                    },
                )
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(applicationContext).notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "github_store_auto_update"
        private const val UPDATES_CHANNEL_ID = "app_updates"
        private const val UPDATE_SERVICE_CHANNEL_ID = "update_service"
        private const val FOREGROUND_NOTIFICATION_ID = 1004
        private const val SUMMARY_NOTIFICATION_ID = 1005
    }
}
