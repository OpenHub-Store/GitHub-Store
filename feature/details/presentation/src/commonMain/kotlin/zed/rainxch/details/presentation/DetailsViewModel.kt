package zed.rainxch.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.ApkPackageInfo
import zed.rainxch.core.domain.model.FavoriteRepo
import zed.rainxch.core.domain.model.GithubAsset
import zed.rainxch.core.domain.model.GithubRelease
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.core.domain.utils.BrowserHelper
import zed.rainxch.core.domain.utils.ShareManager
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
import zed.rainxch.githubstore.core.presentation.res.added_to_favourites
import zed.rainxch.githubstore.core.presentation.res.failed_to_open_app
import zed.rainxch.githubstore.core.presentation.res.failed_to_share_link
import zed.rainxch.githubstore.core.presentation.res.failed_to_uninstall
import zed.rainxch.githubstore.core.presentation.res.installer_saved_downloads
import zed.rainxch.githubstore.core.presentation.res.link_copied_to_clipboard
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded
import zed.rainxch.githubstore.core.presentation.res.removed_from_favourites
import zed.rainxch.githubstore.core.presentation.res.translation_failed
import zed.rainxch.githubstore.core.presentation.res.update_package_mismatch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

class DetailsViewModel(
    private val repositoryId: Long,
    private val ownerParam: String,
    private val repoParam: String,
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
    private val logger: GitHubStoreLogger,
    private val isComingFromUpdate: Boolean,
    private val tweaksRepository: TweaksRepository,
    private val seenReposRepository: SeenReposRepository,
    private val installationManager: InstallationManager,
    private val attestationVerifier: AttestationVerifier,
) : ViewModel() {
    private var hasLoadedInitialData = false
    private var currentDownloadJob: Job? = null
    private var currentAssetName: String? = null
    private var aboutTranslationJob: Job? = null
    private var whatsNewTranslationJob: Job? = null

    private var cachedDownloadAssetName: String? = null

    private val _state = MutableStateFlow(DetailsState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadInitial()
                    observeLiquidGlassEnabled()

                    hasLoadedInitialData = true
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                DetailsState(),
            )

    private val _events = Channel<DetailsEvent>()
    val events = _events.receiveAsFlow()

    private val rateLimited = AtomicBoolean(false)

    fun confirmUninstall() {
        _state.update { it.copy(showUninstallConfirmation = false) }
        val installedApp = _state.value.installedApp ?: return
        logger.debug("Uninstalling app (confirmed): ${installedApp.packageName}")
        viewModelScope.launch {
            try {
                installer.uninstall(installedApp.packageName)
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

    @OptIn(ExperimentalTime::class)
    fun onAction(action: DetailsAction) {
        when (action) {
            DetailsAction.Retry -> {
                hasLoadedInitialData = false
                loadInitial()
            }

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

            is DetailsAction.OnMessage -> {
                // Handled in composable
            }

            is DetailsAction.SelectDownloadAsset -> {
                _state.update { state -> state.copy(primaryAsset = action.release) }
            }

            DetailsAction.ToggleReleaseAssetsPicker -> {
                _state.update { state -> state.copy(isReleaseSelectorVisible = !state.isReleaseSelectorVisible) }
            }
        }
    }

    private fun observeLiquidGlassEnabled() {
        viewModelScope.launch {
        }
    }

    private fun recomputeAssetsForRelease(release: GithubRelease?): Pair<List<GithubAsset>, GithubAsset?> {
        val installable =
            release
                ?.assets
                ?.filter { asset ->
                    installer.isAssetInstallable(asset.name)
                }.orEmpty()
        val primary = installer.choosePrimaryAsset(installable)
        return installable to primary
    }

    private fun observeInstalledApp(repoId: Long) {
        viewModelScope.launch {
            installedAppsRepository
                .getAppByRepoIdAsFlow(repoId)
                .distinctUntilChanged()
                .collect { app ->
                    _state.update { it.copy(installedApp = app) }
                }
        }
    }

    private fun installViaExternalApp() {
        currentDownloadJob?.cancel()
        val job =
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
                ReleaseCategory.STABLE -> _state.value.allReleases.filter { !it.isPrerelease }
                ReleaseCategory.PRE_RELEASE -> _state.value.allReleases.filter { it.isPrerelease }
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
                _state.value.installableAssets.firstOrNull {
                    it.name == installedApp.latestAssetName
                } ?: _state.value.primaryAsset

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
            } catch (t: Throwable) {
                logger.error("Failed to toggle favorite: ${t.message}")
            }
        }
    }

    private fun cancelCurrentDownload() {
        currentDownloadJob?.cancel()
        currentDownloadJob = null

        val assetName = currentAssetName
        if (assetName != null) {
            cachedDownloadAssetName = assetName
            val releaseTag = _state.value.selectedRelease?.tagName ?: ""
            val totalSize = _state.value.totalBytes ?: _state.value.downloadedBytes
            appendLog(
                assetName = assetName,
                tag = releaseTag,
                size = totalSize,
                result = LogResult.Cancelled,
            )
            logger.debug("Download cancelled – keeping file for potential reuse: $assetName")
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
                val installOutcome = installer.install(warning.pendingFilePath, ext)

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
        currentDownloadJob =
            viewModelScope.launch {
                try {
                    val filePath: String =
                        downloadAsset(
                            assetName = assetName,
                            sizeBytes = sizeBytes,
                            releaseTag = releaseTag,
                            isUpdate = isUpdate,
                            downloadUrl = downloadUrl,
                        ) ?: return@launch

                    installAsset(
                        isUpdate = isUpdate,
                        filePath = filePath,
                        assetName = assetName,
                        downloadUrl = downloadUrl,
                        sizeBytes = sizeBytes,
                        releaseTag = releaseTag,
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
                    logger.error("Failed to extract APK info for $assetName")
                    _state.value =
                        _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            installError = "Failed to verify APK package info",
                        )
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = Error("Failed to extract APK info"),
                    )
                    return
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

        val installOutcome = installer.install(filePath, ext)

        // Launch attestation check asynchronously (non-blocking)
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
            )
        } else if (platform != Platform.ANDROID) {
            cachedDownloadAssetName = null
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

    private suspend fun downloadAsset(
        assetName: String,
        sizeBytes: Long,
        releaseTag: String,
        isUpdate: Boolean,
        downloadUrl: String,
    ): String? {
        currentAssetName = assetName

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
        _state.value =
            _state.value.copy(
                downloadError = null,
                installError = null,
                downloadProgressPercent = null,
                attestationStatus = AttestationStatus.UNCHECKED,
            )

        val existingPath = downloader.getDownloadedFilePath(assetName)
        val filePath: String

        val existingFile = existingPath?.let { File(it) }
        if (existingFile != null && existingFile.exists() && existingFile.length() == sizeBytes) {
            logger.debug("Reusing already downloaded file: $assetName")
            filePath = existingPath
            _state.value =
                _state.value.copy(
                    downloadProgressPercent = 100,
                    downloadedBytes = sizeBytes,
                    totalBytes = sizeBytes,
                    downloadStage = DownloadStage.VERIFYING,
                )
        } else {
            _state.value =
                _state.value.copy(
                    downloadStage = DownloadStage.DOWNLOADING,
                    downloadedBytes = 0L,
                    totalBytes = sizeBytes,
                )
            downloader.download(downloadUrl, assetName).collect { p ->
                _state.value =
                    _state.value.copy(
                        downloadProgressPercent = p.percent,
                        downloadedBytes = p.bytesDownloaded,
                        totalBytes = p.totalBytes ?: sizeBytes,
                    )
                if (p.percent == 100) {
                    _state.value =
                        _state.value.copy(downloadStage = DownloadStage.VERIFYING)
                }
            }

            filePath = downloader.getDownloadedFilePath(assetName)
                ?: throw IllegalStateException("Downloaded file not found")

            cachedDownloadAssetName = assetName
        }

        appendLog(
            assetName = assetName,
            size = sizeBytes,
            tag = releaseTag,
            result = LogResult.Downloaded,
        )
        val ext = assetName.substringAfterLast('.', "").lowercase()

        if (!installer.isSupported(ext)) {
            throw IllegalStateException("Asset type .$ext not supported")
        }

        try {
            installer.ensurePermissionsOrThrow(extOrMime = ext)
        } catch (e: IllegalStateException) {
            logger.warn("Install permission blocked: ${e.message}")
            _state.value =
                _state.value.copy(
                    downloadStage = DownloadStage.IDLE,
                    showExternalInstallerPrompt = true,
                    pendingInstallFilePath = filePath,
                )
            currentAssetName = null
            appendLog(
                assetName = assetName,
                size = sizeBytes,
                tag = releaseTag,
                result = LogResult.PermissionBlocked,
            )
            return null
        }

        return filePath
    }

    private suspend fun saveInstalledAppToDatabase(
        apkInfo: ApkPackageInfo,
        assetName: String,
        assetUrl: String,
        assetSize: Long,
        releaseTag: String,
        isUpdate: Boolean,
        installOutcome: InstallOutcome,
    ) {
        val repo = _state.value.repository ?: return

        if (isUpdate) {
            installationManager.updateInstalledAppVersion(
                UpdateInstalledAppParams(
                    apkInfo = apkInfo,
                    assetName = assetName,
                    assetUrl = assetUrl,
                    releaseTag = releaseTag,
                    isPendingInstall = installOutcome != InstallOutcome.COMPLETED,
                ),
            )
        } else {
            val reloaded =
                installationManager.saveNewInstalledApp(
                    SaveInstalledAppParams(
                        repo = repo,
                        apkInfo = apkInfo,
                        assetName = assetName,
                        assetUrl = assetUrl,
                        assetSize = assetSize,
                        releaseTag = releaseTag,
                        isPendingInstall = installOutcome != InstallOutcome.COMPLETED,
                        isFavourite = _state.value.isFavourite,
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
        currentDownloadJob =
            viewModelScope.launch {
                try {
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

                    downloader.download(downloadUrl, assetName).collect { p ->
                        _state.value = _state.value.copy(downloadProgressPercent = p.percent)
                    }

                    _state.value = _state.value.copy(isDownloading = false)
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = LogResult.Downloaded,
                    )
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
                        result = LogResult.Error(t.message),
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

        val assetsToClean = listOfNotNull(currentAssetName, cachedDownloadAssetName).distinct()
        if (assetsToClean.isNotEmpty()) {
            viewModelScope.launch(NonCancellable) {
                for (asset in assetsToClean) {
                    try {
                        downloader.cancelDownload(asset)
                        logger.debug("Cleaned up download on screen leave: $asset")
                    } catch (t: Throwable) {
                        logger.error("Failed to clean download on leave: ${t.message}")
                    }
                }
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
                    if (ownerParam.isNotEmpty() && repoParam.isNotEmpty()) {
                        detailsRepository.getRepositoryByOwnerAndName(ownerParam, repoParam)
                    } else {
                        detailsRepository.getRepositoryById(repositoryId)
                    }
                launch { seenReposRepository.markAsSeen(repo.id) }

                val isFavoriteDeferred =
                    async {
                        try {
                            favouritesRepository.isFavoriteSync(repo.id)
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (t: Throwable) {
                            logger.error("Failed to load if repo is favourite: ${t.localizedMessage}")
                            false
                        }
                    }
                val isFavorite = isFavoriteDeferred.await()
                val isStarredDeferred =
                    async {
                        try {
                            starredRepository.isStarred(repo.id)
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (t: Throwable) {
                            logger.error("Failed to load if repo is starred: ${t.localizedMessage}")
                            false
                        }
                    }
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
                            )
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            emptyList()
                        } catch (t: Throwable) {
                            logger.warn("Failed to load releases: ${t.message}")
                            emptyList()
                        }
                    }

                val statsDeferred =
                    async {
                        try {
                            detailsRepository.getRepoStats(owner, name)
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
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
                            )
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (_: Throwable) {
                            null
                        }
                    }

                val userProfileDeferred =
                    async {
                        try {
                            detailsRepository.getUserProfile(owner)
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (t: Throwable) {
                            logger.warn("Failed to load user profile: ${t.message}")
                            null
                        }
                    }

                val installedAppDeferred =
                    async {
                        try {
                            val dbApp = installedAppsRepository.getAppByRepoId(repo.id)

                            if (dbApp != null) {
                                if (dbApp.isPendingInstall &&
                                    packageMonitor.isPackageInstalled(dbApp.packageName)
                                ) {
                                    installedAppsRepository.updatePendingStatus(
                                        dbApp.packageName,
                                        false,
                                    )
                                    installedAppsRepository.getAppByPackage(dbApp.packageName)
                                } else {
                                    dbApp
                                }
                            } else {
                                null
                            }
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (t: Throwable) {
                            logger.error("Failed to load installed app: ${t.message}")
                            null
                        }
                    }

                val isObtainiumEnabled = platform == Platform.ANDROID
                val isAppManagerEnabled = platform == Platform.ANDROID

                val allReleases = allReleasesDeferred.await()
                val stats = statsDeferred.await()
                val readme = readmeDeferred.await()
                val userProfile = userProfileDeferred.await()
                val installedApp = installedAppDeferred.await()

                if (rateLimited.get()) {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = null)
                    return@launch
                }

                val selectedRelease =
                    allReleases.firstOrNull { !it.isPrerelease }
                        ?: allReleases.firstOrNull()

                val (installable, primary) = recomputeAssetsForRelease(selectedRelease)

                val isObtainiumAvailable = installer.isObtainiumInstalled()
                val isAppManagerAvailable = installer.isAppManagerInstalled()

                val liquidGlassEnabled = tweaksRepository.getLiquidGlassEnabled().first()

                logger.debug("Loaded repo: ${repo.name}, installedApp: ${installedApp?.packageName}")

                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = null,
                        repository = repo,
                        allReleases = allReleases,
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
                        isLiquidGlassEnabled = liquidGlassEnabled,
                    )

                observeInstalledApp(repo.id)
            } catch (e: RateLimitException) {
                logger.error("Rate limited: ${e.message}")
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = getString(Res.string.rate_limit_exceeded),
                    )
            } catch (t: Throwable) {
                logger.error("Details load failed: ${t.message}")
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = t.message ?: "Failed to load details",
                    )
            }
        }
    }

    private fun translateContent(
        text: String,
        targetLanguageCode: String,
        updateState: (TranslationState) -> Unit,
        getCurrentState: () -> TranslationState,
    ): Job =
        viewModelScope.launch {
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
    }
}
