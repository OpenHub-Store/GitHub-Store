package zed.rainxch.core.data.services.installer

import android.content.Context
import android.os.ParcelFileDescriptor
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zed.rainxch.core.data.services.dhizuku.DhizukuServiceManager
import zed.rainxch.core.data.services.dhizuku.model.DhizukuStatus
import zed.rainxch.core.data.services.root.RootServiceManager
import zed.rainxch.core.data.services.root.model.RootStatus
import zed.rainxch.core.data.services.shizuku.ShizukuServiceManager
import zed.rainxch.core.data.services.shizuku.model.ShizukuStatus
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.installation.InstallerAttribution
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerInfoExtractor
import java.io.File

class SilentInstallerDispatcher(
    private val androidContext: Context,
    private val androidInstaller: Installer,
    private val shizukuServiceManager: ShizukuServiceManager,
    private val dhizukuServiceManager: DhizukuServiceManager,
    private val rootServiceManager: RootServiceManager,
    private val tweaksRepository: TweaksRepository,
    private val scope: CoroutineScope,
) : Installer {
    companion object {
        private const val TAG = "SilentInstaller"
    }

    @Volatile
    private var cachedInstallerType: InstallerType = InstallerType.DEFAULT

    @Volatile
    private var cachedInstallerAttribution: InstallerAttribution = InstallerAttribution.SystemDefault

    fun observeInstallerPreference() {
        scope.launch {
            tweaksRepository.getInstallerType().collect { type ->
                cachedInstallerType = type
                Logger.d(TAG) { "Installer type changed to: $type" }
            }
        }
        scope.launch {
            tweaksRepository.getInstallerAttribution().collect { attribution ->
                cachedInstallerAttribution = attribution
                Logger.d(TAG) { "Installer attribution changed to: $attribution" }
            }
        }
    }

    override suspend fun isSupported(extOrMime: String): Boolean = androidInstaller.isSupported(extOrMime)

    override fun isAssetInstallable(assetName: String): Boolean = androidInstaller.isAssetInstallable(assetName)

    override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? = androidInstaller.choosePrimaryAsset(assets)

    override fun detectSystemArchitecture(): SystemArchitecture = androidInstaller.detectSystemArchitecture()

    override fun isObtainiumInstalled(): Boolean = androidInstaller.isObtainiumInstalled()

    override fun openInObtainium(
        repoOwner: String,
        repoName: String,
        onOpenInstaller: () -> Unit,
    ) = androidInstaller.openInObtainium(repoOwner, repoName, onOpenInstaller)

    override fun isAppManagerInstalled(): Boolean = androidInstaller.isAppManagerInstalled()

    override fun openInAppManager(
        filePath: String,
        onOpenInstaller: () -> Unit,
    ) = androidInstaller.openInAppManager(filePath, onOpenInstaller)

    override fun getApkInfoExtractor(): InstallerInfoExtractor = androidInstaller.getApkInfoExtractor()

    override fun openApp(packageName: String): Boolean = androidInstaller.openApp(packageName)

    override fun openWithExternalInstaller(filePath: String) = androidInstaller.openWithExternalInstaller(filePath)

    override suspend fun ensurePermissionsOrThrow(extOrMime: String) {
        Logger.d(TAG) {
            "ensurePermissionsOrThrow() — extOrMime=$extOrMime, cachedType=$cachedInstallerType"
        }
        if (resolveActiveBackend() != Backend.DEFAULT) {
            Logger.d(TAG) { "Silent backend active — skipping unknown sources permission check" }
            return
        }
        androidInstaller.ensurePermissionsOrThrow(extOrMime)
    }

    override suspend fun install(
        filePath: String,
        extOrMime: String,
    ): InstallOutcome {
        Logger.d(TAG) { "install() called — filePath=$filePath, extOrMime=$extOrMime, cached=$cachedInstallerType" }

        val backend = resolveActiveBackend()
        if (backend != Backend.DEFAULT) {
            val outcome = trySilentInstall(filePath, backend)
            if (outcome != null) return outcome
        }

        Logger.d(TAG) { "Falling back to standard AndroidInstaller for: $filePath" }
        androidInstaller.ensurePermissionsOrThrow(extOrMime)
        return androidInstaller.install(filePath, extOrMime)
    }

    override fun uninstall(packageName: String) {
        Logger.d(TAG) { "uninstall() called — packageName=$packageName, cached=$cachedInstallerType" }

        when (val backend = resolveActiveBackend()) {
            Backend.SHIZUKU, Backend.DHIZUKU, Backend.ROOT -> {
                scope.launch(Dispatchers.IO) {
                    silentUninstall(packageName, backend)
                }
            }
            Backend.DEFAULT -> {
                androidInstaller.uninstall(packageName)
            }
        }
    }

    private suspend fun trySilentInstall(filePath: String, backend: Backend): InstallOutcome? {
        Logger.d(TAG) { "Routing install through $backend" }
        val installerAttribution = cachedInstallerAttribution.resolvePackageName()
        val userId = PackageSessionInstaller.appUserId()
        val originatingUid = android.os.Process.myUid()
        return try {
            val file = File(filePath)
            val (expectedPkg, expectedVc) = PackageSessionInstaller.readApkIdentity(androidContext, filePath)
            val first =
                withContext(Dispatchers.IO) {
                    runInstall(
                        file = file,
                        backend = backend,
                        installerAttribution = installerAttribution,
                        expectedPkg = expectedPkg,
                        expectedVc = expectedVc,
                        userId = userId,
                        originatingUid = originatingUid,
                    )
                }

            val resolved =
                if (
                    backend == Backend.DHIZUKU &&
                    first == PackageSessionInstaller.STATUS_PENDING_USER_ACTION_REQUIRED &&
                    !installerAttribution.isNullOrBlank()
                ) {
                    Logger.w(TAG) {
                        "Dhizuku returned PENDING_USER_ACTION with attribution=$installerAttribution; retrying without attribution"
                    }
                    withContext(Dispatchers.IO) {
                        runInstall(
                            file = file,
                            backend = backend,
                            installerAttribution = null,
                            expectedPkg = expectedPkg,
                            expectedVc = expectedVc,
                            userId = userId,
                            originatingUid = originatingUid,
                        )
                    }
                } else {
                    first
                }
            when {
                resolved == null -> {
                    Logger.w(TAG) { "$backend service is null, will fall back" }
                    null
                }
                resolved == PackageSessionInstaller.STATUS_SUCCESS -> InstallOutcome.COMPLETED
                resolved == PackageSessionInstaller.STATUS_ABORTED -> {
                    throw IllegalStateException("Installation cancelled")
                }
                else -> {
                    Logger.w(TAG) { "$backend install returned $resolved, will fall back" }
                    null
                }
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG) { "$backend install exception, falling back: ${e.javaClass.simpleName}: ${e.message}" }
            null
        }
    }

    private suspend fun runInstall(
        file: File,
        backend: Backend,
        installerAttribution: String?,
        expectedPkg: String?,
        expectedVc: Long,
        userId: Int,
        originatingUid: Int,
    ): Int? =
        when (backend) {
            Backend.SHIZUKU ->
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    val service = shizukuServiceManager.getService() ?: return@use null
                    service.installPackage(
                        pfd,
                        file.length(),
                        expectedPkg,
                        installerAttribution,
                        userId,
                        originatingUid,
                    )
                }
            Backend.DHIZUKU ->
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    val service = dhizukuServiceManager.getService() ?: return@use null
                    service.installPackage(
                        pfd,
                        file.length(),
                        expectedPkg,
                        expectedVc,
                        installerAttribution,
                        userId,
                        originatingUid,
                    )
                }
            Backend.ROOT ->
                rootServiceManager.installPackage(
                    apkFile = file,
                    installerPackageName = installerAttribution,
                    userId = userId,
                )
            Backend.DEFAULT -> null
        }

    private suspend fun silentUninstall(packageName: String, backend: Backend) {
        val userId = PackageSessionInstaller.appUserId()
        try {
            val result = when (backend) {
                Backend.SHIZUKU -> {
                    val service = shizukuServiceManager.getService()
                    service?.uninstallPackage(packageName, userId)
                }
                Backend.DHIZUKU -> {
                    val service = dhizukuServiceManager.getService()
                    service?.uninstallPackage(packageName, userId)
                }
                Backend.ROOT -> rootServiceManager.uninstallPackage(packageName, userId)
                Backend.DEFAULT -> null
            }
            if (result == null || result != PackageSessionInstaller.STATUS_SUCCESS) {
                Logger.w(TAG) { "$backend uninstall failed (result=$result), falling back" }
                androidInstaller.uninstall(packageName)
            }
        } catch (e: Exception) {
            Logger.e(TAG) { "$backend uninstall exception, falling back: ${e.message}" }
            androidInstaller.uninstall(packageName)
        }
    }

    private fun resolveActiveBackend(): Backend = when (cachedInstallerType) {
        InstallerType.SHIZUKU -> {
            shizukuServiceManager.refreshStatus()
            if (shizukuServiceManager.status.value == ShizukuStatus.READY) Backend.SHIZUKU else Backend.DEFAULT
        }
        InstallerType.DHIZUKU -> {
            dhizukuServiceManager.refreshStatus()
            if (dhizukuServiceManager.status.value == DhizukuStatus.READY) Backend.DHIZUKU else Backend.DEFAULT
        }
        InstallerType.ROOT -> {
            if (rootServiceManager.status.value != RootStatus.READY) {
                rootServiceManager.refreshStatus()
            }
            if (rootServiceManager.status.value == RootStatus.READY) Backend.ROOT else Backend.DEFAULT
        }
        InstallerType.DEFAULT -> Backend.DEFAULT
    }

    private enum class Backend { DEFAULT, SHIZUKU, DHIZUKU, ROOT }
}
