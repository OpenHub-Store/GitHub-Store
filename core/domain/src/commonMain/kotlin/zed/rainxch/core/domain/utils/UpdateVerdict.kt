package zed.rainxch.core.domain.utils

// Pure update-decision logic — no repository, no DAO, no IO. Branch order:
// skipped tag wins over everything, then timestamp, then code equality,
// then reconcilability, then semver. Every branch is pinned by
// UpdateVerdictTest.
object UpdateVerdict {

    data class Installed(
        val tag: String?,
        val versionCode: Long,
    )

    data class Stored(
        val latestTag: String?,
        val latestVersionCode: Long?,
        val publishedAt: String?,
        val wasUpdateAvailable: Boolean,
    )

    data class Matched(
        val tag: String,
        val publishedAt: String?,
        val isPrerelease: Boolean,
    )

    fun decide(
        installed: Installed,
        stored: Stored,
        matched: Matched,
        skippedTag: String?,
    ): Result {
        val reconcilable = VersionMath.versionsReconcilable(installed.tag, matched.tag)
        val codesAlreadyMatch =
            installed.versionCode > 0L &&
                stored.latestVersionCode != null &&
                stored.latestVersionCode > 0L &&
                installed.versionCode == stored.latestVersionCode &&
                matched.tag == stored.latestTag

        val matchesSkipped =
            skippedTag != null && VersionMath.isExactSameVersion(matched.tag, skippedTag)
        val skipBecameStale =
            skippedTag != null &&
                !matchesSkipped &&
                VersionMath.isVersionNewer(matched.tag, skippedTag)

        val opaqueMatched = VersionMath.isOpaqueMarker(matched.tag)
        val sameTag = VersionMath.isExactSameVersion(matched.tag, installed.tag)
        val usedTimestampLogic =
            opaqueMatched ||
                (sameTag && !reconcilable) ||
                (!reconcilable && (matched.isPrerelease || VersionMath.isPreReleaseTag(matched.tag)))

        val timestampWouldReport =
            if (usedTimestampLogic) {
                VersionMath.shouldReportTimestampUpdate(
                    matchedTag = matched.tag,
                    matchedPublishedAt = matched.publishedAt,
                    previousLatestPublishedAt = stored.publishedAt,
                    previousWasUpdateAvailable = stored.wasUpdateAvailable,
                    previousLatestTag = stored.latestTag,
                )
            } else {
                false
            }

        val isUpdateAvailable =
            when {
                // A release the user deliberately skipped must never be re-offered,
                // including an opaque nightly tag that CI re-publishes. It is cleared
                // again only by a strictly newer release (skipBecameStale).
                matchesSkipped -> false
                usedTimestampLogic -> timestampWouldReport
                codesAlreadyMatch -> false
                !reconcilable -> false
                else ->
                    VersionMath.isVersionNewer(
                        candidate = matched.tag,
                        current = installed.tag,
                    )
            }

        return Result(
            isUpdateAvailable = isUpdateAvailable,
            skipBecameStale = skipBecameStale,
            codesAlreadyMatch = codesAlreadyMatch,
        )
    }

    data class Result(
        val isUpdateAvailable: Boolean,
        val skipBecameStale: Boolean,
        // true when the installed APK's versionCode already equals the matched
        // release's (the package really is that build) — the only case where
        // rewriting the installed tag is legitimate
        val codesAlreadyMatch: Boolean,
    )
}
