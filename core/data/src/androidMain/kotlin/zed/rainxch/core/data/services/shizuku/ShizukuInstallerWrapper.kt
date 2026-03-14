package zed.rainxch.core.data.services.shizuku

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.model.GithubAsset
import zed.rainxch.core.domain.model.InstallerType
import zed.rainxch.core.domain.model.SystemArchitecture
import zed.rainxch.core.domain.repository.ThemesRepository
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerInfoExtractor

/**
 * Wrapper around [Installer] that transparently intercepts `install()` and `uninstall()`
 * calls to use Shizuku when available and enabled by the user.
 *
 * All other methods (asset selection, architecture detection, Obtainium/AppManager support)
 * delegate directly to the underlying [androidInstaller] unchanged.
 *
 * Fallback behavior: if Shizuku install/uninstall fails for any reason, falls back
 * to the standard [androidInstaller] implementation silently.
 */
class ShizukuInstallerWrapper(
    private val androidInstaller: Installer,
    private val shizukuServiceManager: ShizukuServiceManager,
    private val themesRepository: ThemesRepository
) : Installer {

    companion object {
        private const val TAG = "ShizukuInstaller"
    }

    /**
     * Cached installer type preference, updated by flow collection.
     * Defaults to DEFAULT so Shizuku is never used unless explicitly opted in.
     */
    @Volatile
    private var cachedInstallerType: InstallerType = InstallerType.DEFAULT

    /**
     * Start observing the installer type preference from DataStore.
     * Call once after construction (from DI setup).
     */
    fun observeInstallerPreference(scope: CoroutineScope) {
        scope.launch {
            themesRepository.getInstallerType().collect { type ->
                cachedInstallerType = type
                Logger.d(TAG) { "Installer type changed to: $type" }
            }
        }
    }

    // ==================== Delegated methods (always go to AndroidInstaller) ====================

    override suspend fun isSupported(extOrMime: String): Boolean =
        androidInstaller.isSupported(extOrMime)

    override fun isAssetInstallable(assetName: String): Boolean =
        androidInstaller.isAssetInstallable(assetName)

    override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? =
        androidInstaller.choosePrimaryAsset(assets)

    override fun detectSystemArchitecture(): SystemArchitecture =
        androidInstaller.detectSystemArchitecture()

    override fun isObtainiumInstalled(): Boolean =
        androidInstaller.isObtainiumInstalled()

    override fun openInObtainium(
        repoOwner: String,
        repoName: String,
        onOpenInstaller: () -> Unit
    ) = androidInstaller.openInObtainium(repoOwner, repoName, onOpenInstaller)

    override fun isAppManagerInstalled(): Boolean =
        androidInstaller.isAppManagerInstalled()

    override fun openInAppManager(
        filePath: String,
        onOpenInstaller: () -> Unit
    ) = androidInstaller.openInAppManager(filePath, onOpenInstaller)

    override fun getApkInfoExtractor(): InstallerInfoExtractor =
        androidInstaller.getApkInfoExtractor()

    override fun openApp(packageName: String): Boolean =
        androidInstaller.openApp(packageName)

    override fun openWithExternalInstaller(filePath: String) =
        androidInstaller.openWithExternalInstaller(filePath)

    // ==================== Overridden methods (may use Shizuku) ====================

    override suspend fun ensurePermissionsOrThrow(extOrMime: String) {
        Logger.d(TAG) { "ensurePermissionsOrThrow() — extOrMime=$extOrMime, cachedType=$cachedInstallerType, status=${shizukuServiceManager.status.value}" }
        if (shouldUseShizuku()) {
            Logger.d(TAG) { "Shizuku active — skipping unknown sources permission check" }
            return
        }
        Logger.d(TAG) { "Delegating ensurePermissionsOrThrow to AndroidInstaller" }
        androidInstaller.ensurePermissionsOrThrow(extOrMime)
    }

    override suspend fun install(filePath: String, extOrMime: String) {
        Logger.d(TAG) { "install() called — filePath=$filePath, extOrMime=$extOrMime" }
        Logger.d(TAG) { "cachedInstallerType=$cachedInstallerType, shizukuStatus=${shizukuServiceManager.status.value}" }

        if (shouldUseShizuku()) {
            Logger.d(TAG) { "Shizuku is enabled and READY — attempting Shizuku install" }
            try {
                val service = shizukuServiceManager.getService()
                if (service != null) {
                    // Run the blocking AIDL call on IO dispatcher to avoid ANR
                    val result = withContext(Dispatchers.IO) {
                        val file = java.io.File(filePath)
                        val pfd = android.os.ParcelFileDescriptor.open(
                            file,
                            android.os.ParcelFileDescriptor.MODE_READ_ONLY
                        )
                        pfd.use {
                            Logger.d(TAG) { "Got Shizuku service, calling installPackage($filePath, size=${file.length()})..." }
                            service.installPackage(it, file.length())
                        }
                    }
                    Logger.d(TAG) { "Shizuku installPackage() returned: $result" }
                    if (result == 0) {
                        Logger.d(TAG) { "Shizuku install SUCCEEDED for: $filePath" }
                        return
                    }
                    Logger.w(TAG) { "Shizuku install FAILED with code: $result, falling back to standard installer" }
                } else {
                    Logger.w(TAG) { "Shizuku service is NULL, falling back to standard installer" }
                }
            } catch (e: Exception) {
                Logger.e(TAG) { "Shizuku install exception, falling back: ${e.javaClass.simpleName}: ${e.message}" }
            }
        } else {
            Logger.d(TAG) { "Not using Shizuku (enabled=${isShizukuEnabled()}, status=${shizukuServiceManager.status.value})" }
        }

        // Fallback: ensure permissions then use standard installer
        Logger.d(TAG) { "Using standard AndroidInstaller for: $filePath" }
        androidInstaller.ensurePermissionsOrThrow(extOrMime)
        androidInstaller.install(filePath, extOrMime)
    }

    override fun uninstall(packageName: String) {
        Logger.d(TAG) { "uninstall() called — packageName=$packageName" }
        Logger.d(TAG) { "cachedInstallerType=$cachedInstallerType, shizukuStatus=${shizukuServiceManager.status.value}" }

        if (isShizukuEnabled() && shizukuServiceManager.status.value == ShizukuStatus.READY) {
            Logger.d(TAG) { "Attempting Shizuku uninstall..." }
            // Fire on background thread — callers don't await result for standard uninstall either
            Thread {
                try {
                    // Bind/get service on this background thread (getService is suspend)
                    val service = runBlocking { shizukuServiceManager.getService() }
                    if (service != null) {
                        Logger.d(TAG) { "Got service, calling uninstallPackage($packageName)..." }
                        val result = service.uninstallPackage(packageName)
                        Logger.d(TAG) { "Shizuku uninstallPackage() returned: $result" }
                        if (result == 0) {
                            Logger.d(TAG) { "Shizuku uninstall SUCCEEDED for: $packageName" }
                        } else {
                            Logger.w(TAG) { "Shizuku uninstall FAILED with code: $result, falling back" }
                            androidInstaller.uninstall(packageName)
                        }
                    } else {
                        Logger.w(TAG) { "Shizuku service is NULL, falling back" }
                        androidInstaller.uninstall(packageName)
                    }
                } catch (e: Exception) {
                    Logger.e(TAG) { "Shizuku uninstall exception, falling back: ${e.message}" }
                    androidInstaller.uninstall(packageName)
                }
            }.start()
            return
        }

        Logger.d(TAG) { "Using standard AndroidInstaller uninstall for: $packageName" }
        androidInstaller.uninstall(packageName)
    }

    // ==================== Internal helpers ====================

    private suspend fun shouldUseShizuku(): Boolean {
        return isShizukuEnabled() && shizukuServiceManager.status.value == ShizukuStatus.READY
    }

    private fun isShizukuEnabled(): Boolean {
        return cachedInstallerType == InstallerType.SHIZUKU
    }
}
