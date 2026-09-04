package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath

// Zone-scoped write surface for InstalledApp. A bare copy() with dozens of
// named args let any writer overwrite fields owned by another writer (the
// overwrite-bug class); each function below copies the fields of its declared
// zone, pinned by InstalledAppUpdatesTest. Two declared cross-side owners:
// the migrate zone (one-time import normalizer owning both sides' version
// name/code, never tags, flags, or assets) and confirmInstall reconciling
// latestVersionCode to the installed code when the install caught up to it.

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

    // Non-pending confirmations finish the install, so pending metadata goes
    // with them; a system-installer handoff keeps the parked file alive.
    val parkedFile = if (isPending) pendingInstallFilePath else null
    val parkedVersion = if (isPending) pendingInstallVersion else null
    val parkedAsset = if (isPending) pendingInstallAssetName else null

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
        pendingInstallFilePath = parkedFile,
        pendingInstallVersion = parkedVersion,
        pendingInstallAssetName = parkedAsset,
    )
}

fun InstalledApp.resolvePendingFromSystem(
    resolvedTag: String,
    versionName: String?,
    versionCode: Long,
): InstalledApp = copy(
    isPendingInstall = false,
    installedVersion = resolvedTag,
    installedVersionName = versionName,
    installedVersionCode = versionCode,
    isUpdateAvailable = updateFlagAgainstSnapshot(versionCode),
)

// an installed code below the stored snapshot means an update is still on
// the table; a null snapshot means nothing newer is known
private fun InstalledApp.updateFlagAgainstSnapshot(installedCode: Long): Boolean =
    (latestVersionCode ?: 0L) > installedCode

// only valid when the system confirms the installed code already matches
fun InstalledApp.normalizeInstalledTag(tag: String): InstalledApp = copy(
    installedVersion = tag,
    isUpdateAvailable = false,
)

// one-time import/migration normalization; owns both sides' version fields by design
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
): InstalledApp = copy(
    installedVersionName = versionName,
    installedVersionCode = versionCode,
    isUpdateAvailable = updateFlagAgainstSnapshot(versionCode),
)

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
