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

        val (branch, reason, isUpdateAvailable) =
            when {
                // A release the user deliberately skipped must never be re-offered,
                // including an opaque nightly tag that CI re-publishes. It is cleared
                // again only by a strictly newer release (skipBecameStale).
                matchesSkipped ->
                    Triple("skipped", "matches_skipped_tag", false)
                usedTimestampLogic ->
                    Triple(
                        "timestamp",
                        timestampReason(
                            isUpdate = timestampWouldReport,
                            matchedPublishedAt = matchedPublishedAt,
                            storedPublishedAt = storedPublishedAt,
                            previousWasUpdateAvailable = wasUpdateAvailable,
                        ),
                        timestampWouldReport,
                    )
                codesAlreadyMatch ->
                    Triple("codes_match", "codes_already_match", false)
                !reconcilable ->
                    Triple("irreconcilable", "versions_not_reconcilable", false)
                else -> {
                    val newer =
                        VersionMath.isVersionNewer(
                            candidate = matchedTag,
                            current = installedTag,
                        )
                    Triple(
                        "semver",
                        if (newer) "semver_newer" else "semver_not_newer",
                        newer,
                    )
                }
            }

        return Result(
            branch = branch,
            reason = reason,
            isUpdateAvailable = isUpdateAvailable,
            usedTimestampLogic = usedTimestampLogic,
            opaqueMatched = opaqueMatched,
            sameTag = sameTag,
            reconcilable = reconcilable,
            codesAlreadyMatch = codesAlreadyMatch,
            skipBecameStale = skipBecameStale,
        )
    }

    // only legitimate when the installed APK's versionCode already equals the
    // matched release's versionCode (the package really is that build)
    fun mayRewriteInstalledTag(result: Result): Boolean = result.codesAlreadyMatch

    private fun timestampReason(
        isUpdate: Boolean,
        matchedPublishedAt: String?,
        storedPublishedAt: String?,
        previousWasUpdateAvailable: Boolean,
    ): String =
        when {
            isUpdate && storedPublishedAt == null -> "timestamp_first_scan_null_baseline"
            isUpdate && matchedPublishedAt != null &&
                storedPublishedAt != null &&
                matchedPublishedAt > storedPublishedAt -> "timestamp_newer"
            isUpdate && previousWasUpdateAvailable -> "timestamp_retained"
            matchedPublishedAt == null -> "timestamp_matched_published_at_null"
            storedPublishedAt != null && matchedPublishedAt <= storedPublishedAt ->
                "timestamp_not_newer"
            else -> "timestamp_false"
        }

    data class Result(
        val branch: String,
        val reason: String,
        val isUpdateAvailable: Boolean,
        val usedTimestampLogic: Boolean,
        val opaqueMatched: Boolean,
        val sameTag: Boolean,
        val reconcilable: Boolean,
        val codesAlreadyMatch: Boolean,
        val skipBecameStale: Boolean,
    )
}
