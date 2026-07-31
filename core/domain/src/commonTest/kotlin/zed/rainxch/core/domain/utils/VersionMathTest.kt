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
    }

    @Test
    fun normalize_extracts_digits_from_calver_nightly() {
        assertEquals("20260731", VersionMath.normalizeVersion("nightly-20260731"))
        assertEquals("20260801", VersionMath.normalizeVersion("nightly-20260801"))
    }

    @Test
    fun normalize_semver_unaffected() {
        assertEquals("1.2.3", VersionMath.normalizeVersion("1.2.3"))
        assertEquals("1.2.3-beta", VersionMath.normalizeVersion("v1.2.3-beta"))
        assertEquals("2.0.9.1", VersionMath.normalizeVersion("2.0.9.1"))
    }

    @Test
    fun opaque_marker_pair_detects_hash_suffixes() {
        assertTrue(VersionMath.isOpaqueMarkerPair("nightly-abc", "nightly-def"))
        assertTrue(VersionMath.isOpaqueMarkerPair("nightly", "nightly"))
        assertTrue(VersionMath.isOpaqueMarkerPair("canary-deadbeef", "canary-cafef00d"))
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
        assertFalse(VersionMath.isPreReleaseTag("v1.2.3"))
        assertFalse(VersionMath.isPreReleaseTag("1.2.3"))
    }

    @Test
    fun nightly_marker_label() {
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("nightly"))
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("nightly-abc"))
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("v1.2.3-nightly"))
    }

    @Test
    fun detect_scheme_for_nightly() {
        assertEquals(VersionMath.Scheme.Unknown, VersionMath.detectScheme("nightly"))
        assertEquals(VersionMath.Scheme.Unknown, VersionMath.detectScheme("nightly-abc"))
        assertEquals(VersionMath.Scheme.SemVer, VersionMath.detectScheme("v1.2.3-nightly"))
        assertEquals(VersionMath.Scheme.CalVer, VersionMath.detectScheme("2026-07-31"))
    }
}
