package zed.rainxch.core.domain.system

import zed.rainxch.core.domain.model.GithubAsset
import zed.rainxch.core.domain.model.SystemArchitecture

interface Installer {
    suspend fun isSupported(extOrMime: String): Boolean

    suspend fun ensurePermissionsOrThrow(extOrMime: String)

    suspend fun install(
        filePath: String,
        extOrMime: String,
    )

    fun uninstall(packageName: String)

    fun isAssetInstallable(assetName: String): Boolean

    fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset?

    fun detectSystemArchitecture(): SystemArchitecture

    fun isObtainiumInstalled(): Boolean

    fun openInObtainium(
        repoOwner: String,
        repoName: String,
        onOpenInstaller: () -> Unit,
    )

    fun isAppManagerInstalled(): Boolean

    fun openInAppManager(
        filePath: String,
        onOpenInstaller: () -> Unit,
    )

    fun getApkInfoExtractor(): InstallerInfoExtractor

    fun openApp(packageName: String): Boolean

    fun openWithExternalInstaller(filePath: String)
}
