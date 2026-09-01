package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath

/**
 * Zone-scoped update functions for [InstalledApp].
 *
 * InstalledApp mixes several concerns in one flat data class. A bare
 * `copy(...)` with dozens of named arguments lets any writer overwrite fields
 * owned by another writer — the concurrency amplifier behind the overwrite-class
 * bugs in this project's history. These functions partition the WRITE surface:
 * each one copies ONLY the fields of its own zone, so cross-zone overwrites are
 * impossible by construction. Read sites are unaffected (the data class stays
 * flat; the Room schema stays at v18).
 *
 * Zones & single-writer ownership:
 *  - **install** — `installedVersion` (GitHub tag), installed asset identity,
 *    versionName/Code, signingFingerprint, lastUpdatedAt/lastCheckedAt.
 *    Written only by real install/confirm events.
 *  - **observe** — installedVersionName/Code + recomputed isUpdateAvailable,
 *    as seen by the system package manager. Must NEVER touch the
 *    `installedVersion` tag (tag is owned by install events only).
 *  - **check** — latestVersion*, releaseNotes, latestReleasePublishedAt,
 *    skippedReleaseTag. Written by the update-check path via targeted DAO
 *    updates (updateVersionInfo / setSkippedReleaseTag / ...), never through
 *    full-row copies. The one exception is [withLatestSnapshot], used to park
 *    the install target right before a download-triggered install.
 *  - **pending** — isPendingInstall, pendingInstall* fields.
 *  - **config** — updateCheckEnabled, includePreReleases, assetFilterRegex,
 *    fallbackToOlderReleases, preferred* fields. Written via targeted DAO
 *    updates; no function needed here.
 */

// ─────────────────────────────────────────────────────────────────────────────
// install zone — real install/confirm events only
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Records a completed install of [tag].
 *
 * Recomputes [InstalledApp.isUpdateAvailable] against the stored latest
 * snapshot: if the snapshot is still newer than what we just installed the
 * flag stays true (and [InstalledApp.latestVersionCode] is preserved);
 * otherwise the snapshot is reconciled to the installed code.
 *
 * Pending metadata is cleared unless [isPending] is true (install handoff to
 * the system installer keeps the parked file info until the broadcast lands).
 */
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

/**
 * Resolves a pending install from a system package observation: the parked
 * version is now physically installed. Adopts [resolvedTag] as the installed
 * tag, refreshes versionName/Code from the system and recomputes
 * [InstalledApp.isUpdateAvailable] from version codes.
 */
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

/**
 * Normalizes a stale self-installed tag to the tracked [tag] (e.g. the store
 * app updated itself outside of a tracked install event). Only valid when the
 * system confirms the installed code already matches.
 */
fun InstalledApp.normalizeInstalledTag(tag: String): InstalledApp = copy(
    installedVersion = tag,
    isUpdateAvailable = false,
)

/**
 * One-time import/migration normalization: aligns versionName/Code on both the
 * installed and latest sides. The ONLY sanctioned writer that touches two
 * zones at once — runtime paths must never use this.
 */
fun InstalledApp.withMigratedVersionInfo(
    versionName: String?,
    versionCode: Long,
): InstalledApp = copy(
    installedVersionName = versionName,
    installedVersionCode = versionCode,
    latestVersionName = versionName,
    latestVersionCode = versionCode,
)

// ─────────────────────────────────────────────────────────────────────────────
// observe zone — system observations, never the installedVersion tag
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Refreshes versionName/Code observed from the system (external update or
 * downgrade done outside this app) and recomputes
 * [InstalledApp.isUpdateAvailable] from version codes. The installedVersion
 * tag is intentionally NOT touched — it is owned by install events only.
 */
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

// ─────────────────────────────────────────────────────────────────────────────
// pending zone
// ─────────────────────────────────────────────────────────────────────────────

fun InstalledApp.markPending(): InstalledApp = copy(isPendingInstall = true)

fun InstalledApp.clearPending(): InstalledApp = copy(isPendingInstall = false)

// ─────────────────────────────────────────────────────────────────────────────
// check zone — install-target snapshot only (see zone docs above)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parks the checked latest release as the install target, right before a
 * download-triggered install hands off to the system installer. Chain with
 * [markPending] for the full pre-install handoff.
 */
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
