package zed.rainxch.tweaks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.model.settings.ProxyScope
import zed.rainxch.core.domain.model.settings.TranslationProvider
import zed.rainxch.core.domain.network.ProxyTestOutcome
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.network.ProxyTester
import zed.rainxch.core.domain.repository.CacheRepository
import zed.rainxch.core.domain.repository.ProxyRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.AggressiveOemDetector
import zed.rainxch.core.domain.system.AppVersionInfo
import zed.rainxch.core.domain.system.InstallerStatusProvider
import zed.rainxch.core.domain.system.UpdateScheduleManager
import zed.rainxch.tweaks.presentation.model.ProxyScopeFormState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_save_proxy_settings
import zed.rainxch.githubstore.core.presentation.res.invalid_proxy_port
import zed.rainxch.githubstore.core.presentation.res.proxy_host_invalid
import zed.rainxch.githubstore.core.presentation.res.proxy_host_required
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_auth_required
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_dns
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_status
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_timeout
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_unknown
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_unreachable
import zed.rainxch.githubstore.core.presentation.res.tweaks_clear_downloads_failed
import zed.rainxch.githubstore.core.presentation.res.tweaks_custom_forge_invalid_hostname
import zed.rainxch.githubstore.core.presentation.res.tweaks_custom_forge_remove_failed
import zed.rainxch.githubstore.core.presentation.res.tweaks_custom_forge_save_failed
import zed.rainxch.tweaks.presentation.model.ProxyType
import zed.rainxch.tweaks.presentation.connection.parseProxyUrl
import zed.rainxch.tweaks.presentation.utils.TweaksDeepLinkBus
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.model.appearance.FontTheme
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.domain.model.installation.InstallerAttribution
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.AppTheme
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.model.installation.PresetKey
import zed.rainxch.tweaks.presentation.model.ProxyEntryBackup
import zed.rainxch.tweaks.presentation.model.SettingsBackup
import kotlin.time.Duration.Companion.milliseconds

class TweaksViewModel(
    private val tweaksRepository: TweaksRepository,
    private val appVersionInfo: AppVersionInfo,
    private val installerStatusProvider: InstallerStatusProvider,
    private val proxyRepository: ProxyRepository,
    private val proxyTester: ProxyTester,
    private val updateScheduleManager: UpdateScheduleManager,
    private val seenReposRepository: SeenReposRepository,
    private val logger: KomiStoreLogger,
    private val aggressiveOemDetector: AggressiveOemDetector,
    private val cacheRepository: CacheRepository,
    private val shareManager: ShareManager,
) : ViewModel() {
    private companion object {
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        private const val BATTERY_OPT_PREF_READ_TIMEOUT_MS: Long = 1_000

        private val IPV4_PATTERN =
            Regex(
                "^(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)" +
                        "(\\.(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)){3}$",
            )

        private val IPV6_PATTERN = Regex("^[0-9A-Fa-f:]+$")

        private val HOSTNAME_PATTERN =
            Regex(
                "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?" +
                        "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$",
            )
    }

    private var hasLoadedInitialData = false
    private var cacheSizeJob: Job? = null

    private val _state = MutableStateFlow(TweaksState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                loadCurrentTheme()
                loadVersionName()
                loadProxyConfig()
                loadInstallerPreference()
                loadAutoUpdatePreference()
                loadUpdateCheckInterval()
                loadUpdateCheckEnabled()
                loadIncludePreReleases()
                loadHideSeenEnabled()
                loadScrollbarEnabled()
                loadContentWidth()
                loadTranslationSettings()
                loadAppLanguage()
                loadAutoTranslate()

                observeShizukuStatus()
                observeDhizukuStatus()
                observeRootStatus()
                observeInstallerAttribution()
                observeMasterProxyConfig()
                observeUseMasterFlags()
                observeFeedbackDeepLink()

                hasLoadedInitialData = true
            }
            refreshCacheSize()

            evaluateBatteryOptimizationCard()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = TweaksState(),
        )

    private val _events = Channel<TweaksEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private fun observeFeedbackDeepLink() {
        viewModelScope.launch {
            TweaksDeepLinkBus.openFeedbackRequests.collect {
                _state.update { it.copy(isFeedbackSheetVisible = true) }
                TweaksDeepLinkBus.consume()
            }
        }
    }

    private fun refreshCacheSize() {
        if (cacheSizeJob?.isActive == true) return
        cacheSizeJob = viewModelScope.launch {
            cacheRepository.observeCacheSize().collect { sizeBytes ->
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
        _state.update { it.copy(versionName = appVersionInfo.versionName) }
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
            tweaksRepository.getPersonality().collect { personality ->
                _state.update { it.copy(selectedPersonality = personality) }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getAccentId().collect { accentId ->
                _state.update { it.copy(selectedAccent = accentId) }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getMangaPaper().collect { paper ->
                _state.update { it.copy(mangaPaper = paper) }
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

        viewModelScope.launch {
            tweaksRepository.getCustomForgeHosts().collect { hosts ->
                _state.update { it.copy(customForgeHosts = hosts.toImmutableSet()) }
            }
        }
    }

    private fun loadProxyConfig() {

        ProxyScope.entries.forEach { scope ->
            viewModelScope.launch {
                proxyRepository.getProxyConfig(scope).collect { config ->
                    _state.update { state ->
                        val existing = state.formFor(scope)
                        if (existing.isDraftDirty) return@update state
                        val populated =
                            existing.copy(
                                type = ProxyType.fromConfig(config),
                                host =
                                    when (config) {
                                        is ProxyConfig.Http -> config.host
                                        is ProxyConfig.Socks -> config.host
                                        else -> existing.host
                                    },
                                port =
                                    when (config) {
                                        is ProxyConfig.Http -> config.port.toString()
                                        is ProxyConfig.Socks -> config.port.toString()
                                        else -> existing.port
                                    },
                                username =
                                    when (config) {
                                        is ProxyConfig.Http -> config.username.orEmpty()
                                        is ProxyConfig.Socks -> config.username.orEmpty()
                                        else -> existing.username
                                    },
                                password =
                                    when (config) {
                                        is ProxyConfig.Http -> config.password.orEmpty()
                                        is ProxyConfig.Socks -> config.password.orEmpty()
                                        else -> existing.password
                                    },
                            )
                        state.copy(
                            proxyForms = (state.proxyForms + (scope to populated)).toImmutableMap(),
                        )
                    }
                }
            }
        }
    }

    private fun mutateForm(
        scope: ProxyScope,
        block: (ProxyScopeFormState) -> ProxyScopeFormState,
    ) {
        _state.update { state ->
            val updated = block(state.formFor(scope)).copy(isDraftDirty = true)
            state.copy(
                proxyForms = (state.proxyForms + (scope to updated)).toImmutableMap(),
            )
        }
    }

    private fun mutateFormUi(
        scope: ProxyScope,
        block: (ProxyScopeFormState) -> ProxyScopeFormState,
    ) {
        _state.update { state ->
            state.copy(
                proxyForms = (state.proxyForms + (scope to block(state.formFor(scope)))).toImmutableMap(),
            )
        }
    }

    private fun clearDirty(scope: ProxyScope) {
        _state.update { state ->
            val form = state.formFor(scope)
            if (!form.isDraftDirty) return@update state
            state.copy(
                proxyForms = (state.proxyForms + (scope to form.copy(isDraftDirty = false))).toImmutableMap(),
            )
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

    private fun observeDhizukuStatus() {
        viewModelScope.launch {
            installerStatusProvider.dhizukuAvailability.collect { availability ->
                _state.update {
                    it.copy(dhizukuAvailability = availability)
                }
            }
        }
    }

    private fun observeRootStatus() {
        viewModelScope.launch {
            installerStatusProvider.rootAvailability.collect { availability ->
                _state.update {
                    it.copy(rootAvailability = availability)
                }
            }
        }
    }

    private fun persistInstallerAttribution(
        attribution: zed.rainxch.core.domain.model.installation.InstallerAttribution,
    ) {
        viewModelScope.launch {
            runCatching {
                tweaksRepository.setInstallerAttribution(attribution)
            }.onSuccess {
                _state.update {
                    it.copy(
                        installerAttributionCustomExpanded = false,
                        installerAttributionCustomError = null,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logger.error("TweaksViewModel: failed to persist installer attribution", error)
                _state.update {
                    it.copy(installerAttributionCustomError = "write_failed")
                }
            }
        }
    }

    private fun observeInstallerAttribution() {
        viewModelScope.launch {
            tweaksRepository.getInstallerAttribution()
                .catch { e ->
                    logger.error("TweaksViewModel: installer attribution flow error", e)
                }
                .collect { attribution ->
                    _state.update { current ->
                        val isCustom =
                            attribution is zed.rainxch.core.domain.model.installation.InstallerAttribution.Custom
                        val customDraft =
                            (attribution as? zed.rainxch.core.domain.model.installation.InstallerAttribution.Custom)?.packageName
                                ?: current.installerAttributionCustomDraft
                        current.copy(
                            installerAttribution = attribution,
                            installerAttributionCustomDraft = customDraft,
                            installerAttributionCustomExpanded = if (isCustom) {
                                current.installerAttributionCustomExpanded
                            } else {
                                false
                            },
                        )
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

    private fun loadUpdateCheckEnabled() {
        viewModelScope.launch {
            tweaksRepository.getUpdateCheckEnabled().collect { enabled ->
                _state.update {
                    it.copy(updateCheckEnabled = enabled)
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

    private fun loadContentWidth() {
        viewModelScope.launch {
            tweaksRepository.getContentWidth().collect { width ->
                _state.update { it.copy(contentWidth = width) }
            }
        }
    }

    private fun loadTranslationSettings() {
        viewModelScope.launch {
            tweaksRepository.getTranslationProvider().collect { provider ->
                _state.update { it.copy(translationProvider = provider) }
            }
        }
        viewModelScope.launch {
            tweaksRepository.getYoudaoAppKey().collect { appKey ->
                _state.update { it.copy(youdaoAppKey = appKey) }
            }
        }
        viewModelScope.launch {
            tweaksRepository.getYoudaoAppSecret().collect { appSecret ->
                _state.update { it.copy(youdaoAppSecret = appSecret) }
            }
        }
        viewModelScope.launch {
            tweaksRepository.getLibreTranslateBaseUrl().collect { url ->
                _state.update { it.copy(libreTranslateBaseUrl = url) }
            }
        }
        viewModelScope.launch {
            tweaksRepository.getLibreTranslateApiKey().collect { apiKey ->
                _state.update { it.copy(libreTranslateApiKey = apiKey) }
            }
        }
        viewModelScope.launch {
            tweaksRepository.getDeeplAuthKey().collect { authKey ->
                _state.update { it.copy(deeplAuthKey = authKey) }
            }
        }
        viewModelScope.launch {
            tweaksRepository.getMicrosoftTranslatorKey().collect { key ->
                _state.update { it.copy(microsoftTranslatorKey = key) }
            }
        }
        viewModelScope.launch {
            tweaksRepository.getMicrosoftTranslatorRegion().collect { region ->
                _state.update { it.copy(microsoftTranslatorRegion = region) }
            }
        }
    }

    private fun evaluateBatteryOptimizationCard() {
        viewModelScope.launch {
            val dismissed =
                runCatching {
                    withTimeoutOrNull(BATTERY_OPT_PREF_READ_TIMEOUT_MS.milliseconds) {
                        tweaksRepository.getBatteryOptimizationPromptDismissed().firstOrNull()
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    logger.error(
                        "TweaksViewModel: failed to read battery-opt dismissed flag",
                        error,
                    )
                }.getOrNull() ?: false
            val show =
                aggressiveOemDetector.isAggressiveOem() &&
                        !aggressiveOemDetector.isBatteryOptimizationIgnored() &&
                        !dismissed
            _state.update { it.copy(showBatteryOptimizationCard = show) }
        }
    }

    private fun loadAppLanguage() {
        viewModelScope.launch {
            tweaksRepository.getAppLanguage().collect { tag ->
                _state.update { it.copy(selectedAppLanguage = tag) }
            }
        }
    }

    private fun loadAutoTranslate() {
        viewModelScope.launch {
            tweaksRepository.getAutoTranslateEnabled().collect { enabled ->
                _state.update { it.copy(autoTranslateEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            tweaksRepository.getAutoTranslateTargetLang().collect { tag ->
                _state.update { it.copy(autoTranslateTargetLang = tag) }
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

            TweaksAction.OnSkippedUpdatesClick -> {
                // Handled in composable (navigates to the skipped-updates screen).
            }

            TweaksAction.OnHiddenRepositoriesClick -> {
                // Handled in composable (navigates to the hidden-repositories screen).
            }

            is TweaksAction.OnPersonalitySelected -> {
                viewModelScope.launch {
                    tweaksRepository.setPersonality(action.personality)
                }
            }

            is TweaksAction.OnAccentSelected -> {
                viewModelScope.launch {
                    tweaksRepository.setAccentId(action.accent)
                }
            }

            is TweaksAction.OnMangaPaperSelected -> {
                viewModelScope.launch {
                    tweaksRepository.setMangaPaper(action.paper)
                }
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

            is TweaksAction.OnScrollbarToggled -> {
                viewModelScope.launch {
                    tweaksRepository.setScrollbarEnabled(action.enabled)
                }
            }

            is TweaksAction.OnContentWidthSelected -> {
                viewModelScope.launch {
                    tweaksRepository.setContentWidth(action.width)
                }
            }

            is TweaksAction.OnProxyTypeSelected -> {
                mutateForm(action.scope) { it.copy(type = action.type) }

                if (action.type == ProxyType.NONE || action.type == ProxyType.SYSTEM) {
                    val config =
                        if (action.type == ProxyType.NONE) {
                            ProxyConfig.None
                        } else {
                            ProxyConfig.System
                        }
                    viewModelScope.launch {
                        runCatching {
                            proxyRepository.setProxyConfig(action.scope, config)
                        }.onSuccess {

                            clearDirty(action.scope)
                            _events.send(TweaksEvent.OnProxySaved)
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                            _events.send(
                                TweaksEvent.OnProxySaveError(
                                    error.message
                                        ?: getString(Res.string.failed_to_save_proxy_settings),
                                ),
                            )
                        }
                    }
                }
            }

            is TweaksAction.OnProxyHostChanged -> {
                mutateForm(action.scope) { it.copy(host = action.host) }
            }

            is TweaksAction.OnProxyPortChanged -> {
                mutateForm(action.scope) { it.copy(port = action.port) }
            }

            is TweaksAction.OnProxyUsernameChanged -> {
                mutateForm(action.scope) { it.copy(username = action.username) }
            }

            is TweaksAction.OnProxyPasswordChanged -> {
                mutateForm(action.scope) { it.copy(password = action.password) }
            }

            is TweaksAction.OnProxyPasswordVisibilityToggle -> {
                mutateFormUi(action.scope) {
                    it.copy(isPasswordVisible = !it.isPasswordVisible)
                }
            }

            is TweaksAction.OnProxySave -> {
                val form = _state.value.formFor(action.scope)

                val config: ProxyConfig =
                    when (form.type) {
                        ProxyType.NONE -> ProxyConfig.None
                        ProxyType.SYSTEM -> ProxyConfig.System
                        ProxyType.HTTP, ProxyType.SOCKS -> {
                            val port =
                                form.port
                                    .toIntOrNull()
                                    ?.takeIf { it in 1..65535 }
                                    ?: run {
                                        viewModelScope.launch {
                                            _events.send(
                                                TweaksEvent.OnProxySaveError(
                                                    getString(Res.string.invalid_proxy_port),
                                                ),
                                            )
                                        }
                                        return
                                    }
                            val host =
                                form.host.trim().takeIf { isValidProxyHost(it) }
                                    ?: run {
                                        val isBlank = form.host.isBlank()
                                        viewModelScope.launch {
                                            val msg =
                                                if (isBlank) {
                                                    getString(Res.string.proxy_host_required)
                                                } else {
                                                    getString(Res.string.proxy_host_invalid)
                                                }
                                            _events.send(TweaksEvent.OnProxySaveError(msg))
                                        }
                                        return
                                    }
                            val username = form.username.takeIf { it.isNotBlank() }
                            val password = form.password.takeIf { it.isNotBlank() }
                            if (form.type == ProxyType.HTTP) {
                                ProxyConfig.Http(host, port, username, password)
                            } else {
                                ProxyConfig.Socks(host, port, username, password)
                            }
                        }
                    }

                viewModelScope.launch {
                    runCatching {
                        proxyRepository.setProxyConfig(action.scope, config)
                    }.onSuccess {
                        clearDirty(action.scope)
                        _events.send(TweaksEvent.OnProxySaved)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        _events.send(
                            TweaksEvent.OnProxySaveError(
                                error.message
                                    ?: getString(Res.string.failed_to_save_proxy_settings),
                            ),
                        )
                    }
                }
            }

            is TweaksAction.OnProxyTest -> {
                val form = _state.value.formFor(action.scope)
                if (form.isTestInProgress) return
                val config = buildProxyConfigForTest(action.scope) ?: return
                mutateFormUi(action.scope) { it.copy(isTestInProgress = true) }
                viewModelScope.launch {
                    val outcome: ProxyTestOutcome =
                        try {
                            proxyTester.test(config)
                        } catch (e: CancellationException) {

                            throw e
                        } catch (e: Exception) {
                            ProxyTestOutcome.Failure.Unknown(e.message)
                        } finally {
                            mutateFormUi(action.scope) { it.copy(isTestInProgress = false) }
                        }
                    _events.send(outcome.toEvent())
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

            TweaksAction.OnRequestDhizukuPermission -> {
                installerStatusProvider.requestDhizukuPermission()
            }

            TweaksAction.OnRequestRootPermission -> {
                installerStatusProvider.requestRootPermission()
            }

            TweaksAction.OnInstallerAttributionSystemDefault -> {
                persistInstallerAttribution(
                    zed.rainxch.core.domain.model.installation.InstallerAttribution.SystemDefault,
                )
            }

            is TweaksAction.OnInstallerAttributionPresetSelected -> {
                persistInstallerAttribution(
                    zed.rainxch.core.domain.model.installation.InstallerAttribution.Preset(action.key),
                )
            }

            TweaksAction.OnInstallerAttributionCustomToggleExpanded -> {
                _state.update {
                    it.copy(
                        installerAttributionCustomExpanded = !it.installerAttributionCustomExpanded,
                        installerAttributionCustomError = null,
                    )
                }
            }

            is TweaksAction.OnInstallerAttributionCustomChanged -> {
                _state.update {
                    it.copy(
                        installerAttributionCustomDraft = action.value,
                        installerAttributionCustomError = null,
                    )
                }
            }

            TweaksAction.OnInstallerAttributionCustomSave -> {
                val draft = _state.value.installerAttributionCustomDraft.trim()
                if (!zed.rainxch.core.domain.model.installation.InstallerAttributionDefaults.isValidPackageName(
                        draft
                    )
                ) {
                    _state.update {
                        it.copy(installerAttributionCustomError = "invalid")
                    }
                } else {
                    viewModelScope.launch {
                        runCatching {
                            tweaksRepository.setInstallerAttribution(
                                zed.rainxch.core.domain.model.installation.InstallerAttribution.Custom(
                                    draft
                                ),
                            )
                        }.onSuccess {
                            _state.update { it.copy(installerAttributionCustomError = null) }
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                            logger.error(
                                "TweaksViewModel: failed to persist installer attribution",
                                error
                            )
                            _state.update { it.copy(installerAttributionCustomError = "write_failed") }
                        }
                    }
                }
            }

            is TweaksAction.OnAutoUpdateToggled -> {
                viewModelScope.launch {
                    tweaksRepository.setAutoUpdateEnabled(action.enabled)
                }
            }

            is TweaksAction.OnUpdateCheckIntervalChanged -> {
                viewModelScope.launch {
                    tweaksRepository.setUpdateCheckInterval(action.hours)
                    if (_state.value.updateCheckEnabled) {
                        updateScheduleManager.reschedule(action.hours)
                    }
                }
            }

            is TweaksAction.OnUpdateCheckEnabledToggled -> {
                viewModelScope.launch {
                    tweaksRepository.setUpdateCheckEnabled(action.enabled)
                    if (action.enabled) {
                        updateScheduleManager.reschedule(_state.value.updateCheckIntervalHours)
                    } else {
                        updateScheduleManager.cancel()
                    }
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
                        cacheRepository.clearCache()
                    }.onSuccess {
                        cacheSizeJob?.cancel()
                        cacheSizeJob = null
                        refreshCacheSize()
                        _events.send(TweaksEvent.OnCacheCleared)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        _events.send(
                            TweaksEvent.OnCacheClearError(
                                error.message
                                    ?: getString(Res.string.tweaks_clear_downloads_failed),
                            ),
                        )
                    }
                }
            }

            TweaksAction.OnClearDownloadsDismiss -> {
                _state.update { it.copy(isClearDownloadsDialogVisible = false) }
            }

            TweaksAction.OnMirrorPickerClick -> {
                // Handled in composable
            }

            TweaksAction.OnFeedbackClick ->
                _state.update { it.copy(isFeedbackSheetVisible = true) }

            TweaksAction.OnFeedbackDismiss ->
                _state.update { it.copy(isFeedbackSheetVisible = false) }

            TweaksAction.OnLanguagePickerOpen ->
                _state.update { it.copy(isLanguageSheetVisible = true, languageQuery = "") }

            TweaksAction.OnLanguagePickerDismiss ->
                _state.update { it.copy(isLanguageSheetVisible = false) }

            is TweaksAction.OnLanguageQueryChange ->
                _state.update { it.copy(languageQuery = action.query) }

            TweaksAction.OnTranslationProviderExpandToggle ->
                _state.update { it.copy(translationProviderExpanded = !it.translationProviderExpanded) }

            TweaksAction.OnTranslationTargetPickerOpen ->
                _state.update { it.copy(translationTargetPickerOpen = true, languageQuery = "") }

            TweaksAction.OnTranslationTargetPickerDismiss ->
                _state.update { it.copy(translationTargetPickerOpen = false) }

            TweaksAction.OnConnectionPasteSheetOpen ->
                _state.update {
                    it.copy(
                        connectionPasteSheetOpen = true,
                        proxyPasteUrlInput = "",
                        proxyPasteUrlError = false,
                    )
                }

            TweaksAction.OnConnectionPasteSheetDismiss ->
                _state.update { it.copy(connectionPasteSheetOpen = false) }

            TweaksAction.OnConnectionMasterExpandToggle ->
                _state.update { it.copy(connectionMasterExpanded = !it.connectionMasterExpanded) }

            is TweaksAction.OnProxyScopeExpandToggle ->
                _state.update {
                    it.copy(
                        expandedProxyScope =
                            if (it.expandedProxyScope == action.scope) null else action.scope,
                    )
                }

            is TweaksAction.OnDesktopSectionSelected ->
                _state.update { it.copy(desktopSection = action.section) }

            is TweaksAction.OnTranslationProviderSelected -> {
                when (action.provider) {
                    TranslationProvider.GOOGLE -> {

                        _state.update { it.copy(draftTranslationProvider = null) }
                        viewModelScope.launch {
                            tweaksRepository.setTranslationProvider(action.provider)
                            _events.send(TweaksEvent.OnTranslationProviderSaved)
                        }
                    }

                    TranslationProvider.YOUDAO -> {
                        val current = _state.value
                        val hasCreds =
                            current.youdaoAppKey.isNotBlank() &&
                                    current.youdaoAppSecret.isNotBlank()
                        if (hasCreds) {
                            _state.update { it.copy(draftTranslationProvider = null) }
                            viewModelScope.launch {
                                tweaksRepository.setTranslationProvider(action.provider)
                                _events.send(TweaksEvent.OnTranslationProviderSaved)
                            }
                        } else {

                            _state.update {
                                it.copy(draftTranslationProvider = TranslationProvider.YOUDAO)
                            }
                        }
                    }

                    TranslationProvider.LIBRE_TRANSLATE -> {

                        _state.update { it.copy(draftTranslationProvider = null) }
                        viewModelScope.launch {
                            tweaksRepository.setTranslationProvider(action.provider)
                            _events.send(TweaksEvent.OnTranslationProviderSaved)
                        }
                    }

                    TranslationProvider.DEEPL -> {
                        val current = _state.value
                        val hasCreds = current.deeplAuthKey.isNotBlank()
                        if (hasCreds) {
                            _state.update { it.copy(draftTranslationProvider = null) }
                            viewModelScope.launch {
                                tweaksRepository.setTranslationProvider(action.provider)
                                _events.send(TweaksEvent.OnTranslationProviderSaved)
                            }
                        } else {
                            _state.update {
                                it.copy(draftTranslationProvider = TranslationProvider.DEEPL)
                            }
                        }
                    }

                    TranslationProvider.MICROSOFT -> {
                        val current = _state.value
                        val hasCreds = current.microsoftTranslatorKey.isNotBlank()
                        if (hasCreds) {
                            _state.update { it.copy(draftTranslationProvider = null) }
                            viewModelScope.launch {
                                tweaksRepository.setTranslationProvider(action.provider)
                                _events.send(TweaksEvent.OnTranslationProviderSaved)
                            }
                        } else {
                            _state.update {
                                it.copy(draftTranslationProvider = TranslationProvider.MICROSOFT)
                            }
                        }
                    }
                }
            }

            is TweaksAction.OnYoudaoAppKeyChanged -> {
                _state.update { it.copy(youdaoAppKey = action.appKey) }
            }

            is TweaksAction.OnYoudaoAppSecretChanged -> {
                _state.update { it.copy(youdaoAppSecret = action.appSecret) }
            }

            TweaksAction.OnYoudaoAppSecretVisibilityToggle -> {
                _state.update {
                    it.copy(isYoudaoAppSecretVisible = !it.isYoudaoAppSecretVisible)
                }
            }

            TweaksAction.OnYoudaoCredentialsSave -> {
                val current = _state.value
                viewModelScope.launch {
                    tweaksRepository.setYoudaoAppKey(current.youdaoAppKey)
                    tweaksRepository.setYoudaoAppSecret(current.youdaoAppSecret)

                    val shouldActivate =
                        current.youdaoAppKey.isNotBlank() &&
                                current.youdaoAppSecret.isNotBlank() &&
                                (
                                        current.translationProvider != TranslationProvider.YOUDAO ||
                                                current.draftTranslationProvider == TranslationProvider.YOUDAO
                                        )
                    if (shouldActivate) {
                        tweaksRepository.setTranslationProvider(TranslationProvider.YOUDAO)
                    }

                    _state.update { it.copy(draftTranslationProvider = null) }
                    _events.send(TweaksEvent.OnYoudaoCredentialsSaved)
                }
            }

            is TweaksAction.OnLibreTranslateBaseUrlChanged -> {
                _state.update { it.copy(libreTranslateBaseUrl = action.url) }
            }

            is TweaksAction.OnLibreTranslateApiKeyChanged -> {
                _state.update { it.copy(libreTranslateApiKey = action.apiKey) }
            }

            TweaksAction.OnLibreTranslateApiKeyVisibilityToggle -> {
                _state.update {
                    it.copy(isLibreTranslateApiKeyVisible = !it.isLibreTranslateApiKeyVisible)
                }
            }

            TweaksAction.OnLibreTranslateCredentialsSave -> {
                val current = _state.value
                viewModelScope.launch {
                    tweaksRepository.setLibreTranslateBaseUrl(current.libreTranslateBaseUrl)
                    tweaksRepository.setLibreTranslateApiKey(current.libreTranslateApiKey)
                    val shouldActivate =
                        current.libreTranslateBaseUrl.isNotBlank() &&
                                (
                                        current.translationProvider != TranslationProvider.LIBRE_TRANSLATE ||
                                                current.draftTranslationProvider == TranslationProvider.LIBRE_TRANSLATE
                                        )
                    if (shouldActivate) {
                        tweaksRepository.setTranslationProvider(TranslationProvider.LIBRE_TRANSLATE)
                    }
                    _state.update { it.copy(draftTranslationProvider = null) }
                    _events.send(TweaksEvent.OnLibreTranslateCredentialsSaved)
                }
            }

            is TweaksAction.OnDeeplAuthKeyChanged -> {
                _state.update { it.copy(deeplAuthKey = action.authKey) }
            }

            TweaksAction.OnDeeplAuthKeyVisibilityToggle -> {
                _state.update {
                    it.copy(isDeeplAuthKeyVisible = !it.isDeeplAuthKeyVisible)
                }
            }

            TweaksAction.OnDeeplCredentialsSave -> {
                val current = _state.value
                viewModelScope.launch {
                    tweaksRepository.setDeeplAuthKey(current.deeplAuthKey)
                    val shouldActivate =
                        current.deeplAuthKey.isNotBlank() &&
                                (
                                        current.translationProvider != TranslationProvider.DEEPL ||
                                                current.draftTranslationProvider == TranslationProvider.DEEPL
                                        )
                    if (shouldActivate) {
                        tweaksRepository.setTranslationProvider(TranslationProvider.DEEPL)
                    }
                    _state.update { it.copy(draftTranslationProvider = null) }
                    _events.send(TweaksEvent.OnDeeplCredentialsSaved)
                }
            }

            is TweaksAction.OnMicrosoftTranslatorKeyChanged -> {
                _state.update { it.copy(microsoftTranslatorKey = action.key) }
            }

            is TweaksAction.OnMicrosoftTranslatorRegionChanged -> {
                _state.update { it.copy(microsoftTranslatorRegion = action.region) }
            }

            TweaksAction.OnMicrosoftTranslatorKeyVisibilityToggle -> {
                _state.update {
                    it.copy(isMicrosoftTranslatorKeyVisible = !it.isMicrosoftTranslatorKeyVisible)
                }
            }

            TweaksAction.OnMicrosoftTranslatorCredentialsSave -> {
                val current = _state.value
                viewModelScope.launch {
                    tweaksRepository.setMicrosoftTranslatorKey(current.microsoftTranslatorKey)
                    tweaksRepository.setMicrosoftTranslatorRegion(current.microsoftTranslatorRegion)
                    val shouldActivate =
                        current.microsoftTranslatorKey.isNotBlank() &&
                                (
                                        current.translationProvider != TranslationProvider.MICROSOFT ||
                                                current.draftTranslationProvider == TranslationProvider.MICROSOFT
                                        )
                    if (shouldActivate) {
                        tweaksRepository.setTranslationProvider(TranslationProvider.MICROSOFT)
                    }
                    _state.update { it.copy(draftTranslationProvider = null) }
                    _events.send(TweaksEvent.OnMicrosoftTranslatorCredentialsSaved)
                }
            }

            is TweaksAction.OnAppLanguageSelected -> {
                _state.update { it.copy(isLanguageSheetVisible = false) }
                if (action.tag == _state.value.selectedAppLanguage) return
                viewModelScope.launch {
                    tweaksRepository.setAppLanguage(action.tag)
                    if (isDesktop()) {
                        _events.send(TweaksEvent.OnAppLanguageChangeRequiresRestart)
                    }
                }
            }

            is TweaksAction.OnAutoTranslateEnabledToggle -> {
                viewModelScope.launch {
                    tweaksRepository.setAutoTranslateEnabled(action.enabled)
                }
            }

            is TweaksAction.OnAutoTranslateTargetSelected -> {
                _state.update { it.copy(translationTargetPickerOpen = false) }
                viewModelScope.launch {
                    tweaksRepository.setAutoTranslateTargetLang(action.tag)
                }
            }

            TweaksAction.OnOpenBatteryOptimizationSettings -> {
                val launched = aggressiveOemDetector.openBatteryOptimizationSettings()
                if (!launched) {
                    logger.warn("TweaksViewModel: failed to launch battery optimization settings")
                }
            }

            TweaksAction.OnDismissBatteryOptimizationCard -> {
                viewModelScope.launch {
                    runCatching {
                        tweaksRepository.setBatteryOptimizationPromptDismissed(true)
                    }.onFailure {
                        if (it is CancellationException) throw it
                        logger.error(
                            "TweaksViewModel: failed to persist battery-opt dismiss",
                            it,
                        )
                    }
                    _state.update { it.copy(showBatteryOptimizationCard = false) }
                }
            }

            TweaksAction.OnReevaluateBatteryOptimizationCard -> {
                evaluateBatteryOptimizationCard()
            }

            TweaksAction.OnOpenCustomForgesDialog -> {
                _state.update {
                    it.copy(
                        showCustomForgesDialog = true,
                        customForgeDraft = "",
                        customForgeError = null,
                    )
                }
            }

            TweaksAction.OnDismissCustomForgesDialog -> {
                _state.update { it.copy(showCustomForgesDialog = false) }
            }

            is TweaksAction.OnCustomForgeDraftChanged -> {
                _state.update { it.copy(customForgeDraft = action.draft, customForgeError = null) }
            }

            TweaksAction.OnAddCustomForge -> {
                val raw = _state.value.customForgeDraft
                    .trim()
                    .lowercase()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .substringBefore('/')
                viewModelScope.launch {
                    if (raw.isEmpty() || !raw.contains('.') || raw.contains(' ')) {
                        _state.update {
                            it.copy(customForgeError = getString(Res.string.tweaks_custom_forge_invalid_hostname))
                        }
                        return@launch
                    }
                    val result = runCatching { tweaksRepository.addCustomForgeHost(raw) }
                    if (result.isSuccess) {
                        _state.update { it.copy(customForgeDraft = "", customForgeError = null) }
                    } else {

                        _state.update {
                            it.copy(
                                customForgeError = result.exceptionOrNull()?.message
                                    ?: getString(Res.string.tweaks_custom_forge_save_failed),
                            )
                        }
                    }
                }
            }

            is TweaksAction.OnRemoveCustomForge -> {
                viewModelScope.launch {
                    val result = runCatching { tweaksRepository.removeCustomForgeHost(action.host) }
                    if (result.isFailure) {
                        _state.update {
                            it.copy(
                                customForgeError = result.exceptionOrNull()?.message
                                    ?: getString(Res.string.tweaks_custom_forge_remove_failed),
                            )
                        }
                    }
                }
            }

            is TweaksAction.OnMasterProxyTypeSelected -> {
                mutateMasterForm { it.copy(type = action.type) }

                if (action.type == ProxyType.NONE || action.type == ProxyType.SYSTEM) {
                    val config =
                        if (action.type == ProxyType.NONE) {
                            ProxyConfig.None
                        } else {
                            ProxyConfig.System
                        }
                    viewModelScope.launch {
                        runCatching {
                            proxyRepository.setMasterProxyConfig(config)
                            ProxyScope.entries.forEach { scope ->
                                if (_state.value.useMain(scope)) {
                                    proxyRepository.setProxyConfig(scope, config)
                                }
                            }
                        }.onSuccess {
                            _state.update {
                                it.copy(
                                    masterProxyForm = it.masterProxyForm.copy(isDraftDirty = false),
                                )
                            }
                            _events.send(TweaksEvent.OnProxySaved)
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                            _events.send(
                                TweaksEvent.OnProxySaveError(
                                    error.message
                                        ?: getString(Res.string.failed_to_save_proxy_settings),
                                ),
                            )
                        }
                    }
                }
            }

            is TweaksAction.OnMasterProxyHostChanged -> {
                mutateMasterForm { it.copy(host = action.host) }
            }

            is TweaksAction.OnMasterProxyPortChanged -> {
                mutateMasterForm { it.copy(port = action.port) }
            }

            is TweaksAction.OnMasterProxyUsernameChanged -> {
                mutateMasterForm { it.copy(username = action.username) }
            }

            is TweaksAction.OnMasterProxyPasswordChanged -> {
                mutateMasterForm { it.copy(password = action.password) }
            }

            TweaksAction.OnMasterProxyPasswordVisibilityToggle -> {
                mutateMasterForm { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }

            is TweaksAction.OnProxyPasteUrlChanged -> {
                _state.update { it.copy(proxyPasteUrlInput = action.value, proxyPasteUrlError = false) }
            }

            TweaksAction.OnProxyPasteUrlSubmit -> {
                val parsed = parseProxyUrl(_state.value.proxyPasteUrlInput)
                if (parsed == null) {
                    _state.update { it.copy(proxyPasteUrlError = true) }
                } else {
                    _state.update {
                        it.copy(
                            connectionPasteSheetOpen = false,
                            proxyPasteUrlInput = "",
                            proxyPasteUrlError = false,
                        )
                    }
                    mutateMasterForm {
                        it.copy(
                            type = parsed.type,
                            host = parsed.host,
                            port = parsed.port.toString(),
                            username = parsed.username.orEmpty(),
                            password = parsed.password.orEmpty(),
                        )
                    }
                }
            }

            TweaksAction.OnMasterProxySave -> {
                val config = buildMasterProxyConfig() ?: return
                viewModelScope.launch {
                    runCatching {
                        proxyRepository.setMasterProxyConfig(config)
                        ProxyScope.entries.forEach { scope ->
                            if (_state.value.useMain(scope)) {
                                proxyRepository.setProxyConfig(scope, config)
                            }
                        }
                    }.onSuccess {
                        _state.update {
                            it.copy(
                                masterProxyForm = it.masterProxyForm.copy(isDraftDirty = false),
                            )
                        }
                        _events.send(TweaksEvent.OnProxySaved)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        _events.send(
                            TweaksEvent.OnProxySaveError(
                                error.message
                                    ?: getString(Res.string.failed_to_save_proxy_settings),
                            ),
                        )
                    }
                }
            }

            TweaksAction.OnMasterProxyTest -> {
                val form = _state.value.masterProxyForm
                if (form.isTestInProgress) return
                val config = buildMasterProxyConfigForTest() ?: return
                _state.update {
                    it.copy(masterProxyForm = it.masterProxyForm.copy(isTestInProgress = true))
                }
                viewModelScope.launch {
                    val results = try {
                        kotlinx.coroutines.coroutineScope {
                            val search = async {
                                runProbe(config, "https://api.github.com/zen")
                            }
                            val download = async {
                                runProbe(config, "https://github.com/robots.txt")
                            }
                            val translation = async {
                                runProbe(config, "https://translate.disroot.org")
                            }
                            Triple(search.await(), download.await(), translation.await())
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Triple<Long?, Long?, Long?>(null, null, null)
                    } finally {
                        _state.update {
                            it.copy(
                                masterProxyForm = it.masterProxyForm.copy(isTestInProgress = false),
                            )
                        }
                    }
                    _events.send(
                        TweaksEvent.OnMasterProxyTestResult(
                            searchMs = results.first,
                            downloadMs = results.second,
                            translationMs = results.third,
                        ),
                    )
                }
            }

            TweaksAction.OnClearSeenHistoryRequest -> {
                _state.update { it.copy(isClearSeenHistoryDialogVisible = true) }
            }

            TweaksAction.OnClearSeenHistoryDismiss -> {
                _state.update { it.copy(isClearSeenHistoryDialogVisible = false) }
            }

            TweaksAction.OnClearSeenHistoryConfirm -> {
                _state.update { it.copy(isClearSeenHistoryDialogVisible = false) }
                onAction(TweaksAction.OnClearSeenRepos)
            }

            is TweaksAction.OnScopeUseMainToggled -> {
                _state.update {
                    it.copy(
                        useMasterByScope = (it.useMasterByScope + (action.scope to action.useMain)).toImmutableMap(),
                    )
                }
                viewModelScope.launch {
                    runCatching {
                        proxyRepository.setUseMaster(action.scope, action.useMain)
                        if (action.useMain) {
                            val master = proxyRepository.getMasterProxyConfig().first()
                            if (master != null) {
                                proxyRepository.setProxyConfig(action.scope, master)
                            }
                        }
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        _events.send(
                            TweaksEvent.OnProxySaveError(
                                error.message
                                    ?: getString(Res.string.failed_to_save_proxy_settings),
                            ),
                        )
                    }
                }
            }

            TweaksAction.OnExportSettings -> {
                viewModelScope.launch {
                    try {
                        val backup = collectSettingsForExport()
                        val jsonString = json.encodeToString(backup)
                        shareManager.shareFile(
                            "komi-store-settings-backup.json",
                            jsonString,
                        )
                        _events.send(TweaksEvent.OnSettingsExported)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _events.send(
                            TweaksEvent.OnSettingsExportError(
                                e.message ?: "Failed to export settings",
                            ),
                        )
                    }
                }
            }

            TweaksAction.OnImportSettings -> {
                viewModelScope.launch {
                    try {
                        val content = pickSettingsFile() ?: return@launch
                        val backup = json.decodeFromString<SettingsBackup>(content)
                        applySettingsBackup(backup)
                        _events.send(TweaksEvent.OnSettingsImported)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _events.send(
                            TweaksEvent.OnSettingsImportError(
                                e.message ?: "Failed to import settings",
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun pickSettingsFile(): String? = suspendCancellableCoroutine { continuation ->
        shareManager.pickFile("application/json") { content ->
            continuation.resume(content)
        }
    }

    private suspend fun collectSettingsForExport(): SettingsBackup {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return SettingsBackup(
            version = 1,
            exportedAt = now,
            themeColor = tweaksRepository.getThemeColor().first().name,
            isDarkTheme = tweaksRepository.getIsDarkTheme().first(),
            amoledTheme = tweaksRepository.getAmoledTheme().first(),
            mangaPaper = tweaksRepository.getMangaPaper().first().name,
            fontTheme = tweaksRepository.getFontTheme().first().name,
            personality = tweaksRepository.getPersonality().first().name,
            accentId = tweaksRepository.getAccentId().first().name,
            scrollbarEnabled = tweaksRepository.getScrollbarEnabled().first(),
            contentWidth = tweaksRepository.getContentWidth().first().name,
            autoDetectClipboardLinks = tweaksRepository.getAutoDetectClipboardLinks().first(),
            hideSeenEnabled = tweaksRepository.getHideSeenEnabled().first(),
            discoveryPlatforms = tweaksRepository.getDiscoveryPlatforms().first().map { it.name }.toSet(),
            showAllPlatforms = tweaksRepository.getShowAllPlatforms().first(),
            installerType = tweaksRepository.getInstallerType().first().name,
            installerAttribution = encodeInstallerAttributionForBackup(tweaksRepository.getInstallerAttribution().first()),
            autoUpdateEnabled = tweaksRepository.getAutoUpdateEnabled().first(),
            updateCheckEnabled = tweaksRepository.getUpdateCheckEnabled().first(),
            updateCheckIntervalHours = tweaksRepository.getUpdateCheckInterval().first(),
            includePreReleases = tweaksRepository.getIncludePreReleases().first(),
            appLanguage = tweaksRepository.getAppLanguage().first(),
            translationProvider = tweaksRepository.getTranslationProvider().first().name,
            youdaoAppKey = tweaksRepository.getYoudaoAppKey().first(),
            youdaoAppSecret = tweaksRepository.getYoudaoAppSecret().first(),
            libreTranslateBaseUrl = tweaksRepository.getLibreTranslateBaseUrl().first(),
            libreTranslateApiKey = tweaksRepository.getLibreTranslateApiKey().first(),
            deeplAuthKey = tweaksRepository.getDeeplAuthKey().first(),
            microsoftTranslatorKey = tweaksRepository.getMicrosoftTranslatorKey().first(),
            microsoftTranslatorRegion = tweaksRepository.getMicrosoftTranslatorRegion().first(),
            autoTranslateEnabled = tweaksRepository.getAutoTranslateEnabled().first(),
            autoTranslateTargetLang = tweaksRepository.getAutoTranslateTargetLang().first(),
            externalImportEnabled = tweaksRepository.getExternalImportEnabled().first(),
            externalMatchSearchEnabled = tweaksRepository.getExternalMatchSearchEnabled().first(),
            appsSortRule = tweaksRepository.getAppsSortRule().first(),
            starredSortRule = tweaksRepository.getStarredSortRule().first(),
            favouritesSortRule = tweaksRepository.getFavouritesSortRule().first(),
            customForgeHosts = tweaksRepository.getCustomForgeHosts().first(),
            proxyConfigs = buildMap {
                ProxyScope.entries.forEach { scope ->
                    val config = proxyRepository.getProxyConfig(scope).first()
                    put(scope.name, proxyConfigToBackup(config))
                }
            },
            masterProxyConfig = proxyRepository.getMasterProxyConfig().first()?.let { proxyConfigToBackup(it) },
            useMasterByScope = buildMap {
                ProxyScope.entries.forEach { scope ->
                    put(scope.name, proxyRepository.getUseMaster(scope).first())
                }
            },
        )
    }

    private fun proxyConfigToBackup(config: ProxyConfig): ProxyEntryBackup = when (config) {
        is ProxyConfig.None -> ProxyEntryBackup(type = "None")
        is ProxyConfig.System -> ProxyEntryBackup(type = "System")
        is ProxyConfig.Http -> ProxyEntryBackup(
            type = "Http",
            host = config.host,
            port = config.port,
            username = config.username,
            password = config.password,
        )
        is ProxyConfig.Socks -> ProxyEntryBackup(
            type = "Socks",
            host = config.host,
            port = config.port,
            username = config.username,
            password = config.password,
        )
    }

    private suspend fun applySettingsBackup(backup: SettingsBackup) {
        backup.themeColor?.let { tweaksRepository.setThemeColor(AppTheme.valueOf(it)) }
        backup.isDarkTheme?.let { tweaksRepository.setDarkTheme(it) }
        backup.amoledTheme?.let { tweaksRepository.setAmoledTheme(it) }
        backup.mangaPaper?.let { tweaksRepository.setMangaPaper(MangaPaperId.valueOf(it)) }
        backup.fontTheme?.let { tweaksRepository.setFontTheme(FontTheme.valueOf(it)) }
        backup.personality?.let { tweaksRepository.setPersonality(AppPersonality.valueOf(it)) }
        backup.accentId?.let { tweaksRepository.setAccentId(AccentId.valueOf(it)) }
        backup.scrollbarEnabled?.let { tweaksRepository.setScrollbarEnabled(it) }
        backup.contentWidth?.let { tweaksRepository.setContentWidth(ContentWidth.valueOf(it)) }
        backup.autoDetectClipboardLinks?.let { tweaksRepository.setAutoDetectClipboardLinks(it) }
        backup.hideSeenEnabled?.let { tweaksRepository.setHideSeenEnabled(it) }
        backup.discoveryPlatforms?.let { platforms ->
            tweaksRepository.setDiscoveryPlatforms(platforms.mapNotNull { DiscoveryPlatform.fromName(it) }.toSet())
        }
        backup.showAllPlatforms?.let { tweaksRepository.setShowAllPlatforms(it) }
        backup.installerType?.let { tweaksRepository.setInstallerType(InstallerType.valueOf(it)) }
        backup.installerAttribution?.let { decodeInstallerAttributionFromBackup(it)?.let { attr -> tweaksRepository.setInstallerAttribution(attr) } }
        backup.autoUpdateEnabled?.let { tweaksRepository.setAutoUpdateEnabled(it) }
        backup.updateCheckEnabled?.let { tweaksRepository.setUpdateCheckEnabled(it) }
        backup.updateCheckIntervalHours?.let { tweaksRepository.setUpdateCheckInterval(it) }
        backup.includePreReleases?.let { tweaksRepository.setIncludePreReleases(it) }
        backup.appLanguage?.let { tweaksRepository.setAppLanguage(it) }
        backup.translationProvider?.let { tweaksRepository.setTranslationProvider(TranslationProvider.valueOf(it)) }
        backup.youdaoAppKey?.let { tweaksRepository.setYoudaoAppKey(it) }
        backup.youdaoAppSecret?.let { tweaksRepository.setYoudaoAppSecret(it) }
        backup.libreTranslateBaseUrl?.let { tweaksRepository.setLibreTranslateBaseUrl(it) }
        backup.libreTranslateApiKey?.let { tweaksRepository.setLibreTranslateApiKey(it) }
        backup.deeplAuthKey?.let { tweaksRepository.setDeeplAuthKey(it) }
        backup.microsoftTranslatorKey?.let { tweaksRepository.setMicrosoftTranslatorKey(it) }
        backup.microsoftTranslatorRegion?.let { tweaksRepository.setMicrosoftTranslatorRegion(it) }
        backup.autoTranslateEnabled?.let { tweaksRepository.setAutoTranslateEnabled(it) }
        backup.autoTranslateTargetLang?.let { tweaksRepository.setAutoTranslateTargetLang(it) }
        backup.externalImportEnabled?.let { tweaksRepository.setExternalImportEnabled(it) }
        backup.externalMatchSearchEnabled?.let { tweaksRepository.setExternalMatchSearchEnabled(it) }
        backup.appsSortRule?.let { tweaksRepository.setAppsSortRule(it) }
        backup.starredSortRule?.let { tweaksRepository.setStarredSortRule(it) }
        backup.favouritesSortRule?.let { tweaksRepository.setFavouritesSortRule(it) }
        backup.customForgeHosts?.let { hosts ->
            val current = tweaksRepository.getCustomForgeHosts().first()
            val toAdd = hosts - current
            val toRemove = current - hosts
            toRemove.forEach { tweaksRepository.removeCustomForgeHost(it) }
            toAdd.forEach { tweaksRepository.addCustomForgeHost(it) }
        }
        backup.proxyConfigs?.let { configs ->
            configs.forEach { (scopeName, entry) ->
                val scope = ProxyScope.valueOf(scopeName)
                val config = proxyBackupToConfig(entry)
                proxyRepository.setProxyConfig(scope, config)
            }
        }
        backup.masterProxyConfig?.let { entry ->
            proxyRepository.setMasterProxyConfig(proxyBackupToConfig(entry))
        }
        backup.useMasterByScope?.let { flags ->
            flags.forEach { (scopeName, useMaster) ->
                val scope = ProxyScope.valueOf(scopeName)
                proxyRepository.setUseMaster(scope, useMaster)
            }
        }
    }

    private fun proxyBackupToConfig(entry: ProxyEntryBackup): ProxyConfig = when (entry.type) {
        "None" -> ProxyConfig.None
        "System" -> ProxyConfig.System
        "Http" -> ProxyConfig.Http(
            host = entry.host ?: "",
            port = entry.port ?: 0,
            username = entry.username,
            password = entry.password,
        )
        "Socks" -> ProxyConfig.Socks(
            host = entry.host ?: "",
            port = entry.port ?: 0,
            username = entry.username,
            password = entry.password,
        )
        else -> ProxyConfig.None
    }

    private fun encodeInstallerAttributionForBackup(attribution: InstallerAttribution): String = when (attribution) {
        is InstallerAttribution.SystemDefault -> ""
        is InstallerAttribution.Preset -> "preset:${attribution.key.name}"
        is InstallerAttribution.Custom -> "custom:${attribution.packageName.trim()}"
    }

    private fun decodeInstallerAttributionFromBackup(value: String): InstallerAttribution? = when {
        value.isBlank() -> InstallerAttribution.SystemDefault
        value.startsWith("preset:") -> {
            val keyName = value.removePrefix("preset:")
            InstallerAttribution.Preset(
                zed.rainxch.core.domain.model.installation.PresetKey.valueOf(keyName),
            )
        }
        value.startsWith("custom:") -> {
            val packageName = value.removePrefix("custom:")
            InstallerAttribution.Custom(packageName)
        }
        else -> null
    }

    private suspend fun runProbe(config: ProxyConfig, url: String): Long? = try {
        val outcome = proxyTester.test(config, url)
        (outcome as? ProxyTestOutcome.Success)?.latencyMs
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun buildMasterProxyConfigForTest(): ProxyConfig? {
        val form = _state.value.masterProxyForm
        return when (form.type) {
            ProxyType.NONE -> ProxyConfig.None
            ProxyType.SYSTEM -> ProxyConfig.System
            ProxyType.HTTP -> {
                val host = form.host.trim().takeIf { it.isNotEmpty() } ?: return null
                val port = form.port.toIntOrNull() ?: return null
                ProxyConfig.Http(
                    host,
                    port,
                    form.username.takeIf { it.isNotBlank() },
                    form.password.takeIf { it.isNotBlank() },
                )
            }

            ProxyType.SOCKS -> {
                val host = form.host.trim().takeIf { it.isNotEmpty() } ?: return null
                val port = form.port.toIntOrNull() ?: return null
                ProxyConfig.Socks(
                    host,
                    port,
                    form.username.takeIf { it.isNotBlank() },
                    form.password.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    private fun observeMasterProxyConfig() {
        viewModelScope.launch {
            proxyRepository.getMasterProxyConfig().collect { config ->
                _state.update { state ->
                    if (state.masterProxyForm.isDraftDirty) return@update state
                    val existing = state.masterProxyForm
                    val populated = when (config) {
                        null -> existing.copy(type = ProxyType.NONE)
                        is ProxyConfig.None -> existing.copy(
                            type = ProxyType.NONE,
                        )

                        is ProxyConfig.System -> existing.copy(
                            type = ProxyType.SYSTEM,
                        )

                        is ProxyConfig.Http -> existing.copy(
                            type = ProxyType.HTTP,
                            host = config.host,
                            port = config.port.toString(),
                            username = config.username.orEmpty(),
                            password = config.password.orEmpty(),
                        )

                        is ProxyConfig.Socks -> existing.copy(
                            type = ProxyType.SOCKS,
                            host = config.host,
                            port = config.port.toString(),
                            username = config.username.orEmpty(),
                            password = config.password.orEmpty(),
                        )
                    }
                    state.copy(masterProxyForm = populated)
                }
            }
        }
    }

    private fun observeUseMasterFlags() {
        ProxyScope.entries.forEach { scope ->
            viewModelScope.launch {
                proxyRepository.getUseMaster(scope).collect { useMaster ->
                    _state.update { state ->
                        state.copy(
                            useMasterByScope = (state.useMasterByScope + (scope to useMaster)).toImmutableMap(),
                        )
                    }
                }
            }
        }
    }

    private fun mutateMasterForm(block: (ProxyScopeFormState) -> ProxyScopeFormState) {
        _state.update { state ->
            val updated = block(state.masterProxyForm).copy(isDraftDirty = true)
            state.copy(masterProxyForm = updated)
        }
    }

    private fun buildMasterProxyConfig(): ProxyConfig? {
        val form = _state.value.masterProxyForm
        return when (form.type) {
            ProxyType.NONE -> ProxyConfig.None
            ProxyType.SYSTEM -> ProxyConfig.System
            ProxyType.HTTP, ProxyType.SOCKS -> {
                val port = form.port.toIntOrNull()?.takeIf { it in 1..65535 } ?: run {
                    viewModelScope.launch {
                        _events.send(
                            TweaksEvent.OnProxySaveError(
                                getString(Res.string.invalid_proxy_port),
                            ),
                        )
                    }
                    return null
                }
                val host = form.host.trim().takeIf { isValidProxyHost(it) } ?: run {
                    val isBlank = form.host.isBlank()
                    viewModelScope.launch {
                        val msg = if (isBlank) {
                            getString(Res.string.proxy_host_required)
                        } else {
                            getString(Res.string.proxy_host_invalid)
                        }
                        _events.send(TweaksEvent.OnProxySaveError(msg))
                    }
                    return null
                }
                val username = form.username.takeIf { it.isNotBlank() }
                val password = form.password.takeIf { it.isNotBlank() }
                if (form.type == ProxyType.HTTP) {
                    ProxyConfig.Http(host, port, username, password)
                } else {
                    ProxyConfig.Socks(host, port, username, password)
                }
            }
        }
    }

    private fun buildProxyConfigForTest(scope: ProxyScope): ProxyConfig? {
        val form = _state.value.formFor(scope)
        return when (form.type) {
            ProxyType.NONE -> ProxyConfig.None
            ProxyType.SYSTEM -> ProxyConfig.System
            ProxyType.HTTP, ProxyType.SOCKS -> {
                val port =
                    form.port
                        .toIntOrNull()
                        ?.takeIf { it in 1..65535 }
                        ?: run {
                            viewModelScope.launch {
                                _events.send(
                                    TweaksEvent.OnProxyTestError(
                                        getString(Res.string.invalid_proxy_port),
                                    ),
                                )
                            }
                            return null
                        }
                val host =
                    form.host.trim().takeIf { isValidProxyHost(it) }
                        ?: run {
                            val isBlank = form.host.isBlank()
                            viewModelScope.launch {
                                val msg =
                                    if (isBlank) {
                                        getString(Res.string.proxy_host_required)
                                    } else {
                                        getString(Res.string.proxy_host_invalid)
                                    }
                                _events.send(TweaksEvent.OnProxyTestError(msg))
                            }
                            return null
                        }
                val username = form.username.takeIf { it.isNotBlank() }
                val password = form.password.takeIf { it.isNotBlank() }
                if (form.type == ProxyType.HTTP) {
                    ProxyConfig.Http(host, port, username, password)
                } else {
                    ProxyConfig.Socks(host, port, username, password)
                }
            }
        }
    }

    private fun isValidProxyHost(raw: String): Boolean {
        val host = raw.trim()
        if (host.isBlank()) return false
        if (host.length > 253) return false
        if (host.any { it.isWhitespace() }) return false
        if (host.contains("://") || host.contains("/") ||
            host.contains("?") || host.contains("#")
        ) {
            return false
        }
        if (IPV4_PATTERN.matches(host)) return true
        val ipv6Candidate = host.trim('[', ']')
        if (ipv6Candidate.contains(":") && IPV6_PATTERN.matches(ipv6Candidate)) return true
        return HOSTNAME_PATTERN.matches(host)
    }

    private suspend fun ProxyTestOutcome.toEvent(): TweaksEvent =
        when (this) {
            is ProxyTestOutcome.Success ->
                TweaksEvent.OnProxyTestSuccess(latencyMs = latencyMs)

            ProxyTestOutcome.Failure.DnsFailure ->
                TweaksEvent.OnProxyTestError(getString(Res.string.proxy_test_error_dns))

            ProxyTestOutcome.Failure.ProxyUnreachable ->
                TweaksEvent.OnProxyTestError(getString(Res.string.proxy_test_error_unreachable))

            ProxyTestOutcome.Failure.Timeout ->
                TweaksEvent.OnProxyTestError(getString(Res.string.proxy_test_error_timeout))

            ProxyTestOutcome.Failure.ProxyAuthRequired ->
                TweaksEvent.OnProxyTestError(getString(Res.string.proxy_test_error_auth_required))

            is ProxyTestOutcome.Failure.UnexpectedResponse ->
                TweaksEvent.OnProxyTestError(
                    getString(Res.string.proxy_test_error_status, statusCode),
                )

            is ProxyTestOutcome.Failure.Unknown ->

                TweaksEvent.OnProxyTestError(getString(Res.string.proxy_test_error_unknown))
        }
}
