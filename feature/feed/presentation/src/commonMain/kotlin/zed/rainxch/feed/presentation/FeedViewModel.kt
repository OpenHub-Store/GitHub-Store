package zed.rainxch.feed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.getPlatform
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.hasActualUpdate
import zed.rainxch.core.domain.model.installation.isReallyInstalled
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.BrowseFilterStore
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.HiddenReposRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.model.repository.FeedCategory
import zed.rainxch.feed.domain.repository.FeedRepository
import zed.rainxch.feed.presentation.model.toDiscoveryRepoUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_share_link
import zed.rainxch.githubstore.core.presentation.res.feed_failed_to_load
import zed.rainxch.githubstore.core.presentation.res.link_copied_to_clipboard

/**
 * ViewModel responsible for managing the state, loading feed repositories,
 * filtering by platforms and categories, and handling user interactions on the Feed screen.
 */
class FeedViewModel(
    private val feedRepository: FeedRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val seenReposRepository: SeenReposRepository,
    private val hiddenReposRepository: HiddenReposRepository,
    private val userSessionRepository: UserSessionRepository,
    private val tweaksRepository: TweaksRepository,
    private val browseFilterStore: BrowseFilterStore,
    private val logger: KomiStoreLogger,
    private val shareManager: ShareManager,
) : ViewModel() {

    private var hasLoadedInitialData = false
    private var loadJob: Job? = null
    private var pageJob: Job? = null

    private var feedRepos: List<GithubRepoSummary> = emptyList()
    private var nextPage = 1
    private var hasMore = false
    private var isOffline = false
    private var selectedPlatform = detectedPlatform()
    private var selectedCategory = FeedCategory.All

    private var installedById: Map<Long, List<InstalledApp>> = emptyMap()
    private var favouriteIds: Set<Long> = emptySet()
    private var starredIds: Set<Long> = emptySet()
    private var seenIds: Set<Long> = emptySet()
    private var hiddenIds: Set<Long> = emptySet()
    private var isHideSeenEnabled = false
    private var currentUserLogin: String? = null

    private val _state = MutableStateFlow(FeedState(selectedPlatform = selectedPlatform))
    val state: StateFlow<FeedState> = _state
        .onStart {
            if (!hasLoadedInitialData) {
                selectedPlatform = browseFilterStore.platform.value
                selectedCategory = browseFilterStore.category.value
                _state.update {
                    it.copy(selectedPlatform = selectedPlatform, selectedCategory = selectedCategory)
                }
                observeCurrentUser()
                observeInstalledApps()
                observeFavourites()
                observeStarredRepos()
                observeSeenRepos()
                observeHiddenRepos()
                observeHideSeenEnabled()
                observeBrowseFilter()
                reload(isRefresh = false)
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = FeedState(selectedPlatform = selectedPlatform),
        )

    private val _events = Channel<FeedEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Handles UI actions dispatched from the feed composables.
     *
     * @param action The user action to process.
     */
    fun onAction(action: FeedAction) {
        when (action) {
            FeedAction.OnRefresh -> reload(isRefresh = true)

            FeedAction.OnRetry -> reload(isRefresh = false)

            FeedAction.OnLoadMore -> loadMore()

            FeedAction.OnPlatformPickerOpen ->
                _state.update { it.copy(isPlatformPickerVisible = true) }

            FeedAction.OnPlatformPickerDismiss ->
                _state.update { it.copy(isPlatformPickerVisible = false) }

            FeedAction.OnResetFilters -> {
                browseFilterStore.setCategory(FeedCategory.All)
                browseFilterStore.setPlatform(DiscoveryPlatform.All)
            }

            is FeedAction.OnPlatformSelected -> {
                _state.update { it.copy(isPlatformPickerVisible = false) }
                browseFilterStore.setPlatform(action.platform)
            }

            is FeedAction.OnCategorySelected -> {
                browseFilterStore.setCategory(action.category)
            }

            FeedAction.OnToggleLayoutType -> {
                _state.update {
                    val nextType = if (it.layoutType == FeedLayoutType.LIST) FeedLayoutType.GRID else FeedLayoutType.LIST
                    it.copy(layoutType = nextType)
                }
            }

            is FeedAction.OnShareClick -> viewModelScope.launch {
                runCatching {
                    shareManager.shareText("https://github-store.org/app?repo=${action.repo.fullName}")
                }.onFailure { t ->
                    logger.error("Failed to share link: ${t.message}")
                    _events.send(FeedEvent.OnMessage(getString(Res.string.failed_to_share_link)))
                    return@launch
                }
                if (isDesktop()) {
                    _events.send(FeedEvent.OnMessage(getString(Res.string.link_copied_to_clipboard)))
                }
            }

            is FeedAction.OnHideRepository -> viewModelScope.launch {
                val repo = action.repo
                try {
                    hiddenReposRepository.hide(
                        repoId = repo.id,
                        repoName = repo.name,
                        repoOwner = repo.owner.login,
                        repoOwnerAvatarUrl = repo.owner.avatarUrl,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.warn("Hide repository failed for ${repo.id}: ${e.message}")
                }
            }

            is FeedAction.OnMarkAsSeen -> viewModelScope.launch {
                val repo = action.repo
                try {
                    seenReposRepository.markAsSeen(
                        repoId = repo.id,
                        repoName = repo.name,
                        repoOwner = repo.owner.login,
                        repoOwnerAvatarUrl = repo.owner.avatarUrl,
                        repoDescription = repo.description,
                        primaryLanguage = repo.language,
                        repoUrl = repo.htmlUrl,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.warn("Mark as seen failed for ${repo.id}: ${e.message}")
                }
            }

            is FeedAction.OnMarkAsUnseen -> viewModelScope.launch {
                try {
                    seenReposRepository.removeFromHistory(action.repoId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.warn("Mark as unseen failed for ${action.repoId}: ${e.message}")
                }
            }

            FeedAction.OnSearchClick,
            FeedAction.OnProfileClick,
            is FeedAction.OnRepoClick -> Unit
        }
    }

    private fun reload(isRefresh: Boolean) {
        loadJob?.cancel()
        pageJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isRefreshing = isRefresh,
                    isLoading = !isRefresh && feedRepos.isEmpty(),
                    errorMessage = null,
                )
            }
            loadPage(page = 1, replace = true, forceRefresh = isRefresh)
        }
    }

    private fun loadMore() {
        if (!hasMore || _state.value.isLoadingMore || _state.value.isLoading) return
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            loadPage(page = nextPage, replace = false, forceRefresh = false)
        }
    }

    private suspend fun loadPage(page: Int, replace: Boolean, forceRefresh: Boolean) {
        if (!replace) _state.update { it.copy(isLoadingMore = true) }

        feedRepository.getFeed(selectedPlatform, page, forceRefresh).fold(
            onSuccess = { feedPage ->
                feedRepos = if (replace) {
                    feedPage.items
                } else {
                    val existing = feedRepos.mapTo(HashSet()) { it.id }
                    feedRepos + feedPage.items.filter { it.id !in existing }
                }
                nextPage = feedPage.page + 1
                hasMore = feedPage.hasMore
                isOffline = feedPage.isOffline
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = null,
                    )
                }
                rebuild()
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                logger.error("Feed load failed (page $page): ${error.message}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = if (feedRepos.isEmpty()) {
                            error.message ?: getString(Res.string.feed_failed_to_load)
                        } else {
                            it.errorMessage
                        },
                    )
                }
            },
        )
    }

    private fun rebuild() {
        val visible = feedRepos
            .filter { repo -> repo.id !in hiddenIds && (!isHideSeenEnabled || repo.id !in seenIds) }
            .filter { repo -> selectedCategory.matches(repo.topicCodes) }
            .map { it.toCard() }
            .toImmutableList()

        _state.update {
            it.copy(
                repos = visible,
                hasMore = hasMore,
                isOffline = isOffline,
            )
        }
    }

    private fun GithubRepoSummary.toCard() =
        toDiscoveryRepoUi(
            isInstalled = installedById[id].orEmpty().any { it.isReallyInstalled() },
            isUpdateAvailable = installedById[id].orEmpty().any { it.hasActualUpdate() },
            isFavourite = id in favouriteIds,
            isStarred = id in starredIds,
            isSeen = id in seenIds,
            isCurrentUserOwner = currentUserLogin != null &&
                owner.login.equals(currentUserLogin, ignoreCase = true),
        )

    private fun observeBrowseFilter() {
        viewModelScope.launch {
            browseFilterStore.platform.collect { p ->
                if (p != selectedPlatform) {
                    selectedPlatform = p
                    _state.update { it.copy(selectedPlatform = p) }
                    _events.send(FeedEvent.OnScrollToTop)
                    reload(isRefresh = false)
                }
            }
        }
        viewModelScope.launch {
            browseFilterStore.category.collect { c ->
                if (c != selectedCategory) {
                    selectedCategory = c
                    _state.update { it.copy(selectedCategory = c) }
                    _events.send(FeedEvent.OnScrollToTop)
                    rebuild()
                }
            }
        }
    }

    private fun detectedPlatform(): DiscoveryPlatform = when (getPlatform()) {
        Platform.ANDROID -> DiscoveryPlatform.Android
        Platform.WINDOWS -> DiscoveryPlatform.Windows
        Platform.MACOS -> DiscoveryPlatform.Macos
        Platform.LINUX -> DiscoveryPlatform.Linux
    }

    private fun observeInstalledApps() {
        viewModelScope.launch {
            installedAppsRepository.getAllInstalledApps().collect { apps ->
                installedById = apps.groupBy { it.repoId }
                rebuild()
            }
        }
    }

    private fun observeFavourites() {
        viewModelScope.launch {
            favouritesRepository.getAllFavorites().collect { favourites ->
                favouriteIds = favourites.map { it.repoId }.toSet()
                rebuild()
            }
        }
    }

    private fun observeStarredRepos() {
        viewModelScope.launch {
            starredRepository.getAllStarred().collect { starred ->
                starredIds = starred.map { it.repoId }.toSet()
                rebuild()
            }
        }
    }

    private fun observeSeenRepos() {
        viewModelScope.launch {
            seenReposRepository.getAllSeenRepoIds().collect { ids ->
                seenIds = ids
                rebuild()
            }
        }
    }

    private fun observeHiddenRepos() {
        viewModelScope.launch {
            hiddenReposRepository.getAllHiddenRepoIds().collect { ids ->
                hiddenIds = ids
                rebuild()
            }
        }
    }

    private fun observeHideSeenEnabled() {
        viewModelScope.launch {
            tweaksRepository.getHideSeenEnabled().collect { enabled ->
                isHideSeenEnabled = enabled
                rebuild()
            }
        }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            userSessionRepository.getUser().collect { user ->
                currentUserLogin = user?.username
                rebuild()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        pageJob?.cancel()
    }
}
