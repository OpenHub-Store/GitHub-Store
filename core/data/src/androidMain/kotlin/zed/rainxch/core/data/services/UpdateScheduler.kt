package zed.rainxch.core.data.services

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    private const val DEFAULT_INTERVAL_HOURS = 6L
    private const val IMMEDIATE_CHECK_WORK_NAME = "github_store_immediate_update_check"

    fun schedule(
        context: Context,
        intervalHours: Long = DEFAULT_INTERVAL_HOURS,
    ) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                repeatInterval = intervalHours,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            ).setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.MINUTES,
                ).build()

        // KEEP preserves the existing schedule so reopening the app doesn't reset the timer.
        // The schedule is only created fresh on first install or after cancel().
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(
                uniqueWorkName = UpdateCheckWorker.WORK_NAME,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
                request = request,
            )

        // Run an immediate one-time check so users get notified sooner
        // rather than waiting up to intervalHours for the first periodic run.
        // Uses REPLACE so each app launch gets a fresh check (the previous one-time
        // work may have already completed).
        val immediateRequest =
            OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                IMMEDIATE_CHECK_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateRequest,
            )

        Logger.i { "UpdateScheduler: Scheduled periodic update check every ${intervalHours}h + immediate check" }
    }

    /**
     * Enqueues a one-time [AutoUpdateWorker] to download and silently install
     * all available updates via Shizuku. Uses KEEP policy to avoid duplicate runs.
     */
    fun scheduleAutoUpdate(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            OneTimeWorkRequestBuilder<AutoUpdateWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.MINUTES,
                )
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                AutoUpdateWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )

        Logger.i { "UpdateScheduler: Scheduled auto-update worker" }
    }

    fun cancel(context: Context) {
        WorkManager
            .getInstance(context)
            .cancelUniqueWork(UpdateCheckWorker.WORK_NAME)
        WorkManager
            .getInstance(context)
            .cancelUniqueWork(IMMEDIATE_CHECK_WORK_NAME)
        WorkManager
            .getInstance(context)
            .cancelUniqueWork(AutoUpdateWorker.WORK_NAME)
        Logger.i { "UpdateScheduler: Cancelled all update work" }
    }
}
