package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionMathTest {

    @Test
    fun normalize_preserves_opaque_marker_tags() {
        assertEquals("nightly-a1b2c3d", VersionMath.normalizeVersion("nightly-a1b2c3d"))
        assertEquals("canary-deadbeef", VersionMath.normalizeVersion("canary-deadbeef"))
        assertEquals("nightly-abc123", VersionMath.normalizeVersion("vnightly-abc123"))
        assertEquals("nightly", VersionMath.normalizeVersion("nightly"))
        assertEquals("beta-x7z92", VersionMath.normalizeVersion("beta-x7z92"))
        assertEquals("rolling-abc123", VersionMath.normalizeVersion("rolling-abc123"))
        assertEquals("rolling", VersionMath.normalizeVersion("rolling"))
    }

    @Test
    fun normalize_extracts_digits_from_calver_nightly() {
        assertEquals("20260731", VersionMath.normalizeVersion("nightly-20260731"))
        assertEquals("20260801", VersionMath.normalizeVersion("nightly-20260801"))
        assertEquals("20260801", VersionMath.normalizeVersion("rolling-20260801"))
    }

    @Test
    fun normalize_semver_unaffected() {
        assertEquals("1.2.3", VersionMath.normalizeVersion("1.2.3"))
        assertEquals("1.2.3-beta", VersionMath.normalizeVersion("v1.2.3-beta"))
        assertEquals("2.0.9.1", VersionMath.normalizeVersion("2.0.9.1"))
    }

    @Test
    fun opaque_marker_detects_release_tag_alone() {
        assertTrue(VersionMath.isOpaqueMarker("nightly"))
        assertTrue(VersionMath.isOpaqueMarker("nightly-abc"))
        assertTrue(VersionMath.isOpaqueMarker("rolling"))
        assertFalse(VersionMath.isOpaqueMarker("1.0.0"))
        assertFalse(VersionMath.isOpaqueMarker("nightly-20260731"))
    }

    @Test
    fun opaque_marker_pair_detects_hash_suffixes() {
        assertTrue(VersionMath.isOpaqueMarkerPair("nightly-abc", "nightly-def"))
        assertTrue(VersionMath.isOpaqueMarkerPair("nightly", "nightly"))
        assertTrue(VersionMath.isOpaqueMarkerPair("canary-deadbeef", "canary-cafef00d"))
        assertTrue(VersionMath.isOpaqueMarkerPair("rolling-a", "rolling-b"))
    }

    @Test
    fun opaque_marker_pair_rejects_numeric_suffixes() {
        assertFalse(VersionMath.isOpaqueMarkerPair("nightly-abc", "nightly-20260731"))
        assertFalse(VersionMath.isOpaqueMarkerPair("nightly-20260731", "nightly-20260801"))
    }

    @Test
    fun opaque_marker_pair_rejects_semver() {
        assertFalse(VersionMath.isOpaqueMarkerPair("nightly-abc", "1.2.3"))
        assertFalse(VersionMath.isOpaqueMarkerPair("1.2.3", "1.2.4"))
        assertFalse(VersionMath.isOpaqueMarkerPair("1.2.3-beta", "1.2.3-rc1"))
    }

    @Test
    fun versions_reconcilable_semver() {
        assertTrue(VersionMath.versionsReconcilable("1.2.3", "1.2.4"))
        assertTrue(VersionMath.versionsReconcilable("v1.2.3", "1.2.3"))
    }

    @Test
    fun versions_reconcilable_rejects_hash_mismatch() {
        assertFalse(VersionMath.versionsReconcilable("2.0.9.1", "2.0.9-1c19925b5"))
        assertFalse(VersionMath.versionsReconcilable("nightly-abc", "1.2.3"))
    }

    @Test
    fun calver_nightly_compares_numerically() {
        assertTrue(VersionMath.isVersionNewer("nightly-20260801", "nightly-20260731"))
        assertFalse(VersionMath.isVersionNewer("nightly-20260731", "nightly-20260801"))
    }

    @Test
    fun semver_comparison_regression() {
        assertTrue(VersionMath.isVersionNewer("1.2.4", "1.2.3"))
        assertFalse(VersionMath.isVersionNewer("1.2.3", "1.2.4"))
        assertTrue(VersionMath.isVersionNewer("2.0.0", "1.9.9"))
        assertFalse(VersionMath.isVersionNewer("1.0.0-alpha", "1.0.0"))
    }

    @Test
    fun nightly_is_prerelease_tag() {
        assertTrue(VersionMath.isPreReleaseTag("nightly"))
        assertTrue(VersionMath.isPreReleaseTag("nightly-abc"))
        assertTrue(VersionMath.isPreReleaseTag("nightly-20260731"))
        assertTrue(VersionMath.isPreReleaseTag("rolling"))
        assertTrue(VersionMath.isPreReleaseTag("rolling-abc"))
        assertFalse(VersionMath.isPreReleaseTag("v1.2.3"))
        assertFalse(VersionMath.isPreReleaseTag("1.2.3"))
    }

    @Test
    fun nightly_marker_label() {
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("nightly"))
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("nightly-abc"))
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("v1.2.3-nightly"))
        assertEquals("Rolling", VersionMath.preReleaseMarkerLabel("rolling"))
        assertEquals("Rolling", VersionMath.preReleaseMarkerLabel("rolling-abc"))
    }

    @Test
    fun detect_scheme_for_nightly() {
        assertEquals(VersionMath.Scheme.Unknown, VersionMath.detectScheme("nightly"))
        assertEquals(VersionMath.Scheme.Unknown, VersionMath.detectScheme("nightly-abc"))
        assertEquals(VersionMath.Scheme.SemVer, VersionMath.detectScheme("v1.2.3-nightly"))
        assertEquals(VersionMath.Scheme.CalVer, VersionMath.detectScheme("2026-07-31"))
    }

    @Test
    fun timestamp_update_retained_across_scans_without_install() {
        // Regression: an opaque-marker (nightly) update detected on one scan must stay
        // surfaced on the next scan if the user has not installed it yet.
        //
        // Scan 1 detects nightly-abc -> nightly-def (publishedAt T1) and stores
        // latestVersion=nightly-def, latestReleasePublishedAt=T1, isUpdateAvailable=true.
        //
        // Scan 2, no install in between: the matched release is still nightly-def (T1),
        // so the timestamp baseline no longer advances (T1 is not > T1). Without the
        // retention rule the update would silently disappear.
        val stillAvailable =
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly-def",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                previousLatestPublishedAt = "2026-08-01T00:00:00Z",
                previousWasUpdateAvailable = true,
                previousLatestTag = "nightly-def",
            )
        assertTrue(stillAvailable)
    }

    @Test
    fun timestamp_update_reports_newer_release() {
        // A genuinely newer opaque release (later publishedAt) is reported even when the
        // previous scan had not flagged an update yet.
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly-def",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                previousLatestPublishedAt = "2026-08-01T00:00:00Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly-abc",
            ),
        )
    }

    @Test
    fun timestamp_update_not_reported_after_install() {
        // Once the user installs (isUpdateAvailable cleared) and the baseline matches the
        // matched release, no stale update is reported.
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly-def",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                previousLatestPublishedAt = "2026-08-01T00:00:00Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly-def",
            ),
        )
    }

    @Test
    fun timestamp_update_first_scan_with_no_baseline() {
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                previousLatestPublishedAt = null,
                previousWasUpdateAvailable = false,
                previousLatestTag = null,
            ),
        )
    }
}
