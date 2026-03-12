package zed.rainxch.core.data.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase

/**
 * Periodic background worker that checks all tracked installed apps for available updates.
 *
 * Runs via WorkManager on a configurable schedule (default: every 6 hours).
 * First syncs app state with the system package manager, then checks each
 * tracked app's GitHub repository for new releases.
 * Shows a notification when updates are found.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val installedAppsRepository: InstalledAppsRepository by inject()
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase by inject()

    override suspend fun doWork(): Result =
        try {
            Logger.i { "UpdateCheckWorker: Starting periodic update check" }

            // First sync installed apps state with system
            val syncResult = syncInstalledAppsUseCase()
            if (syncResult.isFailure) {
                Logger.w { "UpdateCheckWorker: Sync had issues: ${syncResult.exceptionOrNull()?.message}" }
            }

            // Check all tracked apps for updates
            installedAppsRepository.checkAllForUpdates()

            // Show notification if any updates are available
            showUpdateNotificationIfNeeded()

            Logger.i { "UpdateCheckWorker: Periodic update check completed successfully" }
            Result.success()
        } catch (e: Exception) {
            Logger.e { "UpdateCheckWorker: Update check failed: ${e.message}" }
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }

    @SuppressLint("MissingPermission") // Permission checked at runtime before notify()
    private suspend fun showUpdateNotificationIfNeeded() {
        val appsWithUpdates = installedAppsRepository.getAppsWithUpdates().first()
        if (appsWithUpdates.isEmpty()) {
            Logger.d { "UpdateCheckWorker: No updates available, skipping notification" }
            return
        }

        // Check notification permission for API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Logger.w { "UpdateCheckWorker: POST_NOTIFICATIONS permission not granted, skipping notification" }
                return
            }
        }

        val title =
            if (appsWithUpdates.size == 1) {
                "${appsWithUpdates.first().appName} update available"
            } else {
                "${appsWithUpdates.size} app updates available"
            }

        val text =
            if (appsWithUpdates.size == 1) {
                val app = appsWithUpdates.first()
                "${app.installedVersion} → ${app.latestVersion}"
            } else {
                appsWithUpdates.joinToString(", ") { it.appName }
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
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        Logger.i { "UpdateCheckWorker: Showed notification for ${appsWithUpdates.size} updates" }
    }

    companion object {
        const val WORK_NAME = "github_store_update_check"
        private const val UPDATES_CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 1001
    }
}
