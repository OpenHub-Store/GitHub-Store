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
    fun unparseable_hash_prereleases_are_not_reconcilable() {
        assertFalse(
            VersionMath.versionsReconcilable(
                "26.08.21fae85",
                "26.08.11f15e4",
            ),
        )
        assertFalse(
            VersionMath.versionsReconcilable(
                "26.08.ac0a687",
                "26.08.11f15e4",
            ),
        )
        // short hash tails (4-5 hex chars) must also opt out of numeric compare
        assertFalse(
            VersionMath.versionsReconcilable(
                "1.2.3fabc",
                "1.2.4",
            ),
        )
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
    fun timestamp_tracked_covers_opaque_and_hash_tails() {
        assertTrue(VersionMath.isTimestampTrackedTag("nightly"))
        assertTrue(VersionMath.isTimestampTrackedTag("rolling"))
        // InstallerX-style hash tail is not an opaque marker but is timestamp-tracked
        assertFalse(VersionMath.isOpaqueMarker("26.08.11f15e4"))
        assertTrue(VersionMath.isTimestampTrackedTag("26.08.11f15e4"))
        assertTrue(VersionMath.isTimestampTrackedTag("1.2.3fabc"))
        assertFalse(VersionMath.isTimestampTrackedTag("1.2.3"))
        assertFalse(VersionMath.isTimestampTrackedTag(null))
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
    fun beta_release_comparison_detects_newer_build() {
        assertTrue(VersionMath.isVersionNewer("3.26.16-beta.20", "3.26.16-beta.19"))
        assertFalse(VersionMath.isVersionNewer("3.26.16-beta.19", "3.26.16-beta.20"))
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
