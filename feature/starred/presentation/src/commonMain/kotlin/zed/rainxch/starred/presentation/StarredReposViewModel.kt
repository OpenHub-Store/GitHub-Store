@file:OptIn(ExperimentalTime::class)

package zed.rainxch.starred.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.model.FavoriteRepo
import zed.rainxch.core.domain.repository.AuthenticationState
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.starred.presentation.mappers.toStarredRepositoryUi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class StarredReposViewModel(
    private val authenticationState: AuthenticationState,
    private val starredRepository: StarredRepository,
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {
    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(StarredReposState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    checkAuthAndLoad()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = StarredReposState(),
            )

    private fun checkAuthAndLoad() {
        viewModelScope.launch {
            val isAuthenticated = authenticationState.isCurrentlyUserLoggedIn()

            _state.update { it.copy(isAuthenticated = isAuthenticated) }

            if (isAuthenticated) {
                loadStarredRepos()
                syncIfNeeded()
            }
        }
    }

    private fun loadStarredRepos() {
        viewModelScope.launch {
            combine(
                starredRepository.getAllStarred(),
                favouritesRepository.getAllFavorites(),
            ) { starred, favorites ->
                val favoriteIds = favorites.map { it.repoId }.toSet()

                starred.map {
                    it.toStarredRepositoryUi(
                        isFavorite = favoriteIds.contains(it.repoId),
                    )
                }
            }.flowOn(Dispatchers.Default)
                .collect { starredRepos ->
                    _state.update {
                        it.copy(
                            starredRepositories = starredRepos.toImmutableList(),
                            isLoading = false,
                        )
                    }
                }
        }
    }

    private fun syncIfNeeded() {
        viewModelScope.launch {
            if (starredRepository.needsSync()) {
                syncStarredRepos()
            } else {
                val lastSync = starredRepository.getLastSyncTime()
                _state.update { it.copy(lastSyncTime = lastSync) }
            }
        }
    }

    private fun syncStarredRepos(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, errorMessage = null) }

            val result = starredRepository.syncStarredRepos(forceRefresh)

            result
                .onSuccess {
                    val lastSync = starredRepository.getLastSyncTime()
                    _state.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncTime = lastSync,
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            isSyncing = false,
                            errorMessage = error.message ?: getString(Res.string.sync_starred_failed),
                        )
                    }
                }
        }
    }

    fun onAction(action: StarredReposAction) {
        when (action) {
            StarredReposAction.OnNavigateBackClick -> {
                // Handled in composable
            }

            is StarredReposAction.OnRepositoryClick -> {
                // Handled in composable
            }

            is StarredReposAction.OnDeveloperProfileClick -> {
                // Handled in composable
            }

            is StarredReposAction.OnSignInClick -> {
                // Handled in composable
            }

            StarredReposAction.OnRefresh -> {
                syncStarredRepos(forceRefresh = true)
            }

            StarredReposAction.OnRetrySync -> {
                syncStarredRepos(forceRefresh = true)
            }

            StarredReposAction.OnDismissError -> {
                _state.update { it.copy(errorMessage = null) }
            }

            is StarredReposAction.OnToggleFavorite -> {
                viewModelScope.launch {
                    val repo = action.repository

                    val favoriteRepo =
                        FavoriteRepo(
                            repoId = repo.repoId,
                            repoName = repo.repoName,
                            repoOwner = repo.repoOwner,
                            repoOwnerAvatarUrl = repo.repoOwnerAvatarUrl,
                            repoDescription = repo.repoDescription,
                            primaryLanguage = repo.primaryLanguage,
                            repoUrl = repo.repoUrl,
                            latestVersion = repo.latestRelease,
                            latestReleaseUrl = repo.latestReleaseUrl,
                            addedAt = Clock.System.now().toEpochMilliseconds(),
                            lastSyncedAt = Clock.System.now().toEpochMilliseconds(),
                        )

                    favouritesRepository.toggleFavorite(favoriteRepo)
                }
            }
        }
    }
}
