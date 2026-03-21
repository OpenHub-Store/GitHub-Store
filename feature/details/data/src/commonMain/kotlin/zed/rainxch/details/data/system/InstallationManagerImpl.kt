package zed.rainxch.details.data.system

import kotlinx.coroutines.delay
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.ApkPackageInfo
import zed.rainxch.core.domain.model.InstallSource
import zed.rainxch.core.domain.model.InstalledApp
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.details.domain.model.ApkValidationResult
import zed.rainxch.details.domain.model.FingerprintCheckResult
import zed.rainxch.details.domain.model.SaveInstalledAppParams
import zed.rainxch.details.domain.model.UpdateInstalledAppParams
import zed.rainxch.details.domain.system.InstallationManager
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

class InstallationManagerImpl(
    private val installer: Installer,
    private val installedAppsRepository: InstalledAppsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val logger: GitHubStoreLogger,
) : InstallationManager {
    override suspend fun validateApk(
        filePath: String,
        isUpdate: Boolean,
        trackedPackageName: String?,
    ): ApkValidationResult {
        val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(filePath)
            ?: return ApkValidationResult.ExtractionFailed

        if (isUpdate && trackedPackageName != null && apkInfo.packageName != trackedPackageName) {
            return ApkValidationResult.PackageMismatch(
                apkPackageName = apkInfo.packageName,
                installedPackageName = trackedPackageName,
            )
        }

        return ApkValidationResult.Valid(apkInfo)
    }

    override suspend fun checkSigningFingerprint(apkInfo: ApkPackageInfo): FingerprintCheckResult {
        val existingApp =
            installedAppsRepository.getAppByPackage(apkInfo.packageName)
                ?: return FingerprintCheckResult.Ok

        val expectedFp = existingApp.signingFingerprint ?: return FingerprintCheckResult.Ok
        val actualFp = apkInfo.signingFingerprint ?: return FingerprintCheckResult.Ok

        return if (expectedFp == actualFp) {
            FingerprintCheckResult.Ok
        } else {
            FingerprintCheckResult.Mismatch(
                expectedFingerprint = expectedFp,
                actualFingerprint = actualFp,
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun saveNewInstalledApp(params: SaveInstalledAppParams): InstalledApp? =
        try {
            val apkInfo = params.apkInfo
            val repo = params.repo

            val installedApp =
                InstalledApp(
                    packageName = apkInfo.packageName,
                    repoId = repo.id,
                    repoName = repo.name,
                    repoOwner = repo.owner.login,
                    repoOwnerAvatarUrl = repo.owner.avatarUrl,
                    repoDescription = repo.description,
                    primaryLanguage = repo.language,
                    repoUrl = repo.htmlUrl,
                    installedVersion = params.releaseTag,
                    installedAssetName = params.assetName,
                    installedAssetUrl = params.assetUrl,
                    latestVersion = params.releaseTag,
                    latestAssetName = params.assetName,
                    latestAssetUrl = params.assetUrl,
                    latestAssetSize = params.assetSize,
                    appName = apkInfo.appName,
                    installSource = InstallSource.THIS_APP,
                    installedAt = System.now().toEpochMilliseconds(),
                    lastCheckedAt = System.now().toEpochMilliseconds(),
                    lastUpdatedAt = System.now().toEpochMilliseconds(),
                    isUpdateAvailable = false,
                    updateCheckEnabled = true,
                    releaseNotes = "",
                    systemArchitecture = installer.detectSystemArchitecture().name,
                    fileExtension = params.assetName.substringAfterLast('.', ""),
                    isPendingInstall = params.isPendingInstall,
                    installedVersionName = apkInfo.versionName,
                    installedVersionCode = apkInfo.versionCode,
                    latestVersionName = apkInfo.versionName,
                    latestVersionCode = apkInfo.versionCode,
                    signingFingerprint = apkInfo.signingFingerprint,
                )

            installedAppsRepository.saveInstalledApp(installedApp)

            if (params.isFavourite) {
                favouritesRepository.updateFavoriteInstallStatus(
                    repoId = repo.id,
                    installed = true,
                    packageName = apkInfo.packageName,
                )
            }

            delay(1000)
            val reloaded = installedAppsRepository.getAppByPackage(apkInfo.packageName)
            logger.debug("Successfully saved and reloaded app: ${reloaded?.packageName}")
            reloaded
        } catch (t: Throwable) {
            logger.error("Failed to save installed app to database: ${t.message}")
            t.printStackTrace()
            null
        }

    override suspend fun updateInstalledAppVersion(params: UpdateInstalledAppParams) {
        installedAppsRepository.updateAppVersion(
            packageName = params.apkInfo.packageName,
            newTag = params.releaseTag,
            newAssetName = params.assetName,
            newAssetUrl = params.assetUrl,
            newVersionName = params.apkInfo.versionName,
            newVersionCode = params.apkInfo.versionCode,
            signingFingerprint = params.apkInfo.signingFingerprint,
            isPendingInstall = params.isPendingInstall,
        )
    }
}
