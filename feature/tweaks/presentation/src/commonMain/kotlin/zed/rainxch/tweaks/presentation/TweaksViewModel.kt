package zed.rainxch.tweaks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.model.ProxyConfig
import zed.rainxch.core.domain.repository.ProxyRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.InstallerStatusProvider
import zed.rainxch.core.domain.system.UpdateScheduleManager
import zed.rainxch.core.domain.utils.BrowserHelper
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_save_proxy_settings
import zed.rainxch.githubstore.core.presentation.res.invalid_proxy_port
import zed.rainxch.githubstore.core.presentation.res.proxy_host_required
import zed.rainxch.profile.domain.repository.ProfileRepository
import zed.rainxch.tweaks.presentation.model.ProxyType

class TweaksViewModel(
    private val browserHelper: BrowserHelper,
    private val tweaksRepository: TweaksRepository,
    private val profileRepository: ProfileRepository,
    private val installerStatusProvider: InstallerStatusProvider,
    private val proxyRepository: ProxyRepository,
    private val updateScheduleManager: UpdateScheduleManager,
    private val seenReposRepository: SeenReposRepository,
) : ViewModel() {
    private var hasLoadedInitialData = false
    private var cacheSizeJob: Job? = null

    private val _state = MutableStateFlow(TweaksState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadCurrentTheme()
                    loadVersionName()
                    loadProxyConfig()
                    loadInstallerPreference()
                    loadAutoUpdatePreference()
                    loadUpdateCheckInterval()
                    loadIncludePreReleases()
                    loadLiquidGlassEnabled()
                    loadHideSeenEnabled()
                    loadScrollbarEnabled()

                    observeShizukuStatus()

                    hasLoadedInitialData = true
                }
                refreshCacheSize()
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = TweaksState(),
            )

    private val _events = Channel<TweaksEvent>()
    val events = _events.receiveAsFlow()

    private fun refreshCacheSize() {
        if (cacheSizeJob?.isActive == true) return
        cacheSizeJob =
            viewModelScope.launch {
                profileRepository.observeCacheSize().collect { sizeBytes ->
                    _state.update {
                        it.copy(cacheSize = formatCacheSize(sizeBytes))
                    }
                }
            }
    }

    private fun formatCacheSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.lastIndex) {
            size /= 1024
            unitIndex++
        }
        return if (size == size.toLong().toDouble()) {
            "${size.toLong()} ${units[unitIndex]}"
        } else {
            "${"%.1f".format(size)} ${units[unitIndex]}"
        }
    }

    private fun loadVersionName() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    versionName = profileRepository.getVersionName(),
                )
            }
        }
    }

    private fun loadCurrentTheme() {
        viewModelScope.launch {
            tweaksRepository.getThemeColor().collect { theme ->
                _state.update {
                    it.copy(selectedThemeColor = theme)
                }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getAmoledTheme().collect { isAmoled ->
                _state.update {
                    it.copy(isAmoledThemeEnabled = isAmoled)
                }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getIsDarkTheme().collect { isDarkTheme ->
                _state.update {
                    it.copy(isDarkTheme = isDarkTheme)
                }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getFontTheme().collect { fontTheme ->
                _state.update {
                    it.copy(selectedFontTheme = fontTheme)
                }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getAutoDetectClipboardLinks().collect { enabled ->
                _state.update {
                    it.copy(autoDetectClipboardLinks = enabled)
                }
            }
        }
    }

    private fun loadProxyConfig() {
        viewModelScope.launch {
            proxyRepository.getProxyConfig().collect { config ->
                _state.update {
                    it.copy(
                        proxyType = ProxyType.fromConfig(config),
                        proxyHost =
                            when (config) {
                                is ProxyConfig.Http -> config.host
                                is ProxyConfig.Socks -> config.host
                                else -> it.proxyHost
                            },
                        proxyPort =
                            when (config) {
                                is ProxyConfig.Http -> config.port.toString()
                                is ProxyConfig.Socks -> config.port.toString()
                                else -> it.proxyPort
                            },
                        proxyUsername =
                            when (config) {
                                is ProxyConfig.Http -> config.username ?: ""
                                is ProxyConfig.Socks -> config.username ?: ""
                                else -> it.proxyUsername
                            },
                        proxyPassword =
                            when (config) {
                                is ProxyConfig.Http -> config.password ?: ""
                                is ProxyConfig.Socks -> config.password ?: ""
                                else -> it.proxyPassword
                            },
                    )
                }
            }
        }
    }

    private fun loadInstallerPreference() {
        viewModelScope.launch {
            tweaksRepository.getInstallerType().collect { type ->
                _state.update {
                    it.copy(installerType = type)
                }
            }
        }
    }

    private fun observeShizukuStatus() {
        viewModelScope.launch {
            installerStatusProvider.shizukuAvailability.collect { availability ->
                _state.update {
                    it.copy(shizukuAvailability = availability)
                }
            }
        }
    }

    private fun loadAutoUpdatePreference() {
        viewModelScope.launch {
            tweaksRepository.getAutoUpdateEnabled().collect { enabled ->
                _state.update {
                    it.copy(autoUpdateEnabled = enabled)
                }
            }
        }
    }

    private fun loadUpdateCheckInterval() {
        viewModelScope.launch {
            tweaksRepository.getUpdateCheckInterval().collect { hours ->
                _state.update {
                    it.copy(updateCheckIntervalHours = hours)
                }
            }
        }
    }

    private fun loadLiquidGlassEnabled() {
        viewModelScope.launch {
            tweaksRepository.getLiquidGlassEnabled().collect { enabled ->
                _state.update {
                    it.copy(isLiquidGlassEnabled = enabled)
                }
            }
        }
    }

    private fun loadHideSeenEnabled() {
        viewModelScope.launch {
            tweaksRepository.getHideSeenEnabled().collect { enabled ->
                _state.update {
                    it.copy(isHideSeenEnabled = enabled)
                }
            }
        }
    }

    private fun loadScrollbarEnabled() {
        viewModelScope.launch {
            tweaksRepository.getScrollbarEnabled().collect { enabled ->
                _state.update {
                    it.copy(isScrollbarEnabled = enabled)
                }
            }
        }
    }

    private fun loadIncludePreReleases() {
        viewModelScope.launch {
            tweaksRepository.getIncludePreReleases().collect { enabled ->
                _state.update {
                    it.copy(includePreReleases = enabled)
                }
            }
        }
    }

    fun onAction(action: TweaksAction) {
        when (action) {
            TweaksAction.OnNavigateBackClick -> {
                // Handled in composable
            }

            is TweaksAction.OnThemeColorSelected -> {
                viewModelScope.launch {
                    tweaksRepository.setThemeColor(action.themeColor)
                }
            }

            is TweaksAction.OnAmoledThemeToggled -> {
                viewModelScope.launch {
                    tweaksRepository.setAmoledTheme(action.enabled)
                }
            }

            is TweaksAction.OnDarkThemeChange -> {
                viewModelScope.launch {
                    tweaksRepository.setDarkTheme(action.isDarkTheme)
                }
            }

            is TweaksAction.OnFontThemeSelected -> {
                viewModelScope.launch {
                    tweaksRepository.setFontTheme(action.fontTheme)
                }
            }

            is TweaksAction.OnLiquidGlassEnabledChange -> {
                viewModelScope.launch {
                    tweaksRepository.setLiquidGlassEnabled(action.enabled)
                }
            }

            is TweaksAction.OnScrollbarToggled -> {
                viewModelScope.launch {
                    tweaksRepository.setScrollbarEnabled(action.enabled)
                }
            }

            is TweaksAction.OnProxyTypeSelected -> {
                _state.update { it.copy(proxyType = action.type) }
                if (action.type == ProxyType.NONE || action.type == ProxyType.SYSTEM) {
                    val config =
                        when (action.type) {
                            ProxyType.NONE -> ProxyConfig.None
                            ProxyType.SYSTEM -> ProxyConfig.System
                            else -> return
                        }
                    viewModelScope.launch {
                        runCatching {
                            proxyRepository.setProxyConfig(config)
                        }.onSuccess {
                            _events.send(TweaksEvent.OnProxySaved)
                        }.onFailure { error ->
                            _events.send(
                                TweaksEvent.OnProxySaveError(
                                    error.message ?: getString(Res.string.failed_to_save_proxy_settings),
                                ),
                            )
                        }
                    }
                }
            }

            is TweaksAction.OnProxyHostChanged -> {
                _state.update { it.copy(proxyHost = action.host) }
            }

            is TweaksAction.OnProxyPortChanged -> {
                _state.update { it.copy(proxyPort = action.port) }
            }

            is TweaksAction.OnProxyUsernameChanged -> {
                _state.update { it.copy(proxyUsername = action.username) }
            }

            is TweaksAction.OnProxyPasswordChanged -> {
                _state.update { it.copy(proxyPassword = action.password) }
            }

            TweaksAction.OnProxyPasswordVisibilityToggle -> {
                _state.update { it.copy(isProxyPasswordVisible = !it.isProxyPasswordVisible) }
            }

            TweaksAction.OnProxySave -> {
                val currentState = _state.value
                val port =
                    currentState.proxyPort
                        .toIntOrNull()
                        ?.takeIf { it in 1..65535 }
                        ?: run {
                            viewModelScope.launch {
                                _events.send(TweaksEvent.OnProxySaveError(getString(Res.string.invalid_proxy_port)))
                            }
                            return
                        }
                val host =
                    currentState.proxyHost.trim().takeIf { it.isNotBlank() } ?: run {
                        viewModelScope.launch {
                            _events.send(TweaksEvent.OnProxySaveError(getString(Res.string.proxy_host_required)))
                        }
                        return
                    }

                val username = currentState.proxyUsername.takeIf { it.isNotBlank() }
                val password = currentState.proxyPassword.takeIf { it.isNotBlank() }

                val config =
                    when (currentState.proxyType) {
                        ProxyType.HTTP -> ProxyConfig.Http(host, port, username, password)
                        ProxyType.SOCKS -> ProxyConfig.Socks(host, port, username, password)
                        ProxyType.NONE -> ProxyConfig.None
                        ProxyType.SYSTEM -> ProxyConfig.System
                    }

                viewModelScope.launch {
                    runCatching {
                        proxyRepository.setProxyConfig(config)
                    }.onSuccess {
                        _events.send(TweaksEvent.OnProxySaved)
                    }.onFailure { error ->
                        _events.send(
                            TweaksEvent.OnProxySaveError(
                                error.message ?: getString(Res.string.failed_to_save_proxy_settings),
                            ),
                        )
                    }
                }
            }

            is TweaksAction.OnInstallerTypeSelected -> {
                viewModelScope.launch {
                    tweaksRepository.setInstallerType(action.type)
                }
            }

            TweaksAction.OnRequestShizukuPermission -> {
                installerStatusProvider.requestShizukuPermission()
            }

            is TweaksAction.OnAutoUpdateToggled -> {
                viewModelScope.launch {
                    tweaksRepository.setAutoUpdateEnabled(action.enabled)
                }
            }

            is TweaksAction.OnUpdateCheckIntervalChanged -> {
                viewModelScope.launch {
                    tweaksRepository.setUpdateCheckInterval(action.hours)
                    updateScheduleManager.reschedule(action.hours)
                }
            }

            is TweaksAction.OnIncludePreReleasesToggled -> {
                viewModelScope.launch {
                    tweaksRepository.setIncludePreReleases(action.enabled)
                }
            }

            is TweaksAction.OnAutoDetectClipboardToggled -> {
                viewModelScope.launch {
                    tweaksRepository.setAutoDetectClipboardLinks(action.enabled)
                }
            }

            is TweaksAction.OnHideSeenToggled -> {
                viewModelScope.launch {
                    tweaksRepository.setHideSeenEnabled(action.enabled)
                }
            }

            TweaksAction.OnClearSeenRepos -> {
                viewModelScope.launch {
                    seenReposRepository.clearAll()
                    _events.send(TweaksEvent.OnSeenHistoryCleared)
                }
            }

            TweaksAction.OnRefreshCacheSize -> {
                refreshCacheSize()
            }

            TweaksAction.OnClearCacheClick -> {
                _state.update { it.copy(isClearDownloadsDialogVisible = true) }
            }

            TweaksAction.OnClearDownloadsConfirm -> {
                _state.update { it.copy(isClearDownloadsDialogVisible = false) }
                viewModelScope.launch {
                    runCatching {
                        profileRepository.clearCache()
                    }.onSuccess {
                        cacheSizeJob?.cancel()
                        cacheSizeJob = null
                        refreshCacheSize()
                        _events.send(TweaksEvent.OnCacheCleared)
                    }.onFailure { error ->
                        _events.send(
                            TweaksEvent.OnCacheClearError(
                                error.message ?: "Failed to clear downloads",
                            ),
                        )
                    }
                }
            }

            TweaksAction.OnClearDownloadsDismiss -> {
                _state.update { it.copy(isClearDownloadsDialogVisible = false) }
            }

            TweaksAction.OnHelpClick -> {
                browserHelper.openUrl(
                    url = "https://github.com/OpenHub-Store/GitHub-Store/issues",
                )
            }
        }
    }
}
