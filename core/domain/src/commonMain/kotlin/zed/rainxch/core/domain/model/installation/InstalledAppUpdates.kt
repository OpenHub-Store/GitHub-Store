package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath

// Zone-scoped write surface for InstalledApp. A bare copy() with dozens of
// named args let any writer overwrite fields owned by another writer (the
// overwrite-bug class); each function below copies ONLY its own zone, so
// cross-zone overwrites are impossible by construction. Zone ownership is
// pinned by InstalledAppUpdatesTest. One exception: withLatestSnapshot is a
// check-zone writer used to park the install target before a download.

// install zone — real install/confirm events only

// isUpdateAvailable recomputed against the stored latest snapshot; pending
// metadata cleared unless the install hands off to the system installer.
fun InstalledApp.confirmInstall(
    tag: String,
    assetName: String,
    assetUrl: String,
    versionName: String,
    versionCode: Long,
    signingFingerprint: String?,
    isPending: Boolean = false,
    at: Long,
): InstalledApp {
    val snapshotLatestVersion = latestVersion
    val isUpdateStillAvailable =
        !snapshotLatestVersion.isNullOrBlank() &&
                VersionMath.isVersionNewer(snapshotLatestVersion, tag)

    return copy(
        installedVersion = tag,
        installedAssetName = assetName,
        installedAssetUrl = assetUrl,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        isUpdateAvailable = isUpdateStillAvailable,
        latestVersionCode = if (isUpdateStillAvailable) latestVersionCode else versionCode,
        isPendingInstall = isPending,
        lastUpdatedAt = at,
        lastCheckedAt = at,
        signingFingerprint = signingFingerprint,
        pendingInstallFilePath = if (isPending) pendingInstallFilePath else null,
        pendingInstallVersion = if (isPending) pendingInstallVersion else null,
        pendingInstallAssetName = if (isPending) pendingInstallAssetName else null,
    )
}

fun InstalledApp.resolvePendingFromSystem(
    resolvedTag: String,
    versionName: String?,
    versionCode: Long,
): InstalledApp {
    val latestCode = latestVersionCode ?: 0L
    return copy(
        isPendingInstall = false,
        installedVersion = resolvedTag,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        isUpdateAvailable = latestCode > versionCode,
    )
}

// only valid when the system confirms the installed code already matches
fun InstalledApp.normalizeInstalledTag(tag: String): InstalledApp = copy(
    installedVersion = tag,
    isUpdateAvailable = false,
)

// one-time import/migration normalization; the ONLY sanctioned dual-zone write
fun InstalledApp.withMigratedVersionInfo(
    versionName: String?,
    versionCode: Long,
): InstalledApp = copy(
    installedVersionName = versionName,
    installedVersionCode = versionCode,
    latestVersionName = versionName,
    latestVersionCode = versionCode,
)

// observe zone — system observations, never the installedVersion tag

fun InstalledApp.observeExternalInstall(
    versionName: String?,
    versionCode: Long,
): InstalledApp {
    val latestCode = latestVersionCode ?: 0L
    return copy(
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        isUpdateAvailable = latestCode > versionCode,
    )
}

// pending zone

fun InstalledApp.markPending(): InstalledApp = copy(isPendingInstall = true)

fun InstalledApp.clearPending(): InstalledApp = copy(isPendingInstall = false)

// check zone — install-target snapshot only

fun InstalledApp.withLatestSnapshot(
    version: String,
    assetName: String?,
    assetUrl: String?,
    versionName: String?,
    versionCode: Long?,
): InstalledApp = copy(
    latestVersion = version,
    latestAssetName = assetName,
    latestAssetUrl = assetUrl,
    latestVersionName = versionName,
    latestVersionCode = versionCode,
)
