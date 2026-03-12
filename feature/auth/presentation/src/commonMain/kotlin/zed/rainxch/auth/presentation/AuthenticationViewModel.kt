package zed.rainxch.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import zed.rainxch.auth.domain.repository.AuthenticationRepository
import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.GithubDeviceStart
import zed.rainxch.core.domain.utils.BrowserHelper
import zed.rainxch.core.domain.utils.ClipboardHelper
import zed.rainxch.githubstore.core.presentation.res.*

class AuthenticationViewModel(
    private val authenticationRepository: AuthenticationRepository,
    private val browserHelper: BrowserHelper,
    private val clipboardHelper: ClipboardHelper,
    private val scope: CoroutineScope,
    private val logger: GitHubStoreLogger,
) : ViewModel() {
    private var hasLoadedInitialData = false
    private var countdownJob: Job? = null

    private val _state: MutableStateFlow<AuthenticationState> =
        MutableStateFlow(AuthenticationState())

    private val _events = Channel<AuthenticationEvents>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    scope.launch {
                        authenticationRepository.accessTokenFlow.collect { token ->
                            _state.update {
                                it.copy(
                                    loginState =
                                        if (token.isNullOrEmpty()) {
                                            AuthLoginState.LoggedOut
                                        } else {
                                            _events.trySend(AuthenticationEvents.OnNavigateToMain)
                                            AuthLoginState.LoggedIn
                                        },
                                )
                            }
                        }
                    }

                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = AuthenticationState(),
            )

    fun onAction(action: AuthenticationAction) {
        when (action) {
            is AuthenticationAction.StartLogin -> {
                startLogin()
            }

            is AuthenticationAction.CopyCode -> {
                copyCode(action.start)
            }

            is AuthenticationAction.OpenGitHub -> {
                openGitHub(action.start)
            }

            AuthenticationAction.MarkLoggedIn -> {
                _state.update { it.copy(loginState = AuthLoginState.LoggedIn) }
            }

            AuthenticationAction.MarkLoggedOut -> {
                _state.update { it.copy(loginState = AuthLoginState.LoggedOut) }
            }

            is AuthenticationAction.OnInfo -> {
                _state.update {
                    it.copy(
                        info = action.message,
                    )
                }
            }

            AuthenticationAction.SkipLogin -> {
                _events.trySend(AuthenticationEvents.OnNavigateToMain)
            }
        }
    }

    private fun startCountdown(start: GithubDeviceStart) {
        countdownJob?.cancel()
        countdownJob =
            viewModelScope.launch {
                var remaining = start.expiresInSec
                while (remaining > 0) {
                    _state.update { currentState ->
                        val loginState = currentState.loginState
                        if (loginState is AuthLoginState.DevicePrompt) {
                            currentState.copy(
                                loginState = loginState.copy(remainingSeconds = remaining),
                            )
                        } else {
                            return@launch
                        }
                    }
                    delay(1000L)
                    remaining--
                }
                _state.update {
                    it.copy(
                        loginState =
                            AuthLoginState.Error(
                                message = getString(Res.string.auth_error_code_expired),
                                recoveryHint = getString(Res.string.auth_hint_try_again),
                            ),
                    )
                }
            }
    }

    private fun startLogin() {
        viewModelScope.launch {
            try {
                val start =
                    withContext(Dispatchers.IO) {
                        authenticationRepository.startDeviceFlow()
                    }

                withContext(Dispatchers.Main.immediate) {
                    _state.update {
                        it.copy(
                            loginState =
                                AuthLoginState.DevicePrompt(
                                    start = start,
                                    remainingSeconds = start.expiresInSec,
                                ),
                            copied = false,
                        )
                    }

                    startCountdown(start)

                    try {
                        clipboardHelper.copy(
                            label = getString(Res.string.enter_code_on_github),
                            text = start.userCode,
                        )
                        _state.update { it.copy(copied = true) }
                    } catch (e: Exception) {
                        logger.debug("Failed to copy to clipboard: ${e.message}")
                    }
                }

                withContext(Dispatchers.IO) {
                    authenticationRepository.awaitDeviceToken(start = start)
                }

                countdownJob?.cancel()

                withContext(Dispatchers.Main.immediate) {
                    _state.update { it.copy(loginState = AuthLoginState.LoggedIn) }
                    _events.trySend(AuthenticationEvents.OnNavigateToMain)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                countdownJob?.cancel()
                val (message, hint) = categorizeError(t)
                withContext(Dispatchers.Main.immediate) {
                    _state.update {
                        it.copy(
                            loginState =
                                AuthLoginState.Error(
                                    message = message,
                                    recoveryHint = hint,
                                ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun categorizeError(t: Throwable): Pair<String, String?> {
        val msg = t.message ?: return getString(Res.string.error_unknown) to null
        val lowerMsg = msg.lowercase()
        return when {
            "timeout" in lowerMsg || "timed out" in lowerMsg -> {
                msg to getString(Res.string.auth_hint_check_connection)
            }

            "network" in lowerMsg || "unresolvedaddress" in lowerMsg || "connect" in lowerMsg -> {
                msg to getString(Res.string.auth_hint_check_connection)
            }

            "expired" in lowerMsg || "expire" in lowerMsg -> {
                msg to getString(Res.string.auth_hint_try_again)
            }

            "denied" in lowerMsg || "access_denied" in lowerMsg -> {
                msg to getString(Res.string.auth_hint_denied)
            }

            else -> {
                msg to null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }

    private fun openGitHub(start: GithubDeviceStart) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            try {
                val url = start.verificationUriComplete ?: start.verificationUri
                browserHelper.openUrl(url)
            } catch (e: Exception) {
                logger.debug("⚠️ Failed to open browser: ${e.message}")
            }
        }
    }

    private fun copyCode(start: GithubDeviceStart) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            try {
                clipboardHelper.copy(
                    label = "GitHub Code",
                    text = start.userCode,
                )

                _state.update {
                    val currentRemaining = (it.loginState as? AuthLoginState.DevicePrompt)?.remainingSeconds ?: 0

                    it.copy(
                        loginState = AuthLoginState.DevicePrompt(start, currentRemaining),
                        copied = true,
                    )
                }
            } catch (e: Exception) {
                logger.debug("⚠️ Failed to copy to clipboard: ${e.message}")
                _state.update {
                    val currentRemaining = (it.loginState as? AuthLoginState.DevicePrompt)?.remainingSeconds ?: 0

                    it.copy(
                        loginState = AuthLoginState.DevicePrompt(start, currentRemaining),
                        copied = false,
                    )
                }
            }
        }
    }
}
