package zed.rainxch.core.domain.model.installation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstalledAppUpdatesTest {


    private fun app(
        installedVersion: String = "1.0.0",
        latestVersion: String? = "2.0.0",
        latestVersionCode: Long? = 200L,
        isUpdateAvailable: Boolean = true,
        isPendingInstall: Boolean = false,
        pendingFilePath: String? = "/data/parked.apk",
    ): InstalledApp = InstalledApp(
        packageName = "com.example.app",
        repoId = 1L,
        repoName = "app",
        repoOwner = "owner",
        repoOwnerAvatarUrl = "https://avatar",
        repoDescription = null,
        primaryLanguage = "Kotlin",
        repoUrl = "https://github.com/owner/app",
        installedVersion = installedVersion,
        installedAssetName = "app-1.0.0.apk",
        installedAssetUrl = "https://dl/app-1.0.0.apk",
        latestVersion = latestVersion,
        latestAssetName = "app-2.0.0.apk",
        latestAssetUrl = "https://dl/app-2.0.0.apk",
        latestAssetSize = 1024L,
        appName = "App",
        installSource = InstallSource.THIS_APP,
        installedAt = 1000L,
        lastCheckedAt = 2000L,
        lastUpdatedAt = 1500L,
        isUpdateAvailable = isUpdateAvailable,
        signingFingerprint = "SHA",
        systemArchitecture = "arm64-v8a",
        fileExtension = "apk",
        isPendingInstall = isPendingInstall,
        installedVersionName = "1.0.0",
        installedVersionCode = 100L,
        latestVersionName = "2.0.0",
        latestVersionCode = latestVersionCode,
        latestReleasePublishedAt = "2026-08-01T00:00:00Z",
        pendingInstallFilePath = pendingFilePath,
        pendingInstallVersion = if (pendingFilePath != null) "2.0.0" else null,
        pendingInstallAssetName = if (pendingFilePath != null) "app-2.0.0.apk" else null,
    )


    @Test
    fun confirmInstallWritesInstallZone() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "app-2.0.0.apk",
            assetUrl = "https://dl/app-2.0.0.apk",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = "SHA-NEW",
            at = 9999L,
        )

        assertEquals("2.0.0", result.installedVersion)
        assertEquals("app-2.0.0.apk", result.installedAssetName)
        assertEquals("https://dl/app-2.0.0.apk", result.installedAssetUrl)
        assertEquals("2.0.0", result.installedVersionName)
        assertEquals(200L, result.installedVersionCode)
        assertEquals("SHA-NEW", result.signingFingerprint)
        assertEquals(9999L, result.lastUpdatedAt)
        assertEquals(9999L, result.lastCheckedAt)
        assertFalse(result.isPendingInstall)
    }

    @Test
    fun confirmInstallClearsUpdateFlagWhenLatestMatchesInstalled() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            at = 1L,
        )
        assertFalse(result.isUpdateAvailable)
        // snapshot reconciled to the installed code
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallKeepsUpdateFlagWhenSnapshotStillNewer() {
        val result = app(latestVersion = "3.0.0", latestVersionCode = 300L).confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            at = 1L,
        )
        assertTrue(result.isUpdateAvailable)
        assertEquals(300L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallClearsUpdateFlagWhenSnapshotMissing() {
        val result = app(latestVersion = null, latestVersionCode = null).confirmInstall(
            tag = "1.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "1.0.0",
            versionCode = 100L,
            signingFingerprint = null,
            at = 1L,
        )
        assertFalse(result.isUpdateAvailable)
        assertEquals(100L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallPendingHandoffKeepsParkedMetadata() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            isPending = true,
            at = 1L,
        )
        assertTrue(result.isPendingInstall)
        assertEquals("/data/parked.apk", result.pendingInstallFilePath)
        assertEquals("2.0.0", result.pendingInstallVersion)
    }

    @Test
    fun confirmInstallDoesNotTouchCheckZoneSnapshot() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            at = 1L,
        )
        // check zone fields other than the code reconciliation stay untouched
        assertEquals("2.0.0", result.latestVersion)
        assertEquals("app-2.0.0.apk", result.latestAssetName)
        assertEquals("2026-08-01T00:00:00Z", result.latestReleasePublishedAt)
        assertEquals("2.0.0", result.latestVersionName)
    }


    @Test
    fun resolvePendingFromSystemAdoptsTagAndClearsPending() {
        val result = app(isPendingInstall = true).resolvePendingFromSystem(
            resolvedTag = "2.0.0",
            versionName = "2.0.0",
            versionCode = 200L,
        )
        assertFalse(result.isPendingInstall)
        assertEquals("2.0.0", result.installedVersion)
        assertEquals("2.0.0", result.installedVersionName)
        assertEquals(200L, result.installedVersionCode)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun resolvePendingFromSystemKeepsUpdateFlagWhenSnapshotNewer() {
        val result = app(latestVersionCode = 300L).resolvePendingFromSystem(
            resolvedTag = "2.0.0",
            versionName = "2.0.0",
            versionCode = 200L,
        )
        assertTrue(result.isUpdateAvailable)
    }


    @Test
    fun observeExternalInstallNeverTouchesInstalledTag() {
        val result = app().observeExternalInstall(
            versionName = "1.2.0",
            versionCode = 120L,
        )
        assertEquals("1.0.0", result.installedVersion)
        assertEquals("1.2.0", result.installedVersionName)
        assertEquals(120L, result.installedVersionCode)
        // 200 > 120 → still an update available
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun observeExternalInstallDetectsDowngradeAsUpdateAvailable() {
        val result = app(latestVersionCode = 200L).observeExternalInstall(
            versionName = "0.9.0",
            versionCode = 50L,
        )
        assertTrue(result.isUpdateAvailable)
    }


    @Test
    fun markAndClearPendingTouchOnlyPendingFlag() {
        val marked = app().markPending()
        assertTrue(marked.isPendingInstall)
        assertEquals("/data/parked.apk", marked.pendingInstallFilePath)

        val cleared = app(isPendingInstall = true).clearPending()
        assertFalse(cleared.isPendingInstall)
    }


    @Test
    fun withLatestSnapshotWritesOnlyCheckZone() {
        val result = app().withLatestSnapshot(
            version = "nightly",
            assetName = "app-nightly.apk",
            assetUrl = "https://dl/app-nightly.apk",
            versionName = "26.09.01",
            versionCode = 999L,
        )
        assertEquals("nightly", result.latestVersion)
        assertEquals("app-nightly.apk", result.latestAssetName)
        assertEquals("https://dl/app-nightly.apk", result.latestAssetUrl)
        assertEquals("26.09.01", result.latestVersionName)
        assertEquals(999L, result.latestVersionCode)
        // install zone untouched
        assertEquals("1.0.0", result.installedVersion)
        assertEquals(100L, result.installedVersionCode)
        assertFalse(result.isPendingInstall)
    }


    @Test
    fun chainedPreInstallHandoffCombinesBothZones() {
        val result = app()
            .markPending()
            .withLatestSnapshot(
                version = "3.0.0",
                assetName = "a3",
                assetUrl = "u3",
                versionName = "3.0.0",
                versionCode = 300L,
            )
        assertTrue(result.isPendingInstall)
        assertEquals("3.0.0", result.latestVersion)
        assertEquals("1.0.0", result.installedVersion)
    }


    @Test
    fun migratedVersionInfoAlignsBothSides() {
        val result = app().withMigratedVersionInfo(
            versionName = "1.5.0",
            versionCode = 150L,
        )
        assertEquals("1.5.0", result.installedVersionName)
        assertEquals(150L, result.installedVersionCode)
        assertEquals("1.5.0", result.latestVersionName)
        assertEquals(150L, result.latestVersionCode)
        assertEquals("1.0.0", result.installedVersion)
        assertEquals("2.0.0", result.latestVersion)
    }


    @Test
    fun normalizeInstalledTagAlignsTagAndClearsFlag() {
        val result = app(installedVersion = "1.9.0").normalizeInstalledTag("2.0.0")
        assertEquals("2.0.0", result.installedVersion)
        assertFalse(result.isUpdateAvailable)
    }
}
