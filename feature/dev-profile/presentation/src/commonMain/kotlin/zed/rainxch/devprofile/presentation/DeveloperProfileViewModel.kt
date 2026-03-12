@file:OptIn(ExperimentalTime::class)

package zed.rainxch.devprofile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.model.FavoriteRepo
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.devprofile.domain.model.RepoFilterType
import zed.rainxch.devprofile.domain.model.RepoSortType
import zed.rainxch.devprofile.domain.repository.DeveloperProfileRepository
import zed.rainxch.githubstore.core.presentation.res.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DeveloperProfileViewModel(
    private val username: String,
    private val repository: DeveloperProfileRepository,
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {
    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(DeveloperProfileState(username = username))
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadDeveloperData()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = DeveloperProfileState(username = username),
            )

    private fun loadDeveloperData() {
        viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        isLoading = true,
                        isLoadingRepos = false,
                        errorMessage = null,
                    )
                }

                val profileResult = repository.getDeveloperProfile(username)
                profileResult
                    .onSuccess { profile ->
                        _state.update {
                            it.copy(
                                profile = profile,
                                isLoading = false,
                                isLoadingRepos = true,
                            )
                        }
                    }.onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage =
                                    error.message
                                        ?: getString(Res.string.failed_to_load_profile),
                            )
                        }
                        return@launch
                    }

                val reposResult = repository.getDeveloperRepositories(username)

                reposResult
                    .onSuccess { repos ->
                        _state.update {
                            it.copy(
                                repositories = repos.toImmutableList(),
                                isLoading = false,
                                isLoadingRepos = false,
                            )
                        }
                        applyFiltersAndSort()
                    }.onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isLoadingRepos = false,
                                errorMessage =
                                    error.message
                                        ?: getString(Res.string.failed_to_load_repositories),
                            )
                        }
                    }
            } catch (_: RateLimitException) {
                _state.update {
                    it.copy(isLoading = false, isLoadingRepos = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingRepos = false,
                        errorMessage = e.message,
                    )
                }
            }
        }
    }

    private fun applyFiltersAndSort() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentState = _state.value
            var filtered = currentState.repositories

            if (currentState.searchQuery.isNotBlank()) {
                val query = currentState.searchQuery.lowercase()
                filtered =
                    filtered
                        .filter { repo ->
                            repo.name.lowercase().contains(query) ||
                                repo.description?.lowercase()?.contains(query) == true
                        }.toImmutableList()
            }

            filtered =
                when (currentState.currentFilter) {
                    RepoFilterType.WITH_RELEASES -> {
                        filtered
                            .filter { it.hasInstallableAssets }
                            .toImmutableList()
                    }

                    RepoFilterType.INSTALLED -> {
                        filtered.filter { it.isInstalled }.toImmutableList()
                    }

                    RepoFilterType.FAVORITES -> {
                        filtered.filter { it.isFavorite }.toImmutableList()
                    }
                }

            filtered =
                when (currentState.currentSort) {
                    RepoSortType.UPDATED -> {
                        filtered
                            .sortedByDescending { it.updatedAt }
                            .toImmutableList()
                    }

                    RepoSortType.STARS -> {
                        filtered
                            .sortedByDescending { it.stargazersCount }
                            .toImmutableList()
                    }

                    RepoSortType.NAME -> {
                        filtered.sortedBy { it.name.lowercase() }.toImmutableList()
                    }
                }

            _state.update { it.copy(filteredRepositories = filtered) }
        }
    }

    fun onAction(action: DeveloperProfileAction) {
        when (action) {
            DeveloperProfileAction.OnNavigateBackClick,
            is DeveloperProfileAction.OnRepositoryClick,
            is DeveloperProfileAction.OnOpenLink,
            -> {
            }

            is DeveloperProfileAction.OnFilterChange -> {
                _state.update { it.copy(currentFilter = action.filter) }
                applyFiltersAndSort()
            }

            is DeveloperProfileAction.OnSortChange -> {
                _state.update { it.copy(currentSort = action.sort) }
                applyFiltersAndSort()
            }

            is DeveloperProfileAction.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = action.query) }
                applyFiltersAndSort()
            }

            is DeveloperProfileAction.OnToggleFavorite -> {
                viewModelScope.launch {
                    val repo = action.repository

                    val favoriteRepo =
                        FavoriteRepo(
                            repoId = repo.id,
                            repoName = repo.name,
                            repoOwner = repo.fullName.split("/")[0],
                            repoOwnerAvatarUrl = _state.value.profile?.avatarUrl ?: "",
                            repoDescription = repo.description,
                            primaryLanguage = repo.language,
                            repoUrl = repo.htmlUrl,
                            latestVersion = repo.latestVersion,
                            latestReleaseUrl = null,
                            addedAt = Clock.System.now().toEpochMilliseconds(),
                            lastSyncedAt = Clock.System.now().toEpochMilliseconds(),
                        )

                    favouritesRepository.toggleFavorite(favoriteRepo)

                    _state.update { state ->
                        val updatedRepos =
                            state.repositories
                                .map {
                                    if (it.id == repo.id) {
                                        it.copy(isFavorite = !it.isFavorite)
                                    } else {
                                        it
                                    }
                                }.toImmutableList()

                        state.copy(repositories = updatedRepos)
                    }
                    applyFiltersAndSort()
                }
            }

            DeveloperProfileAction.OnDismissError -> {
                _state.update { it.copy(errorMessage = null) }
            }

            DeveloperProfileAction.OnRetry -> {
                loadDeveloperData()
            }
        }
    }
}
