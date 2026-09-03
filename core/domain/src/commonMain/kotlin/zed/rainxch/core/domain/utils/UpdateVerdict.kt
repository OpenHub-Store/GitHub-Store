package zed.rainxch.core.domain.utils

// Pure update-decision logic — no repository, no DAO, no IO. Branch order:
// skipped tag wins over everything, then timestamp, then code equality,
// then reconcilability, then semver. Every branch is pinned by
// UpdateVerdictTest.
object UpdateVerdict {

    fun decide(
        installedTag: String?,
        installedVersionCode: Long,
        storedLatestTag: String?,
        storedLatestVersionCode: Long?,
        storedPublishedAt: String?,
        wasUpdateAvailable: Boolean,
        skippedTag: String?,
        matchedTag: String,
        matchedPublishedAt: String?,
        matchedIsPrerelease: Boolean,
    ): Result {
        val reconcilable = VersionMath.versionsReconcilable(installedTag, matchedTag)
        val codesAlreadyMatch =
            installedVersionCode > 0L &&
                storedLatestVersionCode != null &&
                storedLatestVersionCode > 0L &&
                installedVersionCode == storedLatestVersionCode &&
                matchedTag == storedLatestTag

        val matchesSkipped =
            skippedTag != null && VersionMath.isExactSameVersion(matchedTag, skippedTag)
        val skipBecameStale =
            skippedTag != null &&
                !matchesSkipped &&
                VersionMath.isVersionNewer(matchedTag, skippedTag)

        val opaqueMatched = VersionMath.isOpaqueMarker(matchedTag)
        val sameTag = VersionMath.isExactSameVersion(matchedTag, installedTag)
        val usedTimestampLogic =
            opaqueMatched ||
                (sameTag && !reconcilable) ||
                (!reconcilable && (matchedIsPrerelease || VersionMath.isPreReleaseTag(matchedTag)))

        val timestampWouldReport =
            if (usedTimestampLogic) {
                VersionMath.shouldReportTimestampUpdate(
                    matchedTag = matchedTag,
                    matchedPublishedAt = matchedPublishedAt,
                    previousLatestPublishedAt = storedPublishedAt,
                    previousWasUpdateAvailable = wasUpdateAvailable,
                    previousLatestTag = storedLatestTag,
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
                        candidate = matchedTag,
                        current = installedTag,
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
