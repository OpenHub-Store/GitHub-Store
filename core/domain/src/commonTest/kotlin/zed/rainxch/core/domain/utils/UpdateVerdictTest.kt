package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateVerdictTest {


    private fun decide(
        installedTag: String = "1.0.0",
        installedVersionCode: Long = 100L,
        storedLatestTag: String? = null,
        storedLatestVersionCode: Long? = null,
        storedPublishedAt: String? = null,
        wasUpdateAvailable: Boolean = false,
        skippedTag: String? = null,
        matchedTag: String = "1.1.0",
        matchedPublishedAt: String? = "2026-08-01T00:00:00Z",
        matchedIsPrerelease: Boolean = false,
    ): UpdateVerdict.Result =
        UpdateVerdict.decide(
            installed = UpdateVerdict.Installed(installedTag, installedVersionCode),
            stored =
                UpdateVerdict.Stored(
                    latestTag = storedLatestTag,
                    latestVersionCode = storedLatestVersionCode,
                    publishedAt = storedPublishedAt,
                    wasUpdateAvailable = wasUpdateAvailable,
                ),
            matched = UpdateVerdict.Matched(matchedTag, matchedPublishedAt, matchedIsPrerelease),
            skippedTag = skippedTag,
        )


    @Test
    fun semver_newer_reports_update() {
        val result = decide(installedTag = "1.0.0", matchedTag = "1.1.0")
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun semver_not_newer_stays_silent() {
        val result = decide(installedTag = "1.2.0", matchedTag = "1.1.0")
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun beta_build_bump_reports_update() {
        // legado regression: 3.26.16-beta.20 > 3.26.16-beta.19
        val result =
            decide(
                installedTag = "3.26.16-beta.19",
                matchedTag = "3.26.16-beta.20",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }


    @Test
    fun nightly_tag_uses_timestamp_logic() {
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                storedPublishedAt = null,
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun nightly_newer_published_at_reports_update() {
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                storedLatestTag = "nightly",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun nightly_same_timestamp_retains_update_until_installed() {
        // Scan 1 flagged the update; scan 2 with no install in between must
        // keep it surfaced rather than silently dropping it.
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                storedLatestTag = "nightly",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                wasUpdateAvailable = true,
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun nightly_same_timestamp_no_baseline_change_stays_silent() {
        // After install (update flag cleared, same baseline), no stale report.
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                storedLatestTag = "nightly",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                wasUpdateAvailable = false,
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertFalse(result.isUpdateAvailable)
    }


    @Test
    fun installerx_unparseable_hash_prerelease_routes_to_timestamp() {
        val result =
            decide(
                installedTag = "26.08.21fae85",
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = null,
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun installerx_older_hash_still_detected_when_baseline_advances() {
        // 26.08.11f15e4 (code 1523) is NEWER than 26.08.21fae85 (code 1509)
        // despite the numeric prefix suggesting otherwise. publishedAt decides.
        val result =
            decide(
                installedTag = "26.08.21fae85",
                installedVersionCode = 1509L,
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }


    @Test
    fun codes_match_short_circuits_to_silent() {
        val result =
            decide(
                installedTag = "1.0.0",
                installedVersionCode = 100L,
                storedLatestTag = "1.0.0",
                storedLatestVersionCode = 100L,
                matchedTag = "1.0.0",
            )
        assertFalse(result.isUpdateAvailable)
        assertTrue(result.codesAlreadyMatch)
    }


    @Test
    fun skipped_nightly_stays_skipped_when_republished() {
        // A skipped opaque tag must not be re-offered just because CI recreated
        // the release with a newer publishedAt — the skip rule wins.
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_tag_is_silent() {
        val result = decide(skippedTag = "1.1.0", matchedTag = "1.1.0")
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_tag_becomes_stale_when_newer_release_appears() {
        val result = decide(skippedTag = "1.1.0", matchedTag = "1.2.0")
        assertTrue(result.skipBecameStale)
        assertTrue(result.isUpdateAvailable)
    }


    @Test
    fun stable_vs_nightly_is_irreconcilable_and_silent() {
        // installed is a nightly tag; matched release is a stable semver tag
        // and NOT flagged as prerelease → must NOT nag (no stable→nightly
        // fallback, and no bogus numeric comparison either).
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "2.0.0",
                matchedIsPrerelease = false,
            )
        assertFalse(result.isUpdateAvailable)
    }


    @Test
    fun rewrite_gate_allows_only_codes_already_match() {
        val matched = decide(installedTag = "1.0.0", installedVersionCode = 100L)
        assertFalse(matched.codesAlreadyMatch)

        val codesMatched =
            decide(
                installedTag = "1.0.0",
                installedVersionCode = 100L,
                storedLatestTag = "1.0.0",
                storedLatestVersionCode = 100L,
                matchedTag = "1.0.0",
            )
        assertTrue(codesMatched.codesAlreadyMatch)
    }
}
