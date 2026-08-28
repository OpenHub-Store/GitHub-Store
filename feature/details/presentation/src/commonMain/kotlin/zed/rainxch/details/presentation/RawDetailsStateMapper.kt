package zed.rainxch.details.presentation

import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.details.domain.model.ReleaseCategory

internal fun RawDetailsState.toView(): DetailsState {
    val filteredReleases = when (selectedReleaseCategory) {
        ReleaseCategory.STABLE -> allReleases.filter { !it.isEffectivelyPreRelease() }
        ReleaseCategory.PRE_RELEASE -> allReleases.filter { it.isEffectivelyPreRelease() }
        ReleaseCategory.ALL -> allReleases
    }.toImmutableList()
    val latestStableRelease = allReleases
        .filter { !it.isEffectivelyPreRelease() }
        .maxByOrNull { it.publishedAt }
    val canSwitchToStable = computeCanSwitchToStable(latestStableRelease)
    val isPendingInstallReady = computeIsPendingInstallReady()

    return DetailsState(
        isLoading = isLoading,
        isCurrentUserOwner = isCurrentUserOwner,
        isRefreshing = isRefreshing,
        refreshCooldownUntilEpochMs = refreshCooldownUntilEpochMs,
        errorMessage = errorMessage,
        userProfile = userProfile,
        repository = repository,
        primaryAsset = primaryAsset,
        installableAssets = installableAssets.toImmutableList(),
        selectedRelease = selectedRelease,
        allReleases = allReleases.toImmutableList(),
        releasesLoadFailed = releasesLoadFailed,
        isRetryingReleases = isRetryingReleases,
        isReleaseSelectorVisible = isReleaseSelectorVisible,
        selectedReleaseCategory = selectedReleaseCategory,
        isVersionPickerVisible = isVersionPickerVisible,
        stats = stats,
        readmeMarkdown = readmeMarkdown,
        readmeLanguage = readmeLanguage,
        installLogs = installLogs.toImmutableList(),
        isDownloading = isDownloading,
        downloadProgressPercent = downloadProgressPercent,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        isInstalling = isInstalling,
        downloadError = downloadError,
        installError = installError,
        downloadStage = downloadStage,
        systemArchitecture = systemArchitecture,
        isObtainiumAvailable = isObtainiumAvailable,
        isObtainiumEnabled = isObtainiumEnabled,
        isInstallDropdownExpanded = isInstallDropdownExpanded,
        isAppManagerAvailable = isAppManagerAvailable,
        isAppManagerEnabled = isAppManagerEnabled,
        installedApp = installedApp,
        installedApps = installedApps.toImmutableList(),
        isFavourite = isFavourite,
        isStarred = isStarred,
        isTrackingApp = isTrackingApp,
        isAboutExpanded = isAboutExpanded,
        isWhatsNewExpanded = isWhatsNewExpanded,
        aboutMeasuredHeightPx = aboutMeasuredHeightPx,
        whatsNewMeasuredHeightPx = whatsNewMeasuredHeightPx,
        aboutTranslation = aboutTranslation,
        whatsNewTranslation = whatsNewTranslation,
        isLanguagePickerVisible = isLanguagePickerVisible,
        languagePickerTarget = languagePickerTarget,
        deviceLanguageCode = deviceLanguageCode,
        isComingFromUpdate = isComingFromUpdate,
        downgradeWarning = downgradeWarning,
        signingKeyWarning = signingKeyWarning,
        showExternalInstallerPrompt = showExternalInstallerPrompt,
        pendingInstallFilePath = pendingInstallFilePath,
        showUninstallConfirmation = showUninstallConfirmation,
        showUnlinkConfirmation = showUnlinkConfirmation,
        attestationStatus = attestationStatus,
        stalledStableSinceDays = stalledStableSinceDays,
        mergedChangelog = mergedChangelog,
        mergedChangelogBaseTag = mergedChangelogBaseTag,
        latestStableHasInstallableAsset = latestStableHasInstallableAsset,
        apkInspection = apkInspection,
        isApkInspectSheetVisible = isApkInspectSheetVisible,
        isApkInspectLoading = isApkInspectLoading,
        isApkInspectCoachmarkPending = isApkInspectCoachmarkPending,
        isChannelChipCoachmarkPending = isChannelChipCoachmarkPending,
        showAllPlatforms = showAllPlatforms,
        filteredReleases = filteredReleases,
        latestStableRelease = latestStableRelease,
        canSwitchToStable = canSwitchToStable,
        isPendingInstallReady = isPendingInstallReady,
    )
}

internal fun RawDetailsState.latestStableRelease(): GithubRelease? =
    allReleases
        .filter { !it.isEffectivelyPreRelease() }
        .maxByOrNull { it.publishedAt }

private fun RawDetailsState.computeCanSwitchToStable(latestStable: GithubRelease?): Boolean {
    val app = installedApp ?: return false
    val stable = latestStable ?: return false
    if (!latestStableHasInstallableAsset) return false
    val installedIsPreRelease = allReleases
        .firstOrNull { VersionMath.isSameVersion(it.tagName, app.installedVersion) }
        ?.isEffectivelyPreRelease() == true
    if (!installedIsPreRelease) return false
    return !VersionMath.isSameVersion(stable.tagName, app.installedVersion)
}

private fun RawDetailsState.computeIsPendingInstallReady(): Boolean {
    val app = installedApp ?: return false
    val parkedVersion = app.pendingInstallVersion ?: return false
    val parkedAsset = app.pendingInstallAssetName ?: return false
    if (app.pendingInstallFilePath.isNullOrBlank()) return false
    val tag = selectedRelease?.tagName ?: return false
    val assetName = primaryAsset?.name ?: return false
    return parkedVersion == tag && parkedAsset == assetName
}
