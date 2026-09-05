package zed.rainxch.core.domain.use_cases

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.model.installation.observeExternalInstall
import zed.rainxch.core.domain.model.installation.resolvePendingFromSystem
import zed.rainxch.core.domain.model.installation.withMigratedVersionInfo
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.PackageMonitor

class SyncInstalledAppsUseCase(
    private val packageMonitor: PackageMonitor,
    private val installedAppsRepository: InstalledAppsRepository,
    private val platform: Platform,
    private val logger: KomiStoreLogger,
) {
    companion object {
        private const val PENDING_TIMEOUT_MS = 24 * 60 * 60 * 1000L

        private const val MIN_PLAUSIBLE_SCAN_SIZE = 20
    }

    suspend operator fun invoke(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val appsInDb = installedAppsRepository.getAllInstalledApps().first()
                if (appsInDb.isEmpty()) return@withContext Result.success(Unit)

                if (packageMonitor.canEnumerateInstalledPackages()) {
                    reconcileEnumerable(appsInDb)
                } else {
                    resolveUntrackablePending(appsInDb)
                }

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Sync failed: ${e.message}")
                Result.failure(e)
            }
        }

    private suspend fun resolveUntrackablePending(appsInDb: List<InstalledApp>) {
        val orphanPending =
            appsInDb.filter { it.isPendingInstall && it.pendingInstallFilePath == null }
        if (orphanPending.isEmpty()) {
            logger.info("Sync (no enumeration): nothing to resolve, 0 deleted")
            return
        }
        installedAppsRepository.executeInTransaction {
            orphanPending.forEach { app ->
                try {
                    installedAppsRepository.updatePendingStatus(app.packageName, false)
                    logger.info("Resolved un-confirmable pending install: ${app.packageName}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Failed to resolve pending ${app.packageName}: ${e.message}")
                }
            }
        }
        logger.info("Sync (no enumeration): ${orphanPending.size} pending resolved, 0 deleted")
    }

    private suspend fun reconcileEnumerable(appsInDb: List<InstalledApp>) {
        val installedPackageNames = packageMonitor.getAllInstalledPackageNames()
        val now = System.currentTimeMillis()

        val deleteCandidates = mutableListOf<String>()
        val staleCandidates = mutableListOf<InstalledApp>()
        val toResolvePending = mutableListOf<InstalledApp>()
        val toMigrate = mutableListOf<Pair<String, MigrationResult>>()
        val toSyncVersions = mutableListOf<InstalledApp>()
        val toClearStaleParkedFile = mutableListOf<InstalledApp>()

        appsInDb.forEach { app ->
            val isOnSystem = installedPackageNames.contains(app.packageName)
            when {
                app.isPendingInstall -> {
                    if (isOnSystem) {
                        toResolvePending.add(app)
                    } else if (now - app.installedAt > PENDING_TIMEOUT_MS) {
                        staleCandidates.add(app)
                    }
                }

                !isOnSystem -> deleteCandidates.add(app.packageName)

                app.installedVersionName == null ->
                    toMigrate.add(app.packageName to determineMigrationData(app))

                platform == Platform.ANDROID -> toSyncVersions.add(app)
            }
            if (isOnSystem && !app.isPendingInstall && app.pendingInstallFilePath != null) {
                toClearStaleParkedFile.add(app)
            }
        }

        guardUntrustworthyScan(
            appsInDb = appsInDb,
            installedPackageNames = installedPackageNames,
            deleteCandidates = deleteCandidates,
            staleCandidates = staleCandidates
        )

        val confirmedDeletes =
            deleteCandidates.filter { pkg ->
                val absent = presenceOf(pkg) is Presence.Absent
                if (!absent) {
                    logger.info("Kept $pkg: not positively absent (installed, hidden, or lookup failed)")
                }
                absent
            }
        val confirmedStaleDeletes = mutableListOf<String>()
        staleCandidates.forEach { app ->
            when (presenceOf(app.packageName)) {
                is Presence.Absent -> confirmedStaleDeletes.add(app.packageName)
                is Presence.Present -> toResolvePending.add(app)
                is Presence.Unknown -> Unit
            }
        }

        val systemInfoByPackage =
            (toResolvePending.map { it.packageName } + toSyncVersions.map { it.packageName })
                .toSet()
                .associateWith { (presenceOf(it) as? Presence.Present)?.info }

        installedAppsRepository.executeInTransaction {
            confirmedDeletes.forEach { deleteTracked(it, "uninstalled app") }
            confirmedStaleDeletes.forEach { deleteTracked(it, "stale pending install (>24h)") }
            toResolvePending.forEach { resolvePending(it, systemInfoByPackage[it.packageName]) }
            toClearStaleParkedFile.forEach { clearParkedFile(it) }
            toMigrate.forEach { (packageName, result) -> migrate(appsInDb, packageName, result) }
            toSyncVersions.forEach { syncVersion(it, systemInfoByPackage[it.packageName]) }
        }

        logger.info(
            "Sync completed: ${confirmedDeletes.size} deleted, " +
                "${confirmedStaleDeletes.size} stale pending removed, " +
                "${toResolvePending.size} pending resolved, ${toMigrate.size} migrated, " +
                "${toSyncVersions.size} version-checked, " +
                "${toClearStaleParkedFile.size} stale parked files cleared",
        )
    }

    private fun guardUntrustworthyScan(
        appsInDb: List<InstalledApp>,
        installedPackageNames: Set<String>,
        deleteCandidates: MutableList<String>,
        staleCandidates: MutableList<InstalledApp>,
    ) {
        val nonPendingCount = appsInDb.count { !it.isPendingInstall }
        val recognizedCount =
            appsInDb.count { !it.isPendingInstall && installedPackageNames.contains(it.packageName) }

        val scanUntrustworthy =
            installedPackageNames.size < MIN_PLAUSIBLE_SCAN_SIZE ||
                (nonPendingCount >= 1 && recognizedCount * 2 < nonPendingCount)
        if (scanUntrustworthy) {
            logger.error(
                "Installed-apps sync: package scan untrustworthy (recognised $recognizedCount of " +
                    "$nonPendingCount tracked, scanSize=${installedPackageNames.size}). Skipping " +
                    "${deleteCandidates.size} deletions + ${staleCandidates.size} stale-pending " +
                    "removals to avoid wiping the library (GH#748).",
            )
            deleteCandidates.clear()
            staleCandidates.clear()
        }
    }

    private suspend fun deleteTracked(packageName: String, reason: String) {
        try {
            installedAppsRepository.deleteInstalledApp(packageName)
            logger.info("Removed $reason: $packageName")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to delete $packageName: ${e.message}")
        }
    }

    private suspend fun resolvePending(app: InstalledApp, systemInfo: SystemPackageInfo?) {
        try {
            if (systemInfo != null) {
                val resolvedTag = app.latestVersion ?: systemInfo.versionName
                installedAppsRepository.updateApp(
                    app.resolvePendingFromSystem(
                        resolvedTag = resolvedTag,
                        versionName = systemInfo.versionName,
                        versionCode = systemInfo.versionCode,
                    ),
                )
                logger.info(
                    "Resolved pending install: ${app.packageName} " +
                        "(v${systemInfo.versionName}, code=${systemInfo.versionCode}, tag=$resolvedTag)",
                )
            } else {
                installedAppsRepository.updatePendingStatus(app.packageName, false)
                logger.info("Resolved pending install (no system info): ${app.packageName}")
            }
            installedAppsRepository.setPendingInstallFilePath(app.packageName, path = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to resolve pending ${app.packageName}: ${e.message}")
        }
    }

    private suspend fun clearParkedFile(app: InstalledApp) {
        try {
            installedAppsRepository.setPendingInstallFilePath(app.packageName, path = null)
            logger.info(
                "Cleared stale parked-file metadata for already-installed ${app.packageName}",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to clear stale parked-file for ${app.packageName}: ${e.message}")
        }
    }

    private suspend fun migrate(
        appsInDb: List<InstalledApp>,
        packageName: String,
        migrationResult: MigrationResult,
    ) {
        try {
            val app = appsInDb.find { it.packageName == packageName } ?: return
            installedAppsRepository.updateApp(
                app.withMigratedVersionInfo(
                    versionName = migrationResult.versionName,
                    versionCode = migrationResult.versionCode,
                ),
            )
            logger.info(
                "Migrated $packageName: ${migrationResult.source} " +
                    "(versionName=${migrationResult.versionName}, code=${migrationResult.versionCode})",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to migrate $packageName: ${e.message}")
        }
    }

    private suspend fun syncVersion(app: InstalledApp, systemInfo: SystemPackageInfo?) {
        try {
            // installedVersion is the GitHub release tag and is owned by install
            // events only. External install sync must never touch it — it only
            // refreshes the versionName/versionCode observed from the system.
            if (systemInfo != null && systemInfo.versionCode != app.installedVersionCode) {
                val wasDowngrade = systemInfo.versionCode < app.installedVersionCode

                val observed = app.observeExternalInstall(
                    versionName = systemInfo.versionName,
                    versionCode = systemInfo.versionCode,
                )
                installedAppsRepository.updateApp(observed)

                val action = if (wasDowngrade) "downgrade" else "external update"
                logger.info(
                    "Detected $action for ${app.packageName}: " +
                        "DB v${app.installedVersionName}(${app.installedVersionCode}) → " +
                        "System v${systemInfo.versionName}(${systemInfo.versionCode}), " +
                        "updateAvailable=${observed.isUpdateAvailable}",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to sync version for ${app.packageName}: ${e.message}")
        }
    }

    private suspend fun presenceOf(packageName: String): Presence =
        runCatching { packageMonitor.getInstalledPackageInfo(packageName) }
            .fold(
                onSuccess = { info -> if (info == null) Presence.Absent else Presence.Present(info) },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    logger.warn("Package lookup failed for $packageName; treated as unknown: ${e.message}")
                    Presence.Unknown
                },
            )

    private sealed interface Presence {
        data object Absent : Presence

        data class Present(val info: SystemPackageInfo) : Presence

        data object Unknown : Presence
    }

    private suspend fun determineMigrationData(app: InstalledApp): MigrationResult =
        if (platform == Platform.ANDROID) {
            val systemInfo = (presenceOf(app.packageName) as? Presence.Present)?.info
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
