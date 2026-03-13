package zed.rainxch.profile.presentation

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
import zed.rainxch.core.domain.repository.ThemesRepository
import zed.rainxch.core.domain.utils.BrowserHelper
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_save_proxy_settings
import zed.rainxch.githubstore.core.presentation.res.invalid_proxy_port
import zed.rainxch.githubstore.core.presentation.res.proxy_host_required
import zed.rainxch.profile.domain.repository.ProfileRepository
import zed.rainxch.profile.presentation.model.ProxyType

class ProfileViewModel(
    private val browserHelper: BrowserHelper,
    private val themesRepository: ThemesRepository,
    private val profileRepository: ProfileRepository,
    private val proxyRepository: ProxyRepository,
) : ViewModel() {
    private var userProfileJob: Job? = null

    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(ProfileState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadCurrentTheme()
                    collectIsUserLoggedIn()
                    loadUserProfile()
                    loadVersionName()
                    loadProxyConfig()
                    observeCacheSize()

                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ProfileState(),
            )

    private val _events = Channel<ProfileEvent>()
    val events = _events.receiveAsFlow()

    private fun observeCacheSize() {
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

    private fun collectIsUserLoggedIn() {
        viewModelScope.launch {
            profileRepository.isUserLoggedIn
                .collect { isLoggedIn ->
                    _state.update { it.copy(isUserLoggedIn = isLoggedIn) }
                    if (isLoggedIn) {
                        loadUserProfile()
                    } else {
                        _state.update { it.copy(userProfile = null) }
                    }
                }
        }
    }

    private fun loadUserProfile() {
        userProfileJob?.cancel()

        userProfileJob =
            viewModelScope.launch {
                profileRepository.getUser().collect { profile ->
                    _state.update { it.copy(userProfile = profile) }
                }
            }
    }

    private fun loadCurrentTheme() {
        viewModelScope.launch {
            themesRepository.getThemeColor().collect { theme ->
                _state.update {
                    it.copy(selectedThemeColor = theme)
                }
            }
        }

        viewModelScope.launch {
            themesRepository.getAmoledTheme().collect { isAmoled ->
                _state.update {
                    it.copy(isAmoledThemeEnabled = isAmoled)
                }
            }
        }

        viewModelScope.launch {
            themesRepository.getIsDarkTheme().collect { isDarkTheme ->
                _state.update {
                    it.copy(isDarkTheme = isDarkTheme)
                }
            }
        }

        viewModelScope.launch {
            themesRepository.getFontTheme().collect { fontTheme ->
                _state.update {
                    it.copy(selectedFontTheme = fontTheme)
                }
            }
        }

        viewModelScope.launch {
            themesRepository.getAutoDetectClipboardLinks().collect { enabled ->
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

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.OnHelpClick -> {
                browserHelper.openUrl(
                    url = "https://github.com/OpenHub-Store/GitHub-Store/issues",
                )
            }

            ProfileAction.OnClearCacheClick -> {
                viewModelScope.launch {
                    runCatching {
                        profileRepository.clearCache()
                    }.onSuccess {
                        observeCacheSize()
                        _events.send(ProfileEvent.OnCacheCleared)
                    }.onFailure { error ->
                        _events.send(
                            ProfileEvent.OnCacheClearError(
                                error.message ?: "Failed to clear cache",
                            ),
                        )
                    }
                }
            }

            is ProfileAction.OnThemeColorSelected -> {
                viewModelScope.launch {
                    themesRepository.setThemeColor(action.themeColor)
                }
            }

            is ProfileAction.OnAmoledThemeToggled -> {
                viewModelScope.launch {
                    themesRepository.setAmoledTheme(action.enabled)
                }
            }

            ProfileAction.OnLogoutClick -> {
                _state.update {
                    it.copy(
                        isLogoutDialogVisible = true,
                    )
                }
            }

            ProfileAction.OnLogoutConfirmClick -> {
                viewModelScope.launch {
                    runCatching {
                        profileRepository.logout()
                    }.onSuccess {
                        _state.update { it.copy(isLogoutDialogVisible = false, userProfile = null) }
                        _events.send(ProfileEvent.OnLogoutSuccessful)
                    }.onFailure { error ->
                        _state.update { it.copy(isLogoutDialogVisible = false) }
                        error.message?.let {
                            _events.send(ProfileEvent.OnLogoutError(it))
                        }
                    }
                }
            }

            ProfileAction.OnLogoutDismiss -> {
                _state.update {
                    it.copy(
                        isLogoutDialogVisible = false,
                    )
                }
            }

            ProfileAction.OnNavigateBackClick -> {
                // Handed in composable
            }

            ProfileAction.OnLoginClick -> {
                // Handed in composable
            }

            ProfileAction.OnFavouriteReposClick -> {
                // Handed in composable
            }

            ProfileAction.OnStarredReposClick -> {
                // Handed in composable
            }

            is ProfileAction.OnRepositoriesClick -> {
                // Handed in composable
            }

            ProfileAction.OnSponsorClick -> {
                // Handed in composable
            }

            is ProfileAction.OnFontThemeSelected -> {
                viewModelScope.launch {
                    themesRepository.setFontTheme(action.fontTheme)
                }
            }

            is ProfileAction.OnDarkThemeChange -> {
                viewModelScope.launch {
                    themesRepository.setDarkTheme(action.isDarkTheme)
                }
            }

            is ProfileAction.OnProxyTypeSelected -> {
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
                            _events.send(ProfileEvent.OnProxySaved)
                        }.onFailure { error ->
                            _events.send(
                                ProfileEvent.OnProxySaveError(
                                    error.message ?: getString(Res.string.failed_to_save_proxy_settings),
                                ),
                            )
                        }
                    }
                }
            }

            is ProfileAction.OnProxyHostChanged -> {
                _state.update { it.copy(proxyHost = action.host) }
            }

            is ProfileAction.OnProxyPortChanged -> {
                _state.update { it.copy(proxyPort = action.port) }
            }

            is ProfileAction.OnProxyUsernameChanged -> {
                _state.update { it.copy(proxyUsername = action.username) }
            }

            is ProfileAction.OnProxyPasswordChanged -> {
                _state.update { it.copy(proxyPassword = action.password) }
            }

            ProfileAction.OnProxyPasswordVisibilityToggle -> {
                _state.update { it.copy(isProxyPasswordVisible = !it.isProxyPasswordVisible) }
            }

            is ProfileAction.OnAutoDetectClipboardToggled -> {
                viewModelScope.launch {
                    themesRepository.setAutoDetectClipboardLinks(action.enabled)
                }
            }

            ProfileAction.OnProxySave -> {
                val currentState = _state.value
                val port =
                    currentState.proxyPort
                        .toIntOrNull()
                        ?.takeIf { it in 1..65535 }
                        ?: run {
                            viewModelScope.launch {
                                _events.send(ProfileEvent.OnProxySaveError(getString(Res.string.invalid_proxy_port)))
                            }
                            return
                        }
                val host =
                    currentState.proxyHost.trim().takeIf { it.isNotBlank() } ?: run {
                        viewModelScope.launch {
                            _events.send(ProfileEvent.OnProxySaveError(getString(Res.string.proxy_host_required)))
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
                        _events.send(ProfileEvent.OnProxySaved)
                    }.onFailure { error ->
                        _events.send(
                            ProfileEvent.OnProxySaveError(
                                error.message ?: getString(Res.string.failed_to_save_proxy_settings),
                            ),
                        )
                    }
                }
            }
        }
    }
}
