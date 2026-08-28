package zed.rainxch.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.repository.FavoriteRepo
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.isReallyInstalled
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.RefreshError
import zed.rainxch.core.domain.model.error.RefreshException
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.ApkInspector
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadSpec
import zed.rainxch.core.domain.system.DownloadStage as OrchestratorStage
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.InstallPolicy
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.core.presentation.utils.daysSinceIso
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.details.domain.model.ApkValidationResult
import zed.rainxch.details.domain.model.FingerprintCheckResult
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.domain.model.SaveInstalledAppParams
import zed.rainxch.details.domain.model.UpdateInstalledAppParams
import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.domain.repository.TranslationRepository
import zed.rainxch.details.domain.system.AttestationVerifier
import zed.rainxch.details.domain.system.VerificationResult
import zed.rainxch.details.domain.system.InstallationManager
import zed.rainxch.details.domain.util.VersionHelper
import zed.rainxch.details.presentation.model.AttestationStatus
import zed.rainxch.details.presentation.model.DowngradeWarning
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.details.presentation.model.InstallLogItem
import zed.rainxch.details.presentation.model.LogResult
import zed.rainxch.details.presentation.model.LogResult.Error
import zed.rainxch.details.presentation.model.SigningKeyWarning
import zed.rainxch.details.presentation.model.SupportedLanguages
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_load
import zed.rainxch.githubstore.core.presentation.res.star_added
import zed.rainxch.githubstore.core.presentation.res.star_removed
import zed.rainxch.githubstore.core.presentation.res.star_sign_in_required
import zed.rainxch.githubstore.core.presentation.res.added_to_favourites
import zed.rainxch.githubstore.core.presentation.res.details_unlink_external_app_failure
import zed.rainxch.githubstore.core.presentation.res.details_unlink_external_app_success
import zed.rainxch.githubstore.core.presentation.res.failed_to_load_details
import zed.rainxch.githubstore.core.presentation.res.failed_to_open_app
import zed.rainxch.githubstore.core.presentation.res.failed_to_share_link
import zed.rainxch.githubstore.core.presentation.res.failed_to_uninstall
import zed.rainxch.githubstore.core.presentation.res.installer_saved_downloads
import zed.rainxch.githubstore.core.presentation.res.releases_unavailable_temporarily
import zed.rainxch.githubstore.core.presentation.res.link_copied_to_clipboard
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded_retry_in
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded_signin_hint
import zed.rainxch.githubstore.core.presentation.res.removed_from_favourites
import zed.rainxch.githubstore.core.presentation.res.translation_failed
import zed.rainxch.githubstore.core.presentation.res.update_package_mismatch
import zed.rainxch.githubstore.core.presentation.res.variant_first_pin_toast
import zed.rainxch.githubstore.core.presentation.res.variant_first_pin_toast_generic
import zed.rainxch.githubstore.core.presentation.res.variant_unpinned_toast
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

class DetailsViewModel(
    private val repositoryId: Long,
    private val ownerParam: String,
    private val repoParam: String,
    private val sourceHostParam: String?,
    private val detailsRepository: DetailsRepository,
    private val downloader: Downloader,
    private val installer: Installer,
    private val platform: Platform,
    private val helper: BrowserHelper,
    private val shareManager: ShareManager,
    private val installedAppsRepository: InstalledAppsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val packageMonitor: PackageMonitor,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val translationRepository: TranslationRepository,
    private val logger: KomiStoreLogger,
    private val isComingFromUpdate: Boolean,
    private val tweaksRepository: TweaksRepository,
    private val seenReposRepository: SeenReposRepository,
    private val installationManager: InstallationManager,
    private val attestationVerifier: AttestationVerifier,
    private val downloadOrchestrator: DownloadOrchestrator,
    private val externalImportRepository: ExternalImportRepository,
    private val apkInspector: ApkInspector,
    private val systemInstallSerializer: zed.rainxch.core.domain.system.SystemInstallSerializer,
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {
    private var hasLoadedInitialData = false
    private var currentDownloadJob: Job? = null
    private var currentAssetName: String? = null
    private var aboutTranslationJob: Job? = null
    private var whatsNewTranslationJob: Job? = null

    private val _state = MutableStateFlow(RawDetailsState())
    val state: StateFlow<DetailsState> =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadInitial()
                    observeApkInspectCoachmark()
                    observeChannelChipCoachmark()
                    observeCurrentUserForBadge()
                    observeShowAllPlatforms()

                    hasLoadedInitialData = true
                }
            }
            .map { it.toView() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                DetailsState(),
            )

    private val _events = Channel<DetailsEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val rateLimited = AtomicBoolean(false)

    fun confirmUninstall() {
        _state.update { it.copy(showUninstallConfirmation = false) }
        val installedApp = _state.value.installedApp ?: return
        logger.debug("Uninstalling app (confirmed): ${installedApp.packageName}")
        viewModelScope.launch {
            try {
                installer.uninstall(installedApp.packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to request uninstall for ${installedApp.packageName}: ${e.message}")
                _events.send(
                    DetailsEvent.OnMessage(
                        getString(Res.string.failed_to_uninstall, installedApp.packageName),
                    ),
                )
            }
        }
    }

    private fun confirmUnlinkExternalApp() {
        _state.update { it.copy(showUnlinkConfirmation = false) }
        val installedApp = _state.value.installedApp ?: return
        val packageName = installedApp.packageName
        logger.debug("Unlinking externally-imported app: $packageName")
        viewModelScope.launch {
            try {

                installedAppsRepository.executeInTransaction {
                    externalImportRepository.unlink(packageName)
                    installedAppsRepository.deleteInstalledApp(packageName)
                }
                _events.send(
                    DetailsEvent.OnMessage(
                        getString(Res.string.details_unlink_external_app_success),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to unlink $packageName: ${e.message}")
                _events.send(
                    DetailsEvent.OnMessage(
                        getString(Res.string.details_unlink_external_app_failure),
                    ),
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun onAction(action: DetailsAction) {
        when (action) {
            DetailsAction.Retry -> {
                hasLoadedInitialData = false
                loadInitial()
            }

            DetailsAction.RetryReleases -> retryReleases()

            DetailsAction.Refresh -> refresh()

            DetailsAction.OnDismissDowngradeWarning -> {
                dismissDowngradeWarning()
            }

            DetailsAction.OnDismissSigningKeyWarning -> {
                _state.update {
                    it.copy(
                        signingKeyWarning = null,
                        downloadStage = DownloadStage.IDLE,
                    )
                }
                currentAssetName = null
            }

            DetailsAction.OnOverrideSigningKeyWarning -> {
                overrideSigningKeyWarning()
            }

            DetailsAction.InstallPrimary -> {
                install()
            }

            DetailsAction.OnRequestUninstall -> {
                _state.update { it.copy(showUninstallConfirmation = true) }
            }

            DetailsAction.OnDismissUninstallConfirmation -> {
                _state.update { it.copy(showUninstallConfirmation = false) }
            }

            DetailsAction.OnConfirmUninstall -> {
                confirmUninstall()
            }

            DetailsAction.UninstallApp -> {
                uninstallApp()
            }

            DetailsAction.OnUnlinkExternalApp -> {
                _state.update { it.copy(showUnlinkConfirmation = true) }
            }

            DetailsAction.OnDismissUnlinkConfirmation -> {
                _state.update { it.copy(showUnlinkConfirmation = false) }
            }

            DetailsAction.OnConfirmUnlinkExternalApp -> {
                confirmUnlinkExternalApp()
            }

            is DetailsAction.DownloadAsset -> {
                val release = _state.value.selectedRelease
                downloadAsset(
                    downloadUrl = action.downloadUrl,
                    assetName = action.assetName,
                    sizeBytes = action.sizeBytes,
                    releaseTag = release?.tagName ?: "",
                )
            }

            DetailsAction.CancelCurrentDownload -> {
                cancelCurrentDownload()
            }

            DetailsAction.OnToggleFavorite -> {
                toggleFavourite()
            }

            DetailsAction.OnToggleStar -> {
                toggleStar()
            }

            DetailsAction.OnShareClick -> {
                share()
            }

            DetailsAction.UpdateApp -> {
                update()
            }

            DetailsAction.OpenApp -> {
                openApp()
            }

            DetailsAction.OpenRepoInBrowser -> {
                _state.value.repository?.htmlUrl?.let {
                    helper.openUrl(url = it)
                }
            }

            DetailsAction.OpenAuthorInBrowser -> {
                _state.value.userProfile?.htmlUrl?.let {
                    helper.openUrl(url = it)
                }
            }

            DetailsAction.OpenInObtainium -> {
                openObtainium()
            }

            DetailsAction.OpenInAppManager -> {
                openAppManager()
            }

            DetailsAction.OnToggleInstallDropdown -> {
                _state.update {
                    it.copy(isInstallDropdownExpanded = !it.isInstallDropdownExpanded)
                }
            }

            is DetailsAction.SelectReleaseCategory -> {
                selectReleaseCategory(action)
            }

            is DetailsAction.SelectRelease -> {
                val release = action.release
                val (installable, primary) = recomputeAssetsForRelease(release)
                whatsNewTranslationJob?.cancel()

                _state.update {
                    it.copy(
                        selectedRelease = release,
                        installableAssets = installable,
                        primaryAsset = primary,
                        isVersionPickerVisible = false,
                        whatsNewTranslation = TranslationState(),
                        whatsNewMeasuredHeightPx = null,
                    )
                }
            }

            DetailsAction.ToggleVersionPicker -> {
                _state.update {
                    it.copy(isVersionPickerVisible = !it.isVersionPickerVisible)
                }
            }

            DetailsAction.ToggleAboutExpanded -> {
                _state.update {
                    it.copy(isAboutExpanded = !it.isAboutExpanded)
                }
            }

            DetailsAction.ToggleWhatsNewExpanded -> {
                _state.update {
                    it.copy(isWhatsNewExpanded = !it.isWhatsNewExpanded)
                }
            }

            is DetailsAction.OnAboutMeasured -> {
                val current = _state.value.aboutMeasuredHeightPx
                if (current == null || action.heightPx > current) {
                    _state.update { it.copy(aboutMeasuredHeightPx = action.heightPx) }
                }
            }

            is DetailsAction.OnWhatsNewMeasured -> {
                val current = _state.value.whatsNewMeasuredHeightPx
                if (current == null || action.heightPx > current) {
                    _state.update { it.copy(whatsNewMeasuredHeightPx = action.heightPx) }
                }
            }

            is DetailsAction.TranslateAbout -> {
                val readme = _state.value.readmeMarkdown ?: return
                aboutTranslationJob?.cancel()
                aboutTranslationJob =
                    translateContent(
                        text = readme,
                        targetLanguageCode = action.targetLanguageCode,
                        updateState = { ts -> _state.update { it.copy(aboutTranslation = ts) } },
                        getCurrentState = { _state.value.aboutTranslation },
                    )
            }

            is DetailsAction.TranslateWhatsNew -> {
                val description = _state.value.selectedRelease?.description ?: return
                whatsNewTranslationJob?.cancel()
                whatsNewTranslationJob =
                    translateContent(
                        text = description,
                        targetLanguageCode = action.targetLanguageCode,
                        updateState = { ts -> _state.update { it.copy(whatsNewTranslation = ts) } },
                        getCurrentState = { _state.value.whatsNewTranslation },
                    )
            }

            DetailsAction.ToggleAboutTranslation -> {
                _state.update {
                    val current = it.aboutTranslation
                    it.copy(aboutTranslation = current.copy(isShowingTranslation = !current.isShowingTranslation))
                }
            }

            DetailsAction.ToggleWhatsNewTranslation -> {
                _state.update {
                    val current = it.whatsNewTranslation
                    it.copy(whatsNewTranslation = current.copy(isShowingTranslation = !current.isShowingTranslation))
                }
            }

            is DetailsAction.ShowLanguagePicker -> {
                _state.update {
                    it.copy(
                        isLanguagePickerVisible = true,
                        languagePickerTarget = action.target,
                    )
                }
            }

            DetailsAction.DismissLanguagePicker -> {
                _state.update {
                    it.copy(isLanguagePickerVisible = false, languagePickerTarget = null)
                }
            }

            DetailsAction.OpenWithExternalInstaller -> {
                openExternalInstaller()
            }

            DetailsAction.DismissExternalInstallerPrompt -> {
                _state.value =
                    _state.value.copy(
                        showExternalInstallerPrompt = false,
                        pendingInstallFilePath = null,
                    )
            }

            DetailsAction.InstallWithExternalApp -> {
                installViaExternalApp()
            }

            DetailsAction.OnNavigateBackClick -> {
                // Handled in composable
            }

            is DetailsAction.OpenDeveloperProfile -> {
                // Handled in composable
            }

            is DetailsAction.OnPlatformChipClick -> {
                // Handled in composable
            }

            is DetailsAction.OnMessage -> {
                // Handled in composable
            }

            is DetailsAction.SelectDownloadAsset -> {
                _state.update { state -> state.copy(primaryAsset = action.release) }
                persistPreferredVariantOnPick(action.release)
            }

            DetailsAction.ToggleReleaseAssetsPicker -> {
                _state.update { state -> state.copy(isReleaseSelectorVisible = !state.isReleaseSelectorVisible) }
            }

            DetailsAction.UnpinPreferredVariant -> {
                unpinPreferredVariant()
            }

            DetailsAction.ToggleIncludeBetas -> {

                acknowledgeChannelChipCoachmark()
                toggleIncludeBetas()
            }

            DetailsAction.SwitchToStable -> {
                switchToStable()
            }

            DetailsAction.OnInspectApk -> {
                openApkInspectSheet()
            }

            DetailsAction.OnDismissApkInspect -> {
                _state.update {
                    it.copy(isApkInspectSheetVisible = false)
                }
            }

            DetailsAction.OnAcknowledgeApkInspectCoachmark -> {
                acknowledgeApkInspectCoachmark()
            }

            DetailsAction.OnAcknowledgeChannelChipCoachmark -> {
                acknowledgeChannelChipCoachmark()
            }

            is DetailsAction.OnToggleShowAllPlatforms -> {
                viewModelScope.launch {
                    try {
                        tweaksRepository.setShowAllPlatforms(action.enabled)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logger.warn("Toggle show-all-platforms failed: ${e.message}")
                    }
                }
            }

            is DetailsAction.OnDownloadForTransfer -> {

                helper.openUrl(action.assetUrl) { err ->
                    logger.warn("Open transfer download failed: $err")
                }
            }
        }
    }

    private fun openApkInspectSheet() {
        val installed = _state.value.installedApp
        val parkedPath = installed?.pendingInstallFilePath
        val packageName = installed?.packageName
        val isPending = installed?.isPendingInstall == true
        if (installed == null && parkedPath == null) {
            logger.warn("openApkInspectSheet: nothing inspectable in current state")
            return
        }
        _state.update {
            it.copy(
                isApkInspectSheetVisible = true,
                isApkInspectLoading = true,
                apkInspection = null,
            )
        }
        viewModelScope.launch {
            val inspection =
                if (packageName != null && !isPending) {
                    apkInspector.inspectInstalled(packageName)
                        ?: parkedPath?.let { apkInspector.inspectFile(it) }
                } else if (parkedPath != null) {
                    apkInspector.inspectFile(parkedPath)
                        ?: packageName?.let { apkInspector.inspectInstalled(it) }
                } else if (packageName != null) {
                    apkInspector.inspectInstalled(packageName)
                } else {
                    null
                }
            _state.update {
                it.copy(
                    isApkInspectLoading = false,
                    apkInspection = inspection,
                )
            }

            acknowledgeApkInspectCoachmark()
        }
    }

    private fun acknowledgeApkInspectCoachmark() {
        if (!_state.value.isApkInspectCoachmarkPending) return
        _state.update { it.copy(isApkInspectCoachmarkPending = false) }
        viewModelScope.launch {
            runCatching { tweaksRepository.setApkInspectCoachmarkShown(true) }
                .onFailure { t ->
                    logger.warn("Failed to persist APK inspect coachmark flag: ${t.message}")
                }
        }
    }

    private fun acknowledgeChannelChipCoachmark() {

        _state.update { it.copy(isChannelChipCoachmarkPending = false) }
        viewModelScope.launch {
            runCatching { tweaksRepository.setChannelChipCoachmarkShown(true) }
                .onFailure { t ->
                    logger.warn("Failed to persist channel chip coachmark flag: ${t.message}")
                }
        }
    }

    private data class ReleaseInsights(
        val stalledStableSinceDays: Int?,
        val mergedChangelog: String?,
        val mergedChangelogBaseTag: String?,
        val latestStableHasInstallableAsset: Boolean,
    )

    @OptIn(ExperimentalTime::class)
    private fun computeReleaseInsights(
        allReleases: List<GithubRelease>,
        installedApp: InstalledApp?,
    ): ReleaseInsights {

        val (merged, mergedBase) =
            if (installedApp != null && allReleases.size > 1) {
                val installedTag = installedApp.installedVersion
                val newer =
                    allReleases.filter { release ->
                        VersionMath.isVersionNewer(release.tagName, installedTag)
                    }
                if (newer.size >= 2) {
                    val body =
                        newer.joinToString(separator = "\n\n") { release ->
                            val heading = "— ${release.tagName} —"
                            val notes = release.description?.trim().orEmpty()
                            if (notes.isEmpty()) heading else "$heading\n$notes"
                        }
                    body to installedTag
                } else {
                    null to null
                }
            } else {
                null to null
            }

        val latestStable =
            allReleases
                .filter { !it.isEffectivelyPreRelease() }
                .maxByOrNull { it.publishedAt }

        val stalledDays: Int? =
            run {
                val stable = latestStable ?: return@run null
                val preReleasesAfter =
                    allReleases.any { release ->
                        release.isEffectivelyPreRelease() &&
                                VersionMath.isVersionNewer(release.tagName, stable.tagName)
                    }
                if (!preReleasesAfter) return@run null
                val days = daysSinceIso(stable.publishedAt) ?: return@run null
                if (days >= STALLED_STABLE_THRESHOLD_DAYS) days else null
            }

        val latestStableHasInstallableAsset =
            latestStable?.assets?.any { installer.isAssetInstallable(it.name) } == true

        return ReleaseInsights(
            stalledStableSinceDays = stalledDays,
            mergedChangelog = merged,
            mergedChangelogBaseTag = mergedBase,
            latestStableHasInstallableAsset = latestStableHasInstallableAsset,
        )
    }

    private fun toggleIncludeBetas() {
        val app = _state.value.installedApp ?: return
        val newValue = !app.includePreReleases
        viewModelScope.launch {
            try {
                installedAppsRepository.setIncludePreReleases(
                    packageName = app.packageName,
                    enabled = newValue,
                )

                installedAppsRepository.checkForUpdates(app.packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.warn("toggleIncludeBetas failed for ${app.packageName}: ${t.message}")
            }
        }
    }

    private fun switchToStable() {
        val stable = _state.value.latestStableRelease() ?: return

        val (_, primary) = recomputeAssetsForRelease(stable, _state.value.installedApp)
        if (primary == null) {
            logger.warn(
                "switchToStable: stable ${stable.tagName} has no installable asset; skipping",
            )
            return
        }
        onAction(DetailsAction.SelectRelease(stable))
        onAction(DetailsAction.InstallPrimary)
    }

    private fun persistPreferredVariantOnPick(picked: GithubAsset) {
        val installedApp = _state.value.installedApp ?: return
        val installable = _state.value.installableAssets
        val fingerprint =
            AssetVariant.fingerprintFromPickedAsset(
                pickedAssetName = picked.name,
                siblingAssetCount = installable.size,
            ) ?: return

        val serializedTokens = AssetVariant.serializeTokens(fingerprint.tokens)
        val pickedIndex = installable.indexOfFirst { it.id == picked.id }.takeIf { it >= 0 }

        val currentVariant = installedApp.preferredAssetVariant
        val currentTokens = installedApp.preferredAssetTokens
        val currentGlob = installedApp.assetGlobPattern
        val newSiblingCount = installable.size.takeIf { it > 0 }
        val sameVariant =
            if (fingerprint.variant == null && currentVariant == null) {
                true
            } else {
                fingerprint.variant?.equals(currentVariant, ignoreCase = true) == true
            }
        val isSameFingerprint =
            sameVariant &&
                    serializedTokens == currentTokens &&
                    fingerprint.glob == currentGlob &&
                    pickedIndex == installedApp.pickedAssetIndex &&
                    newSiblingCount == installedApp.pickedAssetSiblingCount

        val isFirstPin =
            currentVariant.isNullOrBlank() &&
                    currentTokens.isNullOrBlank() &&
                    currentGlob.isNullOrBlank()

        val shouldSave = !isSameFingerprint || installedApp.preferredVariantStale
        if (!shouldSave) return

        viewModelScope.launch {
            try {
                installedAppsRepository.setPreferredVariant(
                    packageName = installedApp.packageName,
                    variant = fingerprint.variant,
                    tokens = serializedTokens,
                    glob = fingerprint.glob,
                    pickedIndex = pickedIndex,
                    siblingCount = newSiblingCount,
                )
                if (isFirstPin) {
                    val label = fingerprint.variant
                        ?: fingerprint.tokens.firstOrNull()
                        ?: ""
                    val message =
                        if (label.isNotEmpty()) {
                            getString(Res.string.variant_first_pin_toast, label)
                        } else {
                            getString(Res.string.variant_first_pin_toast_generic)
                        }
                    _events.send(DetailsEvent.OnMessage(message))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(
                    "Failed to persist preferred variant for " +
                            "${installedApp.packageName}: ${e.message}",
                )
            }
        }
    }

    private fun unpinPreferredVariant() {
        val installedApp = _state.value.installedApp ?: return
        viewModelScope.launch {
            try {
                installedAppsRepository.clearPreferredVariant(installedApp.packageName)
                _events.send(
                    DetailsEvent.OnMessage(getString(Res.string.variant_unpinned_toast)),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(
                    "Failed to clear preferred variant for " +
                            "${installedApp.packageName}: ${e.message}",
                )
            }
        }
    }

    private fun observeCurrentUserForBadge() {
        viewModelScope.launch {
            combine(
                userSessionRepository.getUser(),
                _state
                    .map { it.repository?.owner?.login }
                    .distinctUntilChanged(),
            ) { user, ownerLogin ->
                val login = user?.username
                login != null && ownerLogin != null && ownerLogin.equals(login, ignoreCase = true)
            }.collect { isOwner ->
                _state.update { it.copy(isCurrentUserOwner = isOwner) }
            }
        }
    }

    private fun observeApkInspectCoachmark() {
        viewModelScope.launch {
            val alreadyShown =
                runCatching { tweaksRepository.getApkInspectCoachmarkShown().first() }
                    .getOrDefault(true)
            if (alreadyShown) return@launch

            val firstStable = _state.first { !it.isLoading }
            val installedAtOpen =
                firstStable.installedApp?.isReallyInstalled() == true
            if (!installedAtOpen) return@launch
            _state.update { it.copy(isApkInspectCoachmarkPending = true) }
        }
    }

    private fun observeShowAllPlatforms() {
        viewModelScope.launch {
            tweaksRepository.getShowAllPlatforms().collect { enabled ->
                _state.update { it.copy(showAllPlatforms = enabled) }
            }
        }
    }

    private fun observeChannelChipCoachmark() {
        viewModelScope.launch {
            val alreadyShown =
                runCatching { tweaksRepository.getChannelChipCoachmarkShown().first() }
                    .getOrDefault(true)
            if (alreadyShown) return@launch

            val firstStable = _state.first { !it.isLoading }
            if (firstStable.installedApp == null) return@launch
            _state.update { it.copy(isChannelChipCoachmarkPending = true) }
        }
    }

    private fun retryReleases() {
        val repo = _state.value.repository ?: return
        if (_state.value.isRetryingReleases) return
        viewModelScope.launch {
            val prevCategory = _state.value.selectedReleaseCategory
            _state.update { it.copy(isRetryingReleases = true, releasesLoadFailed = false) }
            try {
                val releases =
                    detailsRepository.getAllReleases(
                        owner = repo.owner.login,
                        repo = repo.name,
                        defaultBranch = repo.defaultBranch,
                        sourceHost = sourceHostParam,
                    )

                val byPrevCategory = when (prevCategory) {
                    ReleaseCategory.STABLE -> releases.firstOrNull { !it.isEffectivelyPreRelease() }
                    ReleaseCategory.PRE_RELEASE -> releases.firstOrNull { it.isEffectivelyPreRelease() }
                    ReleaseCategory.ALL -> releases.firstOrNull()
                }
                val selected = byPrevCategory
                    ?: releases.firstOrNull { !it.isEffectivelyPreRelease() }
                    ?: releases.firstOrNull()

                val resolvedCategory = when {
                    byPrevCategory != null -> prevCategory
                    selected?.isEffectivelyPreRelease() == true -> ReleaseCategory.PRE_RELEASE
                    else -> ReleaseCategory.STABLE
                }
                val (installable, primary) =
                    recomputeAssetsForRelease(selected, _state.value.installedApp)
                val insights = computeReleaseInsights(releases, _state.value.installedApp)
                _state.update {
                    it.copy(
                        allReleases = releases,
                        releasesLoadFailed = false,
                        isRetryingReleases = false,
                        selectedRelease = selected,
                        selectedReleaseCategory = resolvedCategory,
                        installableAssets = installable,
                        primaryAsset = primary,
                        stalledStableSinceDays = insights.stalledStableSinceDays,
                        mergedChangelog = insights.mergedChangelog,
                        mergedChangelogBaseTag = insights.mergedChangelogBaseTag,
                        latestStableHasInstallableAsset =
                            insights.latestStableHasInstallableAsset,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: RateLimitException) {
                _state.update {
                    it.copy(isRetryingReleases = false, releasesLoadFailed = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {

                logger.warn("Retry failed to load releases: ${t.message}")
                viewModelScope.launch {
                    _events.send(
                        DetailsEvent.OnMessage(
                            getString(Res.string.releases_unavailable_temporarily),
                        ),
                    )
                }
                _state.update {
                    it.copy(isRetryingReleases = false, releasesLoadFailed = true)
                }
            }
        }
    }

    private fun recomputeAssetsForRelease(
        release: GithubRelease?,
        installedAppOverride: InstalledApp? = _state.value.installedApp,
    ): Pair<List<GithubAsset>, GithubAsset?> {
        val installable =
            release
                ?.assets
                ?.filter { asset ->
                    installer.isAssetInstallable(asset.name)
                }.orEmpty()

        val variantMatch = AssetVariant.resolvePreferredAsset(
            assets = installable,
            pinnedVariant = installedAppOverride?.preferredAssetVariant,
            pinnedTokens = AssetVariant.deserializeTokens(installedAppOverride?.preferredAssetTokens),
            pinnedGlob = installedAppOverride?.assetGlobPattern,
        )
        val samePositionMatch =
            if (variantMatch == null) {
                AssetVariant.resolveBySamePosition(
                    assets = installable,
                    originalIndex = installedAppOverride?.pickedAssetIndex,
                    siblingCountAtPickTime = installedAppOverride?.pickedAssetSiblingCount,
                )
            } else {
                null
            }
        val primary = variantMatch ?: samePositionMatch ?: installer.choosePrimaryAsset(installable)
        return installable to primary
    }

    private fun pickPrimaryInstalledApp(
        apps: List<InstalledApp>,
        primaryAssetName: String?,
    ): InstalledApp? {
        if (apps.isEmpty()) return null
        if (apps.size == 1) return apps.first()
        if (primaryAssetName != null) {
            val filterMatch = apps.firstOrNull { existing ->
                val filter = existing.assetFilterRegex
                filter != null && runCatching { Regex(filter).containsMatchIn(primaryAssetName) }
                    .getOrDefault(false)
            }
            if (filterMatch != null) return filterMatch
        }
        return apps.firstOrNull { !it.isUpdateAvailable } ?: apps.first()
    }

    private fun observeInstalledApp(repoId: Long) {
        viewModelScope.launch {
            installedAppsRepository
                .getAppsByRepoIdAsFlow(repoId)
                .distinctUntilChanged()
                .collect { apps ->

                    val primary = pickPrimaryInstalledApp(
                        apps = apps,
                        primaryAssetName = _state.value.primaryAsset?.name,
                    )

                    val insights = computeReleaseInsights(_state.value.allReleases, primary)
                    _state.update {
                        it.copy(
                            installedApp = primary,
                            installedApps = apps,
                            mergedChangelog = insights.mergedChangelog,
                            mergedChangelogBaseTag = insights.mergedChangelogBaseTag,
                            stalledStableSinceDays = insights.stalledStableSinceDays,
                            latestStableHasInstallableAsset =
                                insights.latestStableHasInstallableAsset,
                        )
                    }
                }
        }
    }

    private fun installViaExternalApp() {
        currentDownloadJob?.cancel()
        val job = viewModelScope.launch {
            try {
                val primary = _state.value.primaryAsset
                val release = _state.value.selectedRelease

                if (primary != null && release != null) {
                    currentAssetName = primary.name

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.DownloadStarted,
                    )

                    _state.value =
                        _state.value.copy(
                            downloadError = null,
                            installError = null,
                            downloadProgressPercent = null,
                            downloadStage = DownloadStage.DOWNLOADING,
                        )

                    downloader
                        .download(primary.downloadUrl, primary.name)
                        .collect { p ->
                            _state.value =
                                _state.value.copy(downloadProgressPercent = p.percent)
                            if (p.percent == 100) {
                                _state.value =
                                    _state.value.copy(downloadStage = DownloadStage.VERIFYING)
                            }
                        }

                    val filePath =
                        downloader.getDownloadedFilePath(primary.name)
                            ?: throw IllegalStateException("Downloaded file not found")

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.Downloaded,
                    )

                    _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                    currentAssetName = null

                    installer.openWithExternalInstaller(filePath)

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.OpenedInExternalInstaller,
                    )
                }
            } catch (e: CancellationException) {
                logger.debug("Install with external app cancelled")
                _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                currentAssetName = null
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to install with external app: ${t.message}")
                _state.value =
                    _state.value.copy(
                        downloadStage = DownloadStage.IDLE,
                        installError = t.message,
                    )
                currentAssetName = null

                _state.value.primaryAsset?.let { asset ->
                    _state.value.selectedRelease?.let { release ->
                        appendLog(
                            assetName = asset.name,
                            size = asset.size,
                            tag = release.tagName,
                            result = Error(t.message),
                        )
                    }
                }
            }
        }

        currentDownloadJob = job
        job.invokeOnCompletion {
            if (currentDownloadJob === job) {
                currentDownloadJob = null
            }
        }

        _state.update {
            it.copy(isInstallDropdownExpanded = false)
        }
    }

    private fun openExternalInstaller() {
        val filePath = _state.value.pendingInstallFilePath
        if (filePath != null) {
            try {
                installer.openWithExternalInstaller(filePath)
                _state.value.primaryAsset?.let { asset ->
                    _state.value.selectedRelease?.let { release ->
                        appendLog(
                            assetName = asset.name,
                            size = asset.size,
                            tag = release.tagName,
                            result = LogResult.OpenedInExternalInstaller,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to open with external installer: ${t.message}")
                _state.value = _state.value.copy(installError = t.message)
            }
        }
        _state.value =
            _state.value.copy(
                showExternalInstallerPrompt = false,
                pendingInstallFilePath = null,
            )
    }

    private fun selectReleaseCategory(action: DetailsAction.SelectReleaseCategory) {
        val newCategory = action.category
        val filtered =
            when (newCategory) {
                ReleaseCategory.STABLE -> _state.value.allReleases.filter { !it.isEffectivelyPreRelease() }
                ReleaseCategory.PRE_RELEASE -> _state.value.allReleases.filter { it.isEffectivelyPreRelease() }
                ReleaseCategory.ALL -> _state.value.allReleases
            }
        val newSelected = filtered.firstOrNull()
        val (installable, primary) = recomputeAssetsForRelease(newSelected)

        whatsNewTranslationJob?.cancel()
        _state.update {
            it.copy(
                selectedReleaseCategory = newCategory,
                selectedRelease = newSelected,
                installableAssets = installable,
                primaryAsset = primary,
                whatsNewTranslation = TranslationState(),
            )
        }
    }

    private fun openAppManager() {
        viewModelScope.launch {
            try {
                val primary = _state.value.primaryAsset
                val release = _state.value.selectedRelease

                if (primary != null && release != null) {
                    currentAssetName = primary.name

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.PreparingForAppManager,
                    )

                    _state.value =
                        _state.value.copy(
                            downloadError = null,
                            installError = null,
                            downloadProgressPercent = null,
                            downloadStage = DownloadStage.DOWNLOADING,
                        )

                    downloader.download(primary.downloadUrl, primary.name).collect { p ->
                        _state.value =
                            _state.value.copy(downloadProgressPercent = p.percent)
                        if (p.percent == 100) {
                            _state.value =
                                _state.value.copy(downloadStage = DownloadStage.VERIFYING)
                        }
                    }

                    val filePath =
                        downloader.getDownloadedFilePath(primary.name)
                            ?: throw IllegalStateException("Downloaded file not found")

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.Downloaded,
                    )

                    _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                    currentAssetName = null

                    installer.openInAppManager(
                        filePath = filePath,
                        onOpenInstaller = {
                            viewModelScope.launch {
                                _events.send(
                                    DetailsEvent.OnOpenRepositoryInApp(APP_MANAGER_REPO_ID),
                                )
                            }
                        },
                    )

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.OpenedInAppManager,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to open in AppManager: ${t.message}")
                _state.value =
                    _state.value.copy(
                        downloadStage = DownloadStage.IDLE,
                        installError = t.message,
                    )
                currentAssetName = null

                _state.value.primaryAsset?.let { asset ->
                    _state.value.selectedRelease?.let { release ->
                        appendLog(
                            assetName = asset.name,
                            size = asset.size,
                            tag = release.tagName,
                            result = Error(t.message),
                        )
                    }
                }
            }
        }
        _state.update {
            it.copy(isInstallDropdownExpanded = false)
        }
    }

    private fun openObtainium() {
        val repo = _state.value.repository
        repo?.owner?.login?.let {
            installer.openInObtainium(
                repoOwner = it,
                repoName = repo.name,
                onOpenInstaller = {
                    viewModelScope.launch {
                        _events.send(
                            DetailsEvent.OnOpenRepositoryInApp(OBTAINIUM_REPO_ID),
                        )
                    }
                },
            )
        }
        _state.update {
            it.copy(isInstallDropdownExpanded = false)
        }
    }

    private fun openApp() {
        val installedApp = _state.value.installedApp ?: return
        val launched = installer.openApp(installedApp.packageName)
        if (!launched) {
            viewModelScope.launch {
                _events.send(
                    DetailsEvent.OnMessage(
                        getString(
                            Res.string.failed_to_open_app,
                            installedApp.appName,
                        ),
                    ),
                )
            }
        }
    }

    private fun update() {
        val installedApp = _state.value.installedApp
        val selectedRelease = _state.value.selectedRelease

        if (installedApp != null && selectedRelease != null && installedApp.isUpdateAvailable) {
            val latestAsset =
                _state.value.primaryAsset
                    ?: _state.value.installableAssets.firstOrNull {
                        it.name == installedApp.latestAssetName
                    }
                    ?: _state.value.installableAssets.firstOrNull {
                        it.name == installedApp.installedAssetName
                    }

            if (latestAsset != null) {
                installAsset(
                    downloadUrl = latestAsset.downloadUrl,
                    assetName = latestAsset.name,
                    sizeBytes = latestAsset.size,
                    releaseTag = selectedRelease.tagName,
                    isUpdate = true,
                )
            }
        }
    }

    private fun share() {
        viewModelScope.launch {
            _state.value.repository?.let { repo ->
                runCatching {
                    shareManager.shareText("https://github-store.org/app?repo=${repo.fullName}")
                }.onFailure { t ->
                    logger.error("Failed to share link: ${t.message}")
                    _events.send(
                        DetailsEvent.OnMessage(getString(Res.string.failed_to_share_link)),
                    )
                    return@launch
                }

                if (platform != Platform.ANDROID) {
                    _events.send(DetailsEvent.OnMessage(getString(Res.string.link_copied_to_clipboard)))
                }
            }
        }
    }

    private fun toggleFavourite() {
        viewModelScope.launch {
            try {
                val repo = _state.value.repository ?: return@launch
                val selectedRelease = _state.value.selectedRelease

                val favoriteRepo =
                    FavoriteRepo(
                        repoId = repo.id,
                        repoName = repo.name,
                        repoOwner = repo.owner.login,
                        repoOwnerAvatarUrl = repo.owner.avatarUrl,
                        repoDescription = repo.description,
                        primaryLanguage = repo.language,
                        repoUrl = repo.htmlUrl,
                        latestVersion = selectedRelease?.tagName,
                        latestReleaseUrl = selectedRelease?.htmlUrl,
                        addedAt = System.now().toEpochMilliseconds(),
                        lastSyncedAt = System.now().toEpochMilliseconds(),
                    )

                favouritesRepository.toggleFavorite(favoriteRepo)

                val newFavoriteState = favouritesRepository.isFavoriteSync(repo.id)
                _state.value = _state.value.copy(isFavourite = newFavoriteState)

                _events.send(
                    element =
                        DetailsEvent.OnMessage(
                            message =
                                getString(
                                    resource =
                                        if (newFavoriteState) {
                                            Res.string.added_to_favourites
                                        } else {
                                            Res.string.removed_from_favourites
                                        },
                                ),
                        ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to toggle favorite: ${t.message}")
            }
        }
    }

    private fun toggleStar() {
        viewModelScope.launch {
            val repo = _state.value.repository ?: return@launch
            if (!userSessionRepository.isCurrentlyUserLoggedIn()) {
                _events.send(DetailsEvent.OnMessage(getString(Res.string.star_sign_in_required)))
                return@launch
            }
            val target = !_state.value.isStarred
            _state.update { it.copy(isStarred = target) }
            starredRepository
                .setStarred(repo.owner.login, repo.name, target)
                .onSuccess {
                    _events.send(
                        DetailsEvent.OnMessage(
                            getString(if (target) Res.string.star_added else Res.string.star_removed),
                        ),
                    )
                    runCatching { starredRepository.syncStarredRepos(forceRefresh = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isStarred = !target) }
                    _events.send(
                        DetailsEvent.OnMessage(e.message ?: getString(Res.string.failed_to_load)),
                    )
                }
        }
    }

    private fun cancelCurrentDownload() {
        currentDownloadJob?.cancel()
        currentDownloadJob = null

        val packageKey = orchestratorKey()
        viewModelScope.launch {
            try {
                downloadOrchestrator.cancel(packageKey)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to cancel orchestrator download: ${t.message}")
            }
        }

        val assetName = currentAssetName
        if (assetName != null) {
            val releaseTag = _state.value.selectedRelease?.tagName ?: ""
            val totalSize = _state.value.totalBytes ?: _state.value.downloadedBytes
            appendLog(
                assetName = assetName,
                tag = releaseTag,
                size = totalSize,
                result = LogResult.Cancelled,
            )
            logger.debug("Download cancelled via orchestrator: $assetName")
        }

        currentAssetName = null
        _state.value =
            _state.value.copy(
                isDownloading = false,
                downloadProgressPercent = null,
                downloadStage = DownloadStage.IDLE,
            )
    }

    private fun uninstallApp() {
        val installedApp = _state.value.installedApp ?: return
        logger.debug("Uninstalling app: ${installedApp.packageName}")
        viewModelScope.launch {
            try {
                installer.uninstall(installedApp.packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to request uninstall for ${installedApp.packageName}: ${e.message}")
                _events.send(
                    DetailsEvent.OnMessage(
                        getString(Res.string.failed_to_uninstall, installedApp.packageName),
                    ),
                )
            }
        }
    }

    private fun install() {
        val primary = _state.value.primaryAsset
        val release = _state.value.selectedRelease
        val installedApp = _state.value.installedApp

        if (primary != null && release != null) {
            if (installedApp != null &&
                !installedApp.isPendingInstall &&
                VersionHelper.normalizeVersion(release.tagName) !=
                VersionHelper.normalizeVersion(
                    installedApp.installedVersion,
                ) &&
                platform == Platform.ANDROID
            ) {
                val isDowngrade =
                    VersionHelper.isDowngradeVersion(
                        candidate = release.tagName,
                        current = installedApp.installedVersion,
                        allReleases = _state.value.allReleases,
                    )

                if (isDowngrade) {
                    _state.update {
                        it.copy(
                            downgradeWarning =
                                DowngradeWarning(
                                    packageName = installedApp.packageName,
                                    currentVersion = installedApp.installedVersion,
                                    targetVersion = release.tagName,
                                ),
                        )
                    }
                    return
                }
            }

            installAsset(
                downloadUrl = primary.downloadUrl,
                assetName = primary.name,
                sizeBytes = primary.size,
                releaseTag = release.tagName,
            )
        }
    }

    private fun overrideSigningKeyWarning() {
        val warning = _state.value.signingKeyWarning ?: return
        _state.update { it.copy(signingKeyWarning = null) }
        dismissDowngradeWarning()
        viewModelScope.launch {
            try {
                val ext = warning.pendingAssetName.substringAfterLast('.', "").lowercase()

                val gatePackageName =
                    if (platform == Platform.ANDROID) warning.pendingApkInfo.packageName else null
                if (gatePackageName != null) {
                    systemInstallSerializer.awaitFreeAndMarkPending(gatePackageName)
                }
                val installOutcome =
                    try {
                        installer.install(warning.pendingFilePath, ext)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        if (gatePackageName != null) {
                            systemInstallSerializer.markCompleted(gatePackageName)
                        }
                        throw e
                    }

                if (platform == Platform.ANDROID) {
                    saveInstalledAppToDatabase(
                        apkInfo = warning.pendingApkInfo,
                        assetName = warning.pendingAssetName,
                        assetUrl = warning.pendingDownloadUrl,
                        assetSize = warning.pendingSizeBytes,
                        releaseTag = warning.pendingReleaseTag,
                        isUpdate = warning.pendingIsUpdate,
                        installOutcome = installOutcome,
                    )
                }

                _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                currentAssetName = null
                appendLog(
                    assetName = warning.pendingAssetName,
                    size = warning.pendingSizeBytes,
                    tag = warning.pendingReleaseTag,
                    result = if (warning.pendingIsUpdate) LogResult.Updated else LogResult.Installed,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Install after override failed: ${t.message}")
                _state.value =
                    _state.value.copy(
                        downloadStage = DownloadStage.IDLE,
                        installError = t.message,
                    )
                currentAssetName = null
            }
        }
    }

    private fun dismissDowngradeWarning() {
        _state.update {
            it.copy(
                downgradeWarning = null,
            )
        }
    }

    private fun installAsset(
        downloadUrl: String,
        assetName: String,
        sizeBytes: Long,
        releaseTag: String,
        isUpdate: Boolean = false,
    ) {

        currentDownloadJob?.cancel()
        val packageKey = orchestratorKey()
        val asset = _state.value.primaryAsset
        val repository = _state.value.repository
        if (asset == null || repository == null) {
            logger.warn("installAsset called with missing primaryAsset/repository")
            return
        }
        currentAssetName = assetName

        val parkedFilePath = parkedFilePathIfMatches(releaseTag, assetName)
        if (parkedFilePath != null) {
            logger.debug("Reusing parked file for $releaseTag / $assetName")
            currentDownloadJob = viewModelScope.launch {
                try {
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = LogResult.Downloaded,
                    )
                    installAsset(
                        isUpdate = isUpdate,
                        filePath = parkedFilePath,
                        assetName = assetName,
                        downloadUrl = downloadUrl,
                        sizeBytes = sizeBytes,
                        releaseTag = releaseTag,
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logger.error("Install of parked file failed: ${t.message}")
                    _state.value =
                        _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            installError = t.message,
                        )
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = Error(t.message),
                    )
                }
            }
            return
        }

        appendLog(
            assetName = assetName,
            size = sizeBytes,
            tag = releaseTag,
            result =
                if (isUpdate) {
                    LogResult.UpdateStarted
                } else {
                    LogResult.DownloadStarted
                },
        )

        currentDownloadJob = viewModelScope.launch {
            try {
                val installerType =
                    try {
                        tweaksRepository.getInstallerType().first()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        InstallerType.DEFAULT
                    }
                val policy =
                    when {
                        platform != Platform.ANDROID -> InstallPolicy.AlwaysInstall
                        installerType == InstallerType.SHIZUKU -> InstallPolicy.AlwaysInstall
                        installerType == InstallerType.DHIZUKU -> InstallPolicy.AlwaysInstall
                        else -> InstallPolicy.InstallWhileForeground
                    }

                downloadOrchestrator.enqueue(
                    DownloadSpec(
                        packageName = packageKey,
                        repoOwner = repository.owner.login,
                        repoName = repository.name,
                        asset = asset,
                        displayAppName = repository.name,
                        installPolicy = policy,
                        releaseTag = releaseTag,
                    ),
                )

                _state.value =
                    _state.value.copy(
                        downloadError = null,
                        installError = null,
                        downloadProgressPercent = null,
                        downloadStage = DownloadStage.DOWNLOADING,
                        downloadedBytes = 0L,
                        totalBytes = sizeBytes,
                        attestationStatus = AttestationStatus.UNCHECKED,
                    )

                observeOrchestratorEntry(
                    packageKey = packageKey,
                    downloadUrl = downloadUrl,
                    assetName = assetName,
                    sizeBytes = sizeBytes,
                    releaseTag = releaseTag,
                    isUpdate = isUpdate,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Install failed: ${t.message}")
                t.printStackTrace()
                _state.value =
                    _state.value.copy(
                        downloadStage = DownloadStage.IDLE,
                        installError = t.message,
                    )
                currentAssetName = null
                appendLog(
                    assetName = assetName,
                    size = sizeBytes,
                    tag = releaseTag,
                    result = Error(t.message),
                )
            }
        }
    }

    private fun parkedFilePathIfMatches(
        releaseTag: String,
        assetName: String,
    ): String? {
        val installedApp = _state.value.installedApp ?: return null
        val parkedPath = installedApp.pendingInstallFilePath ?: return null
        val parkedVersion = installedApp.pendingInstallVersion ?: return null
        val parkedAsset = installedApp.pendingInstallAssetName ?: return null
        if (parkedVersion != releaseTag) return null
        if (parkedAsset != assetName) return null

        return try {
            val file = File(parkedPath)
            if (file.exists() && file.length() > 0) parkedPath else null
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger.warn("Failed to stat parked install file: ${t.message}")
            null
        }
    }

    private fun orchestratorKey(): String {
        val packageName = _state.value.installedApp?.packageName
        if (packageName != null) return packageName
        val owner = _state.value.repository?.owner?.login ?: return "unknown"
        val name = _state.value.repository?.name ?: return "unknown"
        return "$owner/$name"
    }

    private suspend fun observeOrchestratorEntry(
        packageKey: String,
        downloadUrl: String,
        assetName: String,
        sizeBytes: Long,
        releaseTag: String,
        isUpdate: Boolean,
    ) {
        var installFired = false
        downloadOrchestrator.observe(packageKey).collect { entry ->
            if (entry == null) {

                if (_state.value.downloadStage != DownloadStage.IDLE) {
                    _state.value =
                        _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            downloadProgressPercent = null,
                        )
                }
                currentAssetName = null
                return@collect
            }

            _state.value =
                _state.value.copy(
                    downloadProgressPercent = entry.progressPercent,
                    downloadedBytes = entry.bytesDownloaded,
                    totalBytes = entry.totalBytes ?: sizeBytes,
                )

            when (entry.stage) {
                OrchestratorStage.Queued -> {

                    _state.value = _state.value.copy(downloadStage = DownloadStage.DOWNLOADING)
                }

                OrchestratorStage.Downloading -> {
                    _state.value = _state.value.copy(downloadStage = DownloadStage.DOWNLOADING)
                }

                OrchestratorStage.Installing -> {
                    _state.value = _state.value.copy(downloadStage = DownloadStage.INSTALLING)
                }

                OrchestratorStage.AwaitingInstall -> {

                    if (installFired) return@collect
                    installFired = true
                    val filePath = entry.filePath ?: return@collect
                    _state.value =
                        _state.value.copy(downloadStage = DownloadStage.VERIFYING)
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = LogResult.Downloaded,
                    )

                    try {
                        installAsset(
                            isUpdate = isUpdate,
                            filePath = filePath,
                            assetName = assetName,
                            downloadUrl = downloadUrl,
                            sizeBytes = sizeBytes,
                            releaseTag = releaseTag,
                        )

                        downloadOrchestrator.dismiss(packageKey)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        logger.error("Foreground install failed: ${t.message}")
                        _state.value =
                            _state.value.copy(
                                downloadStage = DownloadStage.IDLE,
                                installError = t.message,
                            )
                        appendLog(
                            assetName = assetName,
                            size = sizeBytes,
                            tag = releaseTag,
                            result = Error(t.message),
                        )
                    }
                }

                OrchestratorStage.Completed -> {
                    val resolvedOutcome = entry.installOutcome ?: InstallOutcome.COMPLETED
                    val isCompleted = resolvedOutcome == InstallOutcome.COMPLETED

                    _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = when {
                            !isCompleted -> LogResult.Downloaded
                            isUpdate -> LogResult.Updated
                            else -> LogResult.Installed
                        },
                    )

                    if (platform == Platform.ANDROID) {
                        val filePath = entry.filePath
                        if (filePath != null) {
                            runCatching {
                                val validation = installationManager.validateApk(
                                    filePath = filePath,
                                    isUpdate = isUpdate,
                                    trackedPackageName = _state.value.installedApp?.packageName,
                                )
                                if (validation is ApkValidationResult.Valid) {
                                    saveInstalledAppToDatabase(
                                        apkInfo = validation.apkInfo,
                                        assetName = assetName,
                                        assetUrl = downloadUrl,
                                        assetSize = sizeBytes,
                                        releaseTag = releaseTag,
                                        isUpdate = isUpdate,
                                        installOutcome = resolvedOutcome,
                                        parkedFilePath = filePath,
                                    )
                                } else {
                                    logger.warn(
                                        "Orchestrator install settled (outcome=$resolvedOutcome) " +
                                                "but APK validation failed: $validation",
                                    )
                                }
                            }.onFailure { t ->
                                logger.error("Failed to persist orchestrator install: ${t.message}")
                            }
                        } else {
                            logger.warn(
                                "Orchestrator install settled (outcome=$resolvedOutcome) " +
                                        "but filePath is null; DB not updated",
                            )
                        }
                    }

                    if (isCompleted) {
                        downloadOrchestrator.dismiss(packageKey)
                    }
                    return@collect
                }

                OrchestratorStage.Cancelled -> {
                    _state.value =
                        _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            downloadProgressPercent = null,
                        )
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = LogResult.Cancelled,
                    )
                    return@collect
                }

                OrchestratorStage.Failed -> {
                    _state.value =
                        _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            downloadError = entry.errorMessage,
                        )
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = Error(entry.errorMessage),
                    )
                    _state.value.repository?.id?.let {
                    }
                    downloadOrchestrator.dismiss(packageKey)
                    return@collect
                }
            }
        }
    }

    private suspend fun installAsset(
        isUpdate: Boolean,
        filePath: String,
        assetName: String,
        downloadUrl: String,
        sizeBytes: Long,
        releaseTag: String,
    ) {
        _state.value = _state.value.copy(downloadStage = DownloadStage.INSTALLING)

        val ext = assetName.substringAfterLast('.', "").lowercase()
        val isApk = ext == "apk"
        var validatedApkInfo: ApkPackageInfo? = null

        if (isApk) {
            val validationResult =
                installationManager.validateApk(
                    filePath = filePath,
                    isUpdate = isUpdate,
                    trackedPackageName = _state.value.installedApp?.packageName,
                )

            when (validationResult) {
                is ApkValidationResult.ExtractionFailed -> {

                    logger.warn(
                        "Could not extract APK info for $assetName, " +
                                "proceeding with unvalidated install",
                    )
                }

                is ApkValidationResult.PackageMismatch -> {
                    logger.error(
                        "Package name mismatch on update: " +
                                "APK=${validationResult.apkPackageName}, " +
                                "installed=${validationResult.installedPackageName}",
                    )
                    _state.value =
                        _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            installError =
                                getString(
                                    Res.string.update_package_mismatch,
                                    validationResult.apkPackageName,
                                    validationResult.installedPackageName,
                                ),
                        )
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = Error("Package name mismatch"),
                    )
                    return
                }

                is ApkValidationResult.Valid -> {
                    validatedApkInfo = validationResult.apkInfo
                    val fpResult =
                        installationManager.checkSigningFingerprint(validationResult.apkInfo)
                    if (fpResult is FingerprintCheckResult.Mismatch) {
                        _state.update { state ->
                            state.copy(
                                signingKeyWarning =
                                    SigningKeyWarning(
                                        packageName = validationResult.apkInfo.packageName,
                                        expectedFingerprint = fpResult.expectedFingerprint,
                                        actualFingerprint = fpResult.actualFingerprint,
                                        pendingDownloadUrl = downloadUrl,
                                        pendingAssetName = assetName,
                                        pendingSizeBytes = sizeBytes,
                                        pendingReleaseTag = releaseTag,
                                        pendingIsUpdate = isUpdate,
                                        pendingFilePath = filePath,
                                        pendingApkInfo = validationResult.apkInfo,
                                    ),
                            )
                        }
                        appendLog(
                            assetName = assetName,
                            size = sizeBytes,
                            tag = releaseTag,
                            result = Error("Signing key changed"),
                        )
                        return
                    }
                }
            }
        }

        val gatePackageName =
            if (platform == Platform.ANDROID) validatedApkInfo?.packageName else null
        if (gatePackageName != null) {
            systemInstallSerializer.awaitFreeAndMarkPending(gatePackageName)
        }
        val installOutcome =
            try {
                installer.install(filePath, ext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (gatePackageName != null) {
                    systemInstallSerializer.markCompleted(gatePackageName)
                }
                throw e
            }

        launchAttestationCheck(filePath)

        if (platform == Platform.ANDROID && validatedApkInfo != null) {
            saveInstalledAppToDatabase(
                apkInfo = validatedApkInfo,
                assetName = assetName,
                assetUrl = downloadUrl,
                assetSize = sizeBytes,
                releaseTag = releaseTag,
                isUpdate = isUpdate,
                installOutcome = installOutcome,
                parkedFilePath = filePath,
            )
        } else if (platform != Platform.ANDROID) {
            viewModelScope.launch {
                _events.send(DetailsEvent.OnMessage(getString(Res.string.installer_saved_downloads)))
            }
        }

        _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
        currentAssetName = null
        appendLog(
            assetName = assetName,
            size = sizeBytes,
            tag = releaseTag,
            result =
                if (isUpdate) {
                    LogResult.Updated
                } else {
                    LogResult.Installed
                },
        )
    }

    private fun launchAttestationCheck(filePath: String) {
        val repo = _state.value.repository ?: return
        val owner = repo.owner.login
        val repoName = repo.name

        _state.update { it.copy(attestationStatus = AttestationStatus.CHECKING) }

        viewModelScope.launch {
            val result = attestationVerifier.verify(owner, repoName, filePath)
            _state.update {
                it.copy(
                    attestationStatus = when (result) {
                        is VerificationResult.Verified -> AttestationStatus.VERIFIED
                        is VerificationResult.Unverified -> AttestationStatus.UNVERIFIED
                        is VerificationResult.Error -> AttestationStatus.UNABLE_TO_VERIFY
                    },
                )
            }
        }
    }

    private suspend fun saveInstalledAppToDatabase(
        apkInfo: ApkPackageInfo,
        assetName: String,
        assetUrl: String,
        assetSize: Long,
        releaseTag: String,
        isUpdate: Boolean,
        installOutcome: InstallOutcome,
        parkedFilePath: String? = null,
    ) {
        val repo = _state.value.repository ?: return
        val isPending = installOutcome != InstallOutcome.COMPLETED && platform == Platform.ANDROID

        val pendingPath = parkedFilePath?.takeIf { isPending }

        if (isUpdate) {
            installationManager.updateInstalledAppVersion(
                UpdateInstalledAppParams(
                    apkInfo = apkInfo,
                    assetName = assetName,
                    assetUrl = assetUrl,
                    releaseTag = releaseTag,
                    isPendingInstall = isPending,
                ),
            )

            if (pendingPath != null) {
                runCatching {
                    installedAppsRepository.setPendingInstallFilePath(
                        packageName = apkInfo.packageName,
                        path = pendingPath,
                        version = releaseTag,
                        assetName = assetName,
                    )
                }.onFailure { t ->
                    logger.warn("Failed to park pending install path on update: ${t.message}")
                }
            }
        } else {

            val installable = _state.value.installableAssets
            val pickedIndex = installable
                .indexOfFirst { it.name == assetName }
                .takeIf { it >= 0 }
            val reloaded =
                installationManager.saveNewInstalledApp(
                    SaveInstalledAppParams(
                        repo = repo,
                        apkInfo = apkInfo,
                        assetName = assetName,
                        assetUrl = assetUrl,
                        assetSize = assetSize,
                        releaseTag = releaseTag,
                        isPendingInstall = isPending,
                        isFavourite = _state.value.isFavourite,
                        siblingAssetCount = installable.size,
                        pickedAssetIndex = pickedIndex,
                        pendingInstallFilePath = pendingPath,
                        sourceHost = sourceHostParam,
                    ),
                )
            _state.value = _state.value.copy(installedApp = reloaded)
        }
    }

    private fun downloadAsset(
        downloadUrl: String,
        assetName: String,
        sizeBytes: Long,
        releaseTag: String,
    ) {
        currentDownloadJob?.cancel()
        val packageKey = orchestratorKey()
        val repository = _state.value.repository ?: return

        val asset = _state.value.selectedRelease?.assets
            ?.find { it.downloadUrl == downloadUrl }
            ?: _state.value.primaryAsset
            ?: return
        currentAssetName = assetName

        appendLog(
            assetName = assetName,
            size = sizeBytes,
            tag = releaseTag,
            result = LogResult.DownloadStarted,
        )
        _state.value =
            _state.value.copy(
                isDownloading = true,
                downloadError = null,
                installError = null,
                downloadProgressPercent = null,
            )

        currentDownloadJob = viewModelScope.launch {
            try {
                downloadOrchestrator.enqueue(
                    DownloadSpec(
                        packageName = packageKey,
                        repoOwner = repository.owner.login,
                        repoName = repository.name,
                        asset = asset,
                        displayAppName = repository.name,
                        installPolicy = InstallPolicy.DeferUntilUserAction,
                        releaseTag = releaseTag,
                    ),
                )
                observeOrchestratorEntry(
                    packageKey = packageKey,
                    downloadUrl = downloadUrl,
                    assetName = assetName,
                    sizeBytes = sizeBytes,
                    releaseTag = releaseTag,
                    isUpdate = false,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value =
                    _state.value.copy(
                        isDownloading = false,
                        downloadError = t.message,
                    )
                currentAssetName = null
                appendLog(
                    assetName = assetName,
                    size = sizeBytes,
                    tag = releaseTag,
                    result = Error(t.message),
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun appendLog(
        assetName: String,
        size: Long,
        tag: String,
        result: LogResult,
    ) {
        val now =
            System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .format(
                    LocalDateTime.Format {
                        year()
                        char('-')
                        monthNumber()
                        char('-')
                        day()
                        char(' ')
                        hour()
                        char(':')
                        minute()
                        char(':')
                        second()
                    },
                )
        val newItem =
            InstallLogItem(
                timeIso = now,
                assetName = assetName,
                assetSizeBytes = size,
                releaseTag = tag,
                result = result,
            )
        _state.value =
            _state.value.copy(
                installLogs = listOf(newItem) + _state.value.installLogs,
            )
    }

    override fun onCleared() {
        super.onCleared()

        currentDownloadJob?.cancel()

        val packageKey = orchestratorKey()
        viewModelScope.launch(NonCancellable) {
            try {
                downloadOrchestrator.downgradeToDeferred(packageKey)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to downgrade orchestrator on screen leave: ${t.message}")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun loadInitial() {
        viewModelScope.launch {
            try {
                rateLimited.set(false)

                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val syncResult = syncInstalledAppsUseCase()
                if (syncResult.isFailure) {
                    logger.warn("Sync had issues but continuing: ${syncResult.exceptionOrNull()?.message}")
                }

                val repo =
                    when {

                        sourceHostParam != null -> {
                            if (ownerParam.isBlank() || repoParam.isBlank()) {
                                error("Foreign-source Details opened without owner/repo for host=$sourceHostParam")
                            }
                            detailsRepository.getRepositoryByOwnerAndName(
                                owner = ownerParam,
                                name = repoParam,
                                sourceHost = sourceHostParam,
                            )
                        }

                        ownerParam.isNotEmpty() && repoParam.isNotEmpty() ->
                            detailsRepository.getRepositoryByOwnerAndName(
                                owner = ownerParam,
                                name = repoParam,
                                sourceHost = null,
                            )

                        else -> detailsRepository.getRepositoryById(repositoryId)
                    }
                launch { seenReposRepository.markAsSeen(repo) }

                val isFavoriteDeferred =
                    async {
                        try {
                            favouritesRepository.isFavoriteSync(repo.id)
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger.error("Failed to load if repo is favourite: ${t.localizedMessage}")
                            false
                        }
                    }
                val isStarredDeferred =
                    async {
                        try {
                            starredRepository.isStarred(repo.id)
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger.error("Failed to load if repo is starred: ${t.localizedMessage}")
                            false
                        }
                    }
                val isFavorite = isFavoriteDeferred.await()
                val isStarred = isStarredDeferred.await()

                val owner = repo.owner.login
                val name = repo.name

                _state.value =
                    _state.value.copy(
                        repository = repo,
                        isFavourite = isFavorite == true,
                        isStarred = isStarred == true,
                    )

                val allReleasesDeferred =
                    async {
                        try {
                            detailsRepository.getAllReleases(
                                owner = owner,
                                repo = name,
                                defaultBranch = repo.defaultBranch,
                                sourceHost = sourceHostParam,
                            ) to false
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            emptyList<GithubRelease>() to true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger.warn("Failed to load releases: ${t.message}")
                            emptyList<GithubRelease>() to true
                        }
                    }

                val statsDeferred =
                    async {
                        try {
                            detailsRepository.getRepoStats(
                                owner = owner,
                                repo = name,
                                sourceHost = sourceHostParam,
                            )
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            null
                        }
                    }

                val readmeDeferred =
                    async {
                        try {
                            detailsRepository.getReadme(
                                owner = owner,
                                repo = name,
                                defaultBranch = repo.defaultBranch,
                                sourceHost = sourceHostParam,
                            )
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            null
                        }
                    }

                val userProfileDeferred =
                    async {

                        if (sourceHostParam != null) return@async null
                        try {
                            detailsRepository.getUserProfile(owner)
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger.warn("Failed to load user profile: ${t.message}")
                            null
                        }
                    }

                val installedAppsDeferred =
                    async {
                        try {
                            val dbApps = installedAppsRepository.getAppsByRepoId(repo.id)

                            dbApps.map { dbApp ->
                                if (dbApp.isPendingInstall &&
                                    packageMonitor.isPackageInstalled(dbApp.packageName)
                                ) {
                                    installedAppsRepository.updatePendingStatus(
                                        dbApp.packageName,
                                        false,
                                    )
                                    installedAppsRepository.getAppByPackage(dbApp.packageName)
                                        ?: dbApp
                                } else {
                                    dbApp
                                }
                            }
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            emptyList()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger.error("Failed to load installed apps: ${t.message}")
                            emptyList()
                        }
                    }

                val isObtainiumEnabled = platform == Platform.ANDROID
                val isAppManagerEnabled = platform == Platform.ANDROID

                val (allReleases, releasesFailed) = allReleasesDeferred.await()
                val stats = statsDeferred.await()
                val readme = readmeDeferred.await()
                val userProfile = userProfileDeferred.await()
                val allInstalledApps = installedAppsDeferred.await()
                val installedApp = pickPrimaryInstalledApp(
                    apps = allInstalledApps,
                    primaryAssetName = null,
                )

                if (rateLimited.get()) {

                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = null,
                        releasesLoadFailed = true,
                    )
                    return@launch
                }

                val selectedRelease =
                    allReleases.firstOrNull { !it.isEffectivelyPreRelease() }
                        ?: allReleases.firstOrNull()

                val (installable, primary) = recomputeAssetsForRelease(
                    selectedRelease,
                    installedApp
                )

                val isObtainiumAvailable = installer.isObtainiumInstalled()
                val isAppManagerAvailable = installer.isAppManagerInstalled()

                logger.debug("Loaded repo: ${repo.name}, installedApp: ${installedApp?.packageName}")

                val insights = computeReleaseInsights(allReleases, installedApp)

                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = null,
                        repository = repo,
                        allReleases = allReleases,
                        releasesLoadFailed = releasesFailed,
                        isRetryingReleases = false,
                        selectedRelease = selectedRelease,
                        selectedReleaseCategory = ReleaseCategory.STABLE,
                        stats = stats,
                        readmeMarkdown = readme?.first,
                        readmeLanguage = readme?.second,
                        installableAssets = installable,
                        primaryAsset = primary,
                        userProfile = userProfile,
                        systemArchitecture = installer.detectSystemArchitecture(),
                        isObtainiumAvailable = isObtainiumAvailable,
                        isObtainiumEnabled = isObtainiumEnabled,
                        isAppManagerAvailable = isAppManagerAvailable,
                        isAppManagerEnabled = isAppManagerEnabled,
                        installedApp = installedApp,
                        deviceLanguageCode = translationRepository.getDeviceLanguageCode(),
                        isComingFromUpdate = isComingFromUpdate,
                        stalledStableSinceDays = insights.stalledStableSinceDays,
                        mergedChangelog = insights.mergedChangelog,
                        mergedChangelogBaseTag = insights.mergedChangelogBaseTag,
                        latestStableHasInstallableAsset =
                            insights.latestStableHasInstallableAsset,
                    )

                observeInstalledApp(repo.id)

                maybeAutoTranslate(
                    readmeBody = readme?.first,
                    releaseDescription = selectedRelease?.description,
                )
            } catch (e: RateLimitException) {
                logger.error("Rate limited: ${e.message}")
                val seconds = e.rateLimitInfo.timeUntilReset().inWholeSeconds
                val signedIn = userSessionRepository.isCurrentlyUserLoggedIn()
                val base = if (seconds > 0L) {
                    getString(Res.string.rate_limit_exceeded_retry_in, seconds.toInt())
                } else {
                    getString(Res.string.rate_limit_exceeded)
                }
                val message = if (!signedIn) {
                    base + " " + getString(Res.string.rate_limit_exceeded_signin_hint)
                } else {
                    base
                }
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = message,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Details load failed: ${t.message}")
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = t.message ?: getString(Res.string.failed_to_load_details),
                    )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun refresh() {
        if (_state.value.isRefreshing) return
        val nowMs = System.now().toEpochMilliseconds()
        _state.value.refreshCooldownUntilEpochMs?.let { cooldownUntil ->
            if (cooldownUntil > nowMs) {
                val remaining = ((cooldownUntil - nowMs + 999) / 1000)
                viewModelScope.launch {
                    _events.send(
                        DetailsEvent.OnRefreshError(
                            kind = RefreshError.COOLDOWN,
                            retryAfterSeconds = remaining,
                        ),
                    )
                }
                return
            }
        }
        val repo = _state.value.repository ?: return
        val owner = repo.owner.login
        val name = repo.name

        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {

                val refreshed = if (sourceHostParam != null) {
                    detailsRepository.getRepositoryByOwnerAndName(
                        owner = owner,
                        name = name,
                        sourceHost = sourceHostParam,
                    )
                } else {
                    detailsRepository.refreshRepository(owner, name)
                }
                val releasesDeferred = async {
                    try {
                        detailsRepository.getAllReleases(
                            owner = owner,
                            repo = name,
                            defaultBranch = refreshed.defaultBranch,
                            sourceHost = sourceHostParam,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        logger.warn("Refresh: getAllReleases failed: ${t.message}")
                        null
                    }
                }
                val statsDeferred = async {
                    try {
                        detailsRepository.getRepoStats(
                            owner = owner,
                            repo = name,
                            sourceHost = sourceHostParam,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        logger.warn("Refresh: getRepoStats failed: ${t.message}")
                        null
                    }
                }
                val freshReleases = releasesDeferred.await()
                val freshStats = statsDeferred.await()

                val previousSelected = _state.value.selectedRelease
                val previousCategory = _state.value.selectedReleaseCategory
                val carried = freshReleases?.let { list ->
                    previousSelected?.let { prev ->
                        list.firstOrNull { it.id == prev.id }
                            ?: list.firstOrNull { it.tagName == prev.tagName }
                    }
                }
                val selectedRelease = freshReleases?.let { list ->
                    carried
                        ?: list.firstOrNull { !it.isEffectivelyPreRelease() }
                        ?: list.firstOrNull()
                } ?: previousSelected

                val resolvedCategory = when {
                    carried != null -> previousCategory
                    selectedRelease?.isEffectivelyPreRelease() == true -> ReleaseCategory.PRE_RELEASE
                    selectedRelease != null -> ReleaseCategory.STABLE
                    else -> previousCategory
                }

                val (installable, primary) = recomputeAssetsForRelease(
                    selectedRelease,
                    _state.value.installedApp,
                )
                val insights = computeReleaseInsights(
                    freshReleases ?: _state.value.allReleases,
                    _state.value.installedApp,
                )

                _state.update {
                    it.copy(
                        isRefreshing = false,
                        repository = refreshed,
                        allReleases = freshReleases ?: it.allReleases,
                        releasesLoadFailed = freshReleases == null && it.releasesLoadFailed,
                        selectedRelease = selectedRelease,
                        selectedReleaseCategory = resolvedCategory,
                        stats = freshStats ?: it.stats,
                        installableAssets = installable,
                        primaryAsset = primary,
                        stalledStableSinceDays = insights.stalledStableSinceDays,
                        mergedChangelog = insights.mergedChangelog,
                        mergedChangelogBaseTag = insights.mergedChangelogBaseTag,
                        latestStableHasInstallableAsset =
                            insights.latestStableHasInstallableAsset,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefreshException) {
                logger.warn("Refresh failed (${e.kind}): ${e.message}")
                val cooldownUntil = e.retryAfterSeconds?.let { sec ->
                    System.now().toEpochMilliseconds() + sec * 1000L
                }
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        refreshCooldownUntilEpochMs =
                            if (e.kind == RefreshError.COOLDOWN ||
                                e.kind == RefreshError.BUDGET_EXHAUSTED
                            ) {
                                cooldownUntil ?: it.refreshCooldownUntilEpochMs
                            } else {
                                it.refreshCooldownUntilEpochMs
                            },
                    )
                }
                _events.send(
                    DetailsEvent.OnRefreshError(
                        kind = e.kind,
                        retryAfterSeconds = e.retryAfterSeconds,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Refresh failed: ${t.message}")
                _state.update { it.copy(isRefreshing = false) }
                _events.send(
                    DetailsEvent.OnRefreshError(kind = RefreshError.GENERIC),
                )
            }
        }
    }

    private fun maybeAutoTranslate(readmeBody: String?, releaseDescription: String?) {
        viewModelScope.launch {
            val enabled = runCatching {
                tweaksRepository.getAutoTranslateEnabled().first()
            }.getOrDefault(false)
            if (!enabled) return@launch

            val explicit = runCatching {
                tweaksRepository.getAutoTranslateTargetLang().first()
            }.getOrNull()?.takeIf { it.isNotBlank() }
            val app = runCatching {
                tweaksRepository.getAppLanguage().first()
            }.getOrNull()?.takeIf { it.isNotBlank() }
            val target = explicit ?: app ?: translationRepository.getDeviceLanguageCode()
            if (target.isBlank()) return@launch

            val currentReadmeLang = _state.value.readmeLanguage
            if (!readmeBody.isNullOrBlank() &&
                _state.value.aboutTranslation.translatedText == null &&
                currentReadmeLang?.equals(target, ignoreCase = true) != true
            ) {
                aboutTranslationJob?.cancel()
                aboutTranslationJob = translateContent(
                    text = readmeBody,
                    targetLanguageCode = target,
                    updateState = { ts -> _state.update { it.copy(aboutTranslation = ts) } },
                    getCurrentState = { _state.value.aboutTranslation },
                )
            }

            if (!releaseDescription.isNullOrBlank() &&
                _state.value.whatsNewTranslation.translatedText == null &&
                currentReadmeLang?.equals(target, ignoreCase = true) != true
            ) {
                whatsNewTranslationJob?.cancel()
                whatsNewTranslationJob = translateContent(
                    text = releaseDescription,
                    targetLanguageCode = target,
                    updateState = { ts -> _state.update { it.copy(whatsNewTranslation = ts) } },
                    getCurrentState = { _state.value.whatsNewTranslation },
                )
            }
        }
    }

    private fun translateContent(
        text: String,
        targetLanguageCode: String,
        updateState: (TranslationState) -> Unit,
        getCurrentState: () -> TranslationState,
    ): Job = viewModelScope.launch {
        try {
            updateState(
                getCurrentState().copy(
                    isTranslating = true,
                    error = null,
                    targetLanguageCode = targetLanguageCode,
                ),
            )

            val result =
                translationRepository.translate(
                    text = text,
                    targetLanguage = targetLanguageCode,
                )

            val langDisplayName =
                SupportedLanguages.all
                    .find { it.code == targetLanguageCode }
                    ?.displayName
                    ?: targetLanguageCode

            updateState(
                TranslationState(
                    isTranslating = false,
                    translatedText = result.translatedText,
                    isShowingTranslation = true,
                    targetLanguageCode = targetLanguageCode,
                    targetLanguageDisplayName = langDisplayName,
                    detectedSourceLanguage = result.detectedSourceLanguage,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Translation failed: ${e.message}")
            updateState(
                getCurrentState().copy(
                    isTranslating = false,
                    error = e.message,
                ),
            )
            _events.send(
                DetailsEvent.OnMessage(getString(Res.string.translation_failed)),
            )
        }
    }

    private companion object {
        const val OBTAINIUM_REPO_ID: Long = 523534328
        const val APP_MANAGER_REPO_ID: Long = 268006778
        const val STALLED_STABLE_THRESHOLD_DAYS = 180
    }
}
