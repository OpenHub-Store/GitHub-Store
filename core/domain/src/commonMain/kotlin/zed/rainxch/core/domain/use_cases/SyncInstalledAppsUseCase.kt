package zed.rainxch.core.domain.use_cases

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.InstalledApp
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.PackageMonitor

/**
 * Use case for synchronizing installed apps state with the system package manager.
 *
 * Responsibilities:
 * 1. Remove apps from DB that are no longer installed on the system
 * 2. Migrate legacy apps missing versionName/versionCode fields
 * 3. Resolve pending installs once they appear in the system package manager
 * 4. Clean up stale pending installs (older than 24 hours)
 * 5. Detect external version changes (downgrades on rooted devices, sideloads, etc.)
 *
 * This should be called before loading or refreshing app data to ensure consistency.
 */
class SyncInstalledAppsUseCase(
    private val packageMonitor: PackageMonitor,
    private val installedAppsRepository: InstalledAppsRepository,
    private val platform: Platform,
    private val logger: GitHubStoreLogger,
) {
    companion object {
        private const val PENDING_TIMEOUT_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    /**
     * Executes the sync operation.
     *
     * @return Result indicating success or failure with error details
     */
    suspend operator fun invoke(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val installedPackageNames = packageMonitor.getAllInstalledPackageNames()
                val appsInDb = installedAppsRepository.getAllInstalledApps().first()
                val now = System.currentTimeMillis()

                val toDelete = mutableListOf<String>()
                val toMigrate = mutableListOf<Pair<String, MigrationResult>>()
                val toResolvePending = mutableListOf<InstalledApp>()
                val toDeleteStalePending = mutableListOf<String>()
                val toSyncVersions = mutableListOf<InstalledApp>()

                appsInDb.forEach { app ->
                    val isOnSystem = installedPackageNames.contains(app.packageName)
                    when {
                        app.isPendingInstall -> {
                            if (isOnSystem) {
                                toResolvePending.add(app)
                            } else if (now - app.installedAt > PENDING_TIMEOUT_MS) {
                                toDeleteStalePending.add(app.packageName)
                            }
                        }

                        !isOnSystem -> {
                            toDelete.add(app.packageName)
                        }

                        app.installedVersionName == null -> {
                            val migrationResult = determineMigrationData(app)
                            toMigrate.add(app.packageName to migrationResult)
                        }

                        // Detect external version changes (downgrades on rooted devices, sideloads, etc.)
                        isOnSystem && platform == Platform.ANDROID -> {
                            toSyncVersions.add(app)
                        }
                    }
                }

                executeInTransaction {
                    toDelete.forEach { packageName ->
                        try {
                            installedAppsRepository.deleteInstalledApp(packageName)
                            logger.info("Removed uninstalled app: $packageName")
                        } catch (e: Exception) {
                            logger.error("Failed to delete $packageName: ${e.message}")
                        }
                    }

                    toDeleteStalePending.forEach { packageName ->
                        try {
                            installedAppsRepository.deleteInstalledApp(packageName)
                            logger.info("Removed stale pending install (>24h): $packageName")
                        } catch (e: Exception) {
                            logger.error("Failed to delete stale pending $packageName: ${e.message}")
                        }
                    }

                    toResolvePending.forEach { app ->
                        try {
                            val systemInfo = packageMonitor.getInstalledPackageInfo(app.packageName)
                            if (systemInfo != null) {
                                val latestVersionCode = app.latestVersionCode ?: 0L
                                installedAppsRepository.updateApp(
                                    app.copy(
                                        isPendingInstall = false,
                                        installedVersionName = systemInfo.versionName,
                                        installedVersionCode = systemInfo.versionCode,
                                        isUpdateAvailable = latestVersionCode > systemInfo.versionCode,
                                    ),
                                )
                                logger.info(
                                    "Resolved pending install: ${app.packageName} (v${systemInfo.versionName}, code=${systemInfo.versionCode})",
                                )
                            } else {
                                installedAppsRepository.updatePendingStatus(app.packageName, false)
                                logger.info("Resolved pending install (no system info): ${app.packageName}")
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to resolve pending ${app.packageName}: ${e.message}")
                        }
                    }

                    toMigrate.forEach { (packageName, migrationResult) ->
                        try {
                            val app = appsInDb.find { it.packageName == packageName } ?: return@forEach

                            installedAppsRepository.updateApp(
                                app.copy(
                                    installedVersionName = migrationResult.versionName,
                                    installedVersionCode = migrationResult.versionCode,
                                    latestVersionName = migrationResult.versionName,
                                    latestVersionCode = migrationResult.versionCode,
                                ),
                            )

                            logger.info(
                                "Migrated $packageName: ${migrationResult.source} " +
                                    "(versionName=${migrationResult.versionName}, code=${migrationResult.versionCode})",
                            )
                        } catch (e: Exception) {
                            logger.error("Failed to migrate $packageName: ${e.message}")
                        }
                    }

                    toSyncVersions.forEach { app ->
                        try {
                            val systemInfo = packageMonitor.getInstalledPackageInfo(app.packageName)
                            if (systemInfo != null && systemInfo.versionCode != app.installedVersionCode) {
                                val wasDowngrade = systemInfo.versionCode < app.installedVersionCode
                                val latestVersionCode = app.latestVersionCode ?: 0L
                                val isUpdateAvailable = latestVersionCode > systemInfo.versionCode

                                installedAppsRepository.updateApp(
                                    app.copy(
                                        installedVersionName = systemInfo.versionName,
                                        installedVersionCode = systemInfo.versionCode,
                                        installedVersion = systemInfo.versionName,
                                        isUpdateAvailable = isUpdateAvailable,
                                    ),
                                )

                                val action = if (wasDowngrade) "downgrade" else "external update"
                                logger.info(
                                    "Detected $action for ${app.packageName}: " +
                                        "DB v${app.installedVersionName}(${app.installedVersionCode}) → " +
                                        "System v${systemInfo.versionName}(${systemInfo.versionCode}), " +
                                        "updateAvailable=$isUpdateAvailable",
                                )
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to sync version for ${app.packageName}: ${e.message}")
                        }
                    }
                }

                logger.info(
                    "Sync completed: ${toDelete.size} deleted, ${toDeleteStalePending.size} stale pending removed, " +
                        "${toResolvePending.size} pending resolved, ${toMigrate.size} migrated, " +
                        "${toSyncVersions.size} version-checked",
                )

                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Sync failed: ${e.message}")
                Result.failure(e)
            }
        }

    private suspend fun determineMigrationData(app: InstalledApp): MigrationResult =
        if (platform == Platform.ANDROID) {
            val systemInfo = packageMonitor.getInstalledPackageInfo(app.packageName)
            if (systemInfo != null) {
                MigrationResult(
                    versionName = systemInfo.versionName,
                    versionCode = systemInfo.versionCode,
                    source = "system package manager",
                )
            } else {
                MigrationResult(
                    versionName = app.installedVersion,
                    versionCode = 0L,
                    source = "fallback to release tag",
                )
            }
        } else {
            MigrationResult(
                versionName = app.installedVersion,
                versionCode = 0L,
                source = "desktop fallback to release tag",
            )
        }

    private data class MigrationResult(
        val versionName: String,
        val versionCode: Long,
        val source: String,
    )
}

suspend fun executeInTransaction(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        throw e
    }
}
