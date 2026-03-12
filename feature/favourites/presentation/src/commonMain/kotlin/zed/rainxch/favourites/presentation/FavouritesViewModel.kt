package zed.rainxch.favourites.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.model.FavoriteRepo
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.favourites.presentation.mappers.toFavouriteRepositoryUi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FavouritesViewModel(
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {
    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(FavouritesState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadFavouriteRepos()

                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = FavouritesState(),
            )

    private fun loadFavouriteRepos() {
        viewModelScope.launch {
            favouritesRepository
                .getAllFavorites()
                .map { it.map { it.toFavouriteRepositoryUi() } }
                .flowOn(Dispatchers.Default)
                .collect { favoriteRepos ->
                    _state.update {
                        it.copy(
                            favouriteRepositories = favoriteRepos.toImmutableList(),
                        )
                    }
                }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun onAction(action: FavouritesAction) {
        when (action) {
            FavouritesAction.OnNavigateBackClick -> {
                // Handled in composable
            }

            is FavouritesAction.OnRepositoryClick -> {
                // Handled in composable
            }

            is FavouritesAction.OnDeveloperProfileClick -> {
                // Handled in composable
            }

            is FavouritesAction.OnToggleFavorite -> {
                viewModelScope.launch {
                    val repo = action.favouriteRepository

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
