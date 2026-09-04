package zed.rainxch.githubstore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.RateLimitRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(
    private val tweaksRepository: TweaksRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val userSessionRepository: UserSessionRepository,
    private val rateLimitRepository: RateLimitRepository,
    private val syncUseCase: SyncInstalledAppsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(MainState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            userSessionRepository
                .isUserLoggedIn()
                .collect { isLoggedIn ->
                    _state.update { it.copy(isLoggedIn = isLoggedIn) }

                    if (isLoggedIn) {
                        rateLimitRepository.clear()
                    }
                }
        }

        viewModelScope.launch {
            tweaksRepository
                .getThemeColor()
                .collect { theme ->
                    _state.update {
                        it.copy(currentColorTheme = theme)
                    }
                }
        }
        viewModelScope.launch {
            tweaksRepository
                .getFontTheme()
                .collect { fontTheme ->
                    _state.update {
                        it.copy(currentFontTheme = fontTheme)
                    }
                }
        }

        // Sole writer of the gated appearance fields. combine emits only after
        // all five sources have their first value, so state and the gate flag
        // land in one update. The timeout releases the gate on defaults if a
        // source never emits (same guard as the language read in MainActivity).
        viewModelScope.launch {
            val appearance =
                combine(
                    tweaksRepository.getPersonality(),
                    tweaksRepository.getAccentId(),
                    tweaksRepository.getMangaPaper(),
                    tweaksRepository.getAmoledTheme(),
                    tweaksRepository.getIsDarkTheme(),
                ) { personality, accent, paper, amoled, isDark ->
                    Appearance(personality, accent, paper, amoled, isDark)
                }
            val loaded =
                try {
                    withTimeoutOrNull(APPEARANCE_LOAD_TIMEOUT_MS.milliseconds) {
                        appearance.first()
                    }
                } catch (_: Exception) {
                    null
                }
            if (loaded != null) {
                _state.update { it.withAppearance(loaded).copy(appearanceLoaded = true) }
            } else {
                _state.update { it.copy(appearanceLoaded = true) }
            }

            appearance.collect { snapshot ->
                _state.update { it.withAppearance(snapshot) }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getScrollbarEnabled().collect { enabled ->
                _state.update { it.copy(isScrollbarEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getContentWidth().collect { width ->
                _state.update { it.copy(contentWidth = width) }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getAppLanguage().collect { tag ->
                _state.update { it.copy(appLanguageTag = tag) }
            }
        }

        viewModelScope.launch {
            rateLimitRepository.rateLimitState.collect { rateLimitInfo ->
                _state.update { currentState ->
                    currentState.copy(rateLimitInfo = rateLimitInfo)
                }
            }
        }

        viewModelScope.launch {
            rateLimitRepository.rateLimitExhaustedEvent.collect { info ->
                _state.update { it.copy(showRateLimitDialog = true, rateLimitInfo = info) }
            }
        }

        viewModelScope.launch {
            userSessionRepository.sessionExpiredEvent.collect {
                _state.update { it.copy(showSessionExpiredDialog = true) }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            syncUseCase().onSuccess {
                installedAppsRepository.checkAllForUpdates()
            }
        }
    }

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.DismissRateLimitDialog -> {
                _state.update { it.copy(showRateLimitDialog = false) }
            }

            MainAction.DismissSessionExpiredDialog -> {
                _state.update { it.copy(showSessionExpiredDialog = false) }
            }
        }
    }
}

private const val APPEARANCE_LOAD_TIMEOUT_MS = 2000L

private data class Appearance(
    val personality: AppPersonality,
    val accent: AccentId,
    val mangaPaper: MangaPaperId,
    val isAmoledTheme: Boolean,
    val isDarkTheme: Boolean?,
)

private fun MainState.withAppearance(snapshot: Appearance): MainState =
    copy(
        personality = snapshot.personality,
        accent = snapshot.accent,
        mangaPaper = snapshot.mangaPaper,
        isAmoledTheme = snapshot.isAmoledTheme,
        isDarkTheme = snapshot.isDarkTheme,
    )
