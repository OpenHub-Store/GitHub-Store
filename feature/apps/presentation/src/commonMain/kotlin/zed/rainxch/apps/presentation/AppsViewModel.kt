package zed.rainxch.apps.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.model.UpdateAllProgress
import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.InstalledApp
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.core.domain.utils.ShareManager
import zed.rainxch.githubstore.core.presentation.res.*
import java.io.File

class AppsViewModel(
    private val appsRepository: AppsRepository,
    private val installer: Installer,
    private val downloader: Downloader,
    private val installedAppsRepository: InstalledAppsRepository,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val logger: GitHubStoreLogger,
    private val shareManager: ShareManager,
) : ViewModel() {
    companion object {
        private const val UPDATE_CHECK_COOLDOWN_MS = 30 * 60 * 1000L // 30 minutes
    }

    private var hasLoadedInitialData = false
    private val activeUpdates = mutableMapOf<String, Job>()
    private var updateAllJob: Job? = null
    private var lastAutoCheckTimestamp: Long = 0L

    private val _state = MutableStateFlow(AppsState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadApps()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = AppsState(),
            )

    private val _events = Channel<AppsEvent>()
    val events = _events.receiveAsFlow()

    private fun loadApps() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val syncResult = syncInstalledAppsUseCase()
                if (syncResult.isFailure) {
                    logger.error("Sync had issues but continuing: ${syncResult.exceptionOrNull()?.message}")
                }

                appsRepository.getApps().collect { apps ->
                    val appItems =
                        apps
                            .map { app ->
                                val existing =
                                    _state.value.apps.find {
                                        it.installedApp.packageName == app.packageName
                                    }
                                AppItem(
                                    installedApp = app,
                                    updateState = existing?.updateState ?: UpdateState.Idle,
                                    downloadProgress = existing?.downloadProgress,
                                    error = existing?.error,
                                )
                            }.sortedBy { it.installedApp.isUpdateAvailable }

                    _state.update {
                        it.copy(
                            apps = appItems,
                            isLoading = false,
                            updateAllButtonEnabled =
                                appItems.any { item ->
                                    item.installedApp.isUpdateAvailable
                                },
                        )
                    }

                    filterApps()
                }
            } catch (e: Exception) {
                logger.error("Failed to load apps: ${e.message}")
                _state.update {
                    it.copy(isLoading = false)
                }
            }

            autoCheckForUpdatesIfNeeded()
        }
    }

    private fun autoCheckForUpdatesIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastAutoCheckTimestamp < UPDATE_CHECK_COOLDOWN_MS) {
            logger.debug("Skipping auto-check: last check was ${(now - lastAutoCheckTimestamp) / 1000}s ago")
            return
        }
        checkAllForUpdates()
    }

    private fun checkAllForUpdates() {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingForUpdates = true) }
            try {
                syncInstalledAppsUseCase()
                installedAppsRepository.checkAllForUpdates()
                val now = System.currentTimeMillis()
                lastAutoCheckTimestamp = now
                _state.update { it.copy(lastCheckedTimestamp = now) }
            } catch (e: Exception) {
                logger.error("Check all for updates failed: ${e.message}")
            } finally {
                _state.update { it.copy(isCheckingForUpdates = false) }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            try {
                syncInstalledAppsUseCase()
                installedAppsRepository.checkAllForUpdates()
                val now = System.currentTimeMillis()
                lastAutoCheckTimestamp = now
                _state.update { it.copy(lastCheckedTimestamp = now) }
            } catch (e: Exception) {
                logger.error("Refresh failed: ${e.message}")
            } finally {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun onAction(action: AppsAction) {
        when (action) {
            AppsAction.OnNavigateBackClick -> {
            }

            is AppsAction.OnSearchChange -> {
                _state.update {
                    it.copy(searchQuery = action.query)
                }

                filterApps()
            }

            is AppsAction.OnOpenApp -> {
                openApp(action.app)
            }

            is AppsAction.OnUpdateApp -> {
                updateSingleApp(action.app)
            }

            is AppsAction.OnCancelUpdate -> {
                cancelUpdate(action.packageName)
            }

            AppsAction.OnUpdateAll -> {
                updateAllApps()
            }

            AppsAction.OnCancelUpdateAll -> {
                cancelAllUpdates()
            }

            AppsAction.OnCheckAllForUpdates -> {
                checkAllForUpdates()
            }

            AppsAction.OnRefresh -> {
                refresh()
            }

            is AppsAction.OnNavigateToRepo -> {
                viewModelScope.launch {
                    _events.send(AppsEvent.NavigateToRepo(action.repoId))
                }
            }

            is AppsAction.OnUninstallApp -> {
                _state.update { it.copy(appPendingUninstall = action.app) }
            }

            // Link app to repo
            AppsAction.OnAddByLinkClick -> openLinkSheet()
            AppsAction.OnDismissLinkSheet -> dismissLinkSheet()
            is AppsAction.OnDeviceAppSearchChange -> {
                _state.update { it.copy(deviceAppSearchQuery = action.query) }
            }
            is AppsAction.OnDeviceAppSelected -> {
                _state.update {
                    it.copy(
                        selectedDeviceApp = action.app,
                        linkStep = LinkStep.EnterUrl,
                        repoUrl = "",
                        repoValidationError = null,
                        fetchedRepoInfo = null,
                    )
                }
            }
            is AppsAction.OnRepoUrlChanged -> {
                _state.update {
                    it.copy(
                        repoUrl = action.url,
                        repoValidationError = null,
                    )
                }
            }
            AppsAction.OnValidateAndLinkRepo -> validateAndLinkRepo()
            AppsAction.OnBackToAppPicker -> {
                _state.update {
                    it.copy(
                        linkStep = LinkStep.PickApp,
                        selectedDeviceApp = null,
                        repoUrl = "",
                        repoValidationError = null,
                        fetchedRepoInfo = null,
                    )
                }
            }

            // Export/Import
            AppsAction.OnExportApps -> exportApps()
            AppsAction.OnImportApps -> importAppsFromFile()

            // Uninstall confirmation
            is AppsAction.OnUninstallConfirmed -> {
                uninstallApp(action.app)
                _state.update { it.copy(appPendingUninstall = null) }
            }
            AppsAction.OnDismissUninstallDialog -> {
                _state.update { it.copy(appPendingUninstall = null) }
            }
        }
    }

    private fun filterApps() {
        _state.update { current ->
            current.copy(
                filteredApps = computeFilteredApps(current.apps, current.searchQuery),
            )
        }
    }

    private fun computeFilteredApps(
        apps: List<AppItem>,
        query: String,
    ): List<AppItem> =
        if (query.isBlank()) {
            apps.sortedBy { it.installedApp.isUpdateAvailable }
        } else {
            apps
                .filter { appItem ->
                    appItem.installedApp.appName.contains(query, ignoreCase = true) ||
                        appItem.installedApp.repoOwner.contains(query, ignoreCase = true)
                }.sortedBy { it.installedApp.isUpdateAvailable }
        }

    private fun uninstallApp(app: InstalledApp) {
        viewModelScope.launch {
            try {
                installer.uninstall(app.packageName)
                logger.debug("Requested uninstall for ${app.packageName}")
            } catch (e: Exception) {
                logger.error("Failed to request uninstall for ${app.packageName}: ${e.message}")
                _events.send(
                    AppsEvent.ShowError(
                        getString(Res.string.failed_to_uninstall, app.appName),
                    ),
                )
            }
        }
    }

    private fun openApp(app: InstalledApp) {
        viewModelScope.launch {
            try {
                appsRepository.openApp(
                    installedApp = app,
                    onCantLaunchApp = {
                        viewModelScope.launch {
                            _events.send(
                                AppsEvent.ShowError(
                                    getString(
                                        Res.string.cannot_launch,
                                        app.appName,
                                    ),
                                ),
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                logger.error("Failed to open app: ${e.message}")
                _events.send(
                    AppsEvent.ShowError(
                        getString(
                            Res.string.failed_to_open,
                            app.appName,
                        ),
                    ),
                )
            }
        }
    }

    private fun updateSingleApp(app: InstalledApp) {
        if (activeUpdates.containsKey(app.packageName)) {
            logger.debug("Update already in progress for ${app.packageName}")
            return
        }

        val job =
            viewModelScope.launch {
                try {
                    updateAppState(app.packageName, UpdateState.CheckingUpdate)

                    val latestRelease =
                        try {
                            appsRepository.getLatestRelease(
                                owner = app.repoOwner,
                                repo = app.repoName,
                            )
                        } catch (e: Exception) {
                            logger.error("Failed to fetch latest release: ${e.message}")
                            throw IllegalStateException("Failed to fetch latest release: ${e.message}")
                        }

                    if (latestRelease == null) {
                        throw IllegalStateException("No release found for ${app.appName}")
                    }

                    val installableAssets =
                        latestRelease.assets.filter { asset ->
                            installer.isAssetInstallable(asset.name)
                        }

                    if (installableAssets.isEmpty()) {
                        throw IllegalStateException("No installable assets found for this platform")
                    }

                    val primaryAsset =
                        installer.choosePrimaryAsset(installableAssets)
                            ?: throw IllegalStateException("Could not determine primary asset")

                    logger.debug(
                        "Update: ${app.appName} from ${app.installedVersion} to ${latestRelease.tagName}, " +
                            "asset: ${primaryAsset.name}",
                    )

                    val latestAssetUrl = primaryAsset.downloadUrl
                    val latestAssetName = primaryAsset.name
                    val latestVersion = latestRelease.tagName
                    val latestAssetSize = primaryAsset.size

                    val ext = latestAssetName.substringAfterLast('.', "").lowercase()
                    installer.ensurePermissionsOrThrow(ext)

                    val existingPath = downloader.getDownloadedFilePath(latestAssetName)
                    if (existingPath != null) {
                        val file = File(existingPath)
                        try {
                            val apkInfo =
                                installer.getApkInfoExtractor().extractPackageInfo(existingPath)
                            val normalizedExisting =
                                apkInfo?.versionName?.removePrefix("v")?.removePrefix("V") ?: ""
                            val normalizedLatest =
                                latestVersion.removePrefix("v").removePrefix("V")
                            if (normalizedExisting != normalizedLatest) {
                                val deleted = file.delete()
                                logger.debug("Deleted mismatched existing file ($normalizedExisting != $normalizedLatest): $deleted")
                            }
                        } catch (e: Exception) {
                            logger.debug("Failed to extract APK info for existing file: ${e.message}")
                            val deleted = file.delete()
                            logger.debug("Deleted unextractable existing file: $deleted")
                        }
                    }

                    updateAppState(app.packageName, UpdateState.Downloading)

                    downloader.download(latestAssetUrl, latestAssetName).collect { progress ->
                        updateAppProgress(app.packageName, progress.percent)
                    }

                    val filePath =
                        downloader.getDownloadedFilePath(latestAssetName)
                            ?: throw IllegalStateException("Downloaded file not found")

                    val apkInfo =
                        installer.getApkInfoExtractor().extractPackageInfo(filePath)
                            ?: throw IllegalStateException("Failed to extract APK info")

                    // Save latest release metadata and mark as pending install
                    // so PackageEventReceiver can verify the actual installation
                    val currentApp = installedAppsRepository.getAppByPackage(app.packageName)
                    if (currentApp != null) {
                        installedAppsRepository.updateApp(
                            currentApp.copy(
                                isPendingInstall = true,
                                latestVersion = latestVersion,
                                latestAssetName = latestAssetName,
                                latestAssetUrl = latestAssetUrl,
                                latestVersionName = apkInfo.versionName,
                                latestVersionCode = apkInfo.versionCode,
                            ),
                        )
                    } else {
                        markPendingUpdate(app)
                    }

                    updateAppState(app.packageName, UpdateState.Installing)

                    try {
                        installer.install(filePath, ext)
                    } catch (e: Exception) {
                        installedAppsRepository.updatePendingStatus(app.packageName, false)
                        throw e
                    }

                    // Don't mark as updated here — installer.install() just launches the
                    // system install dialog and returns immediately. PackageEventReceiver
                    // will handle confirming the actual installation via broadcast.
                    updateAppState(app.packageName, UpdateState.Idle)

                    logger.debug("Launched installer for ${app.appName} $latestVersion, waiting for system confirmation")
                } catch (e: CancellationException) {
                    logger.debug("Update cancelled for ${app.packageName}")
                    cleanupUpdate(app.packageName, app.latestAssetName)
                    try {
                        installedAppsRepository.updatePendingStatus(app.packageName, false)
                    } catch (clearEx: Exception) {
                        logger.error("Failed to clear pending status on cancellation: ${clearEx.message}")
                    }
                    updateAppState(app.packageName, UpdateState.Idle)
                    throw e
                } catch (_: RateLimitException) {
                    logger.debug("Rate limited during update for ${app.packageName}")
                    try {
                        installedAppsRepository.updatePendingStatus(app.packageName, false)
                    } catch (clearEx: Exception) {
                        logger.error("Failed to clear pending status on rate limit: ${clearEx.message}")
                    }
                    updateAppState(app.packageName, UpdateState.Idle)
                    _events.send(
                        AppsEvent.ShowError(getString(Res.string.rate_limit_exceeded)),
                    )
                } catch (e: Exception) {
                    logger.error("Update failed for ${app.packageName}: ${e.message}")
                    cleanupUpdate(app.packageName, app.latestAssetName)
                    try {
                        installedAppsRepository.updatePendingStatus(app.packageName, false)
                    } catch (clearEx: Exception) {
                        logger.error("Failed to clear pending status on error: ${clearEx.message}")
                    }
                    updateAppState(
                        app.packageName,
                        UpdateState.Error(e.message ?: "Update failed"),
                    )
                    _events.send(
                        AppsEvent.ShowError(
                            getString(
                                Res.string.failed_to_update,
                                app.appName,
                                e.message ?: "",
                            ),
                        ),
                    )
                } finally {
                    activeUpdates.remove(app.packageName)
                }
            }

        activeUpdates[app.packageName] = job
    }

    private fun updateAllApps() {
        if (_state.value.isUpdatingAll) {
            logger.error("Update all already in progress")
            return
        }

        updateAllJob =
            viewModelScope.launch {
                try {
                    _state.update { it.copy(isUpdatingAll = true) }

                    val appsToUpdate =
                        _state.value.apps.filter {
                            it.installedApp.isUpdateAvailable &&
                                it.updateState !is UpdateState.Success
                        }

                    if (appsToUpdate.isEmpty()) {
                        _events.send(AppsEvent.ShowError(getString(Res.string.no_updates_available)))
                        return@launch
                    }

                    logger.debug("Starting update all for ${appsToUpdate.size} apps")

                    appsToUpdate.forEachIndexed { index, appItem ->
                        if (!isActive) {
                            logger.debug("Update all cancelled")
                            return@launch
                        }

                        _state.update {
                            it.copy(
                                updateAllProgress =
                                    UpdateAllProgress(
                                        current = index + 1,
                                        total = appsToUpdate.size,
                                        currentAppName = appItem.installedApp.appName,
                                    ),
                            )
                        }

                        logger.debug("Updating ${index + 1}/${appsToUpdate.size}: ${appItem.installedApp.appName}")

                        updateSingleApp(appItem.installedApp)
                        activeUpdates[appItem.installedApp.packageName]?.join()

                        delay(1000)
                    }

                    logger.debug("Update all completed successfully")
                    _events.send(AppsEvent.ShowSuccess(getString(Res.string.all_apps_updated_successfully)))
                } catch (e: CancellationException) {
                    logger.debug("Update all cancelled")
                } catch (e: Exception) {
                    logger.error("Update all failed: ${e.message}")
                    _events.send(
                        AppsEvent.ShowError(
                            getString(
                                Res.string.update_all_failed,
                                arrayOf(e.message),
                            ),
                        ),
                    )
                } finally {
                    _state.update {
                        it.copy(
                            isUpdatingAll = false,
                            updateAllProgress = null,
                        )
                    }
                    updateAllJob = null
                }
            }
    }

    private fun cancelUpdate(packageName: String) {
        activeUpdates[packageName]?.cancel()
        activeUpdates.remove(packageName)

        val app = _state.value.apps.find { it.installedApp.packageName == packageName }
        app?.installedApp?.latestAssetName?.let { assetName ->
            viewModelScope.launch {
                cleanupUpdate(packageName, assetName)
            }
        }

        updateAppState(packageName, UpdateState.Idle)
    }

    private fun cancelAllUpdates() {
        updateAllJob?.cancel()
        updateAllJob = null

        activeUpdates.values.forEach { it.cancel() }
        activeUpdates.clear()

        viewModelScope.launch {
            _state.value.apps.forEach { appItem ->
                if (appItem.updateState != UpdateState.Idle &&
                    appItem.updateState != UpdateState.Success
                ) {
                    appItem.installedApp.latestAssetName?.let { assetName ->
                        cleanupUpdate(appItem.installedApp.packageName, assetName)
                    }
                    updateAppState(appItem.installedApp.packageName, UpdateState.Idle)
                }
            }
        }

        _state.update {
            it.copy(
                isUpdatingAll = false,
                updateAllProgress = null,
            )
        }
    }

    private fun updateAppState(
        packageName: String,
        state: UpdateState,
    ) {
        _state.update { currentState ->
            currentState.copy(
                apps =
                    currentState.apps.map { appItem ->
                        if (appItem.installedApp.packageName == packageName) {
                            appItem.copy(
                                updateState = state,
                                downloadProgress =
                                    if (state is UpdateState.Downloading) {
                                        appItem.downloadProgress
                                    } else {
                                        null
                                    },
                                error = if (state is UpdateState.Error) state.message else null,
                            )
                        } else {
                            appItem
                        }
                    },
            )
        }

        filterApps()
    }

    private fun updateAppProgress(
        packageName: String,
        progress: Int?,
    ) {
        _state.update { currentState ->
            currentState.copy(
                apps =
                    currentState.apps.map { appItem ->
                        if (appItem.installedApp.packageName == packageName) {
                            appItem.copy(downloadProgress = progress)
                        } else {
                            appItem
                        }
                    },
            )
        }

        filterApps()
    }

    private suspend fun markPendingUpdate(app: InstalledApp) {
        installedAppsRepository.updatePendingStatus(app.packageName, true)
        logger.debug("Marked ${app.packageName} as pending install")
    }

    private suspend fun cleanupUpdate(
        packageName: String,
        assetName: String?,
    ) {
        try {
            if (assetName != null) {
                val deleted = downloader.cancelDownload(assetName)
                logger.debug("Cleanup for $packageName - file deleted: $deleted")
            }
        } catch (e: Exception) {
            logger.error("Cleanup failed for $packageName: ${e.message}")
        }
    }

    // ── Link app to repo ──────────────────────────────────────────

    private fun openLinkSheet() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    showLinkSheet = true,
                    linkStep = LinkStep.PickApp,
                    deviceApps = emptyList(),
                    deviceAppSearchQuery = "",
                    selectedDeviceApp = null,
                    repoUrl = "",
                    repoValidationError = null,
                    fetchedRepoInfo = null,
                )
            }

            try {
                val trackedPackages = appsRepository.getTrackedPackageNames()
                val deviceApps = appsRepository.getDeviceApps()
                    .filter { it.packageName !in trackedPackages }

                _state.update { it.copy(deviceApps = deviceApps) }
            } catch (e: Exception) {
                logger.error("Failed to load device apps: ${e.message}")
                _events.send(AppsEvent.ShowError("Failed to load installed apps"))
            }
        }
    }

    private fun dismissLinkSheet() {
        _state.update {
            it.copy(
                showLinkSheet = false,
                linkStep = LinkStep.PickApp,
                deviceApps = emptyList(),
                deviceAppSearchQuery = "",
                selectedDeviceApp = null,
                repoUrl = "",
                repoValidationError = null,
                fetchedRepoInfo = null,
                isValidatingRepo = false,
            )
        }
    }

    private fun validateAndLinkRepo() {
        val selectedApp = _state.value.selectedDeviceApp ?: return
        val url = _state.value.repoUrl.trim()

        // Parse owner/repo from URL
        val (owner, repo) = parseGithubUrl(url) ?: run {
            _state.update { it.copy(repoValidationError = "Invalid GitHub URL. Use format: github.com/owner/repo") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isValidatingRepo = true, repoValidationError = null) }

            try {
                val repoInfo = appsRepository.fetchRepoInfo(owner, repo)
                if (repoInfo == null) {
                    _state.update {
                        it.copy(
                            isValidatingRepo = false,
                            repoValidationError = "Repository not found: $owner/$repo",
                        )
                    }
                    return@launch
                }

                appsRepository.linkAppToRepo(selectedApp, repoInfo)

                _state.update {
                    it.copy(
                        isValidatingRepo = false,
                        showLinkSheet = false,
                    )
                }

                _events.send(AppsEvent.AppLinkedSuccessfully(selectedApp.appName))
                _events.send(AppsEvent.ShowSuccess("${selectedApp.appName} linked to ${repoInfo.owner}/${repoInfo.name}"))
            } catch (e: RateLimitException) {
                _state.update {
                    it.copy(
                        isValidatingRepo = false,
                        repoValidationError = "GitHub API rate limit exceeded. Try again later.",
                    )
                }
            } catch (e: Exception) {
                logger.error("Failed to link app: ${e.message}")
                _state.update {
                    it.copy(
                        isValidatingRepo = false,
                        repoValidationError = "Failed to link: ${e.message}",
                    )
                }
            }
        }
    }

    private fun parseGithubUrl(input: String): Pair<String, String>? {
        val cleaned = input.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("github.com/")
            .removeSuffix("/")
            .split("?")[0]   // remove query params
            .split("#")[0]   // remove fragment

        val parts = cleaned.split("/")
        if (parts.size < 2) return null

        val owner = parts[0]
        val repo = parts[1]

        if (owner.isBlank() || repo.isBlank()) return null
        if (owner.length > 39 || repo.length > 100) return null

        return owner to repo
    }

    // ── Export/Import ─────────────────────────────────────────────

    private fun exportApps() {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true) }
            try {
                val json = appsRepository.exportApps()
                val fileName = "github-store-apps-${System.currentTimeMillis()}.json"
                shareManager.shareFile(fileName, json)
                _events.send(AppsEvent.ExportReady(json))
            } catch (e: Exception) {
                logger.error("Export failed: ${e.message}")
                _events.send(AppsEvent.ShowError("Export failed: ${e.message}"))
            } finally {
                _state.update { it.copy(isExporting = false) }
            }
        }
    }

    private fun importAppsFromFile() {
        shareManager.pickFile("application/json") { content ->
            if (content != null) {
                viewModelScope.launch {
                    importApps(content)
                }
            }
        }
    }

    private suspend fun importApps(json: String) {
        _state.update { it.copy(isImporting = true) }
        try {
            val result = appsRepository.importApps(json)
            _events.send(AppsEvent.ImportComplete(result))
            _events.send(
                AppsEvent.ShowSuccess(
                    "Imported ${result.imported} apps" +
                        if (result.skipped > 0) ", ${result.skipped} skipped" else "" +
                            if (result.failed > 0) ", ${result.failed} failed" else "",
                ),
            )
        } catch (e: Exception) {
            logger.error("Import failed: ${e.message}")
            _events.send(AppsEvent.ShowError("Import failed: ${e.message}"))
        } finally {
            _state.update { it.copy(isImporting = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()

        updateAllJob?.cancel()
        activeUpdates.values.forEach { it.cancel() }

        viewModelScope.launch {
            _state.value.apps.forEach { appItem ->
                if (appItem.updateState != UpdateState.Idle &&
                    appItem.updateState != UpdateState.Success
                ) {
                    appItem.installedApp.latestAssetName?.let { assetName ->
                        downloader.cancelDownload(assetName)
                    }
                }
            }
        }
    }
}
