package zed.rainxch.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.DiscoveryPlatform
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.utils.ShareManager
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.core.presentation.utils.toUi
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.domain.model.TopicCategory
import zed.rainxch.home.domain.repository.HomeRepository
import zed.rainxch.home.presentation.HomeEvent.*

class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val platform: Platform,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val logger: GitHubStoreLogger,
    private val shareManager: ShareManager,
    private val tweaksRepository: TweaksRepository,
    private val seenReposRepository: SeenReposRepository,
) : ViewModel() {
    private var hasLoadedInitialData = false
    private var currentJob: Job? = null
    private var switchCategoryJob: Job? = null
    private var topicSupplementJob: Job? = null
    private var nextPageIndex = 1

    private val _state = MutableStateFlow(HomeState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    syncSystemState()

                    loadPlatform()
                    loadRepos(isInitial = true)
                    observeInstalledApps()
                    observeFavourites()
                    observeStarredRepos()
                    observeLiquidGlassEnabled()
                    observeSeenRepos()
                    observeHideSeenEnabled()

                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = HomeState(),
            )

    private val _events = Channel<HomeEvent>()
    val events = _events.receiveAsFlow()

    private fun syncSystemState() {
        viewModelScope.launch {
            try {
                val result = syncInstalledAppsUseCase()
                if (result.isFailure) {
                    logger.warn("Initial sync had issues: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                logger.error("Initial sync failed: ${e.message}")
            }
        }
    }

    private fun loadPlatform() {
        _state.update {
            it.copy(isAppsSectionVisible = platform == Platform.ANDROID)
        }
    }

    private fun observeInstalledApps() {
        viewModelScope.launch {
            installedAppsRepository.getAllInstalledApps().collect { installedApps ->
                val installedMap = installedApps.associateBy { it.repoId }
                _state.update { current ->
                    current.copy(
                        repos =
                            current.repos
                                .map { homeRepo ->
                                    val app = installedMap[homeRepo.repository.id]
                                    homeRepo.copy(
                                        isInstalled = app != null,
                                        isUpdateAvailable = app?.isUpdateAvailable ?: false,
                                    )
                                }.toImmutableList(),
                        isUpdateAvailable = installedMap.any { it.value.isUpdateAvailable },
                    )
                }
            }
        }
    }

    private fun loadRepos(
        isInitial: Boolean = false,
        category: HomeCategory? = null,
        platform: DiscoveryPlatform? = null,
        topic: TopicCategory? = null,
        topicExplicitlySet: Boolean = false,
    ): Job? {
        currentJob?.cancel()
        topicSupplementJob?.cancel()

        if (_state.value.isLoading || _state.value.isLoadingMore) {
            logger.debug("Already loading, skipping...")
            return null
        }

        if (isInitial) {
            nextPageIndex = 1
        }

        val targetCategory = category ?: _state.value.currentCategory
        val targetPlatform = platform ?: _state.value.currentPlatform
        val targetTopic = if (topicExplicitlySet) topic else _state.value.selectedTopic

        logger.debug("Loading repos: category=$targetCategory, topic=$targetTopic, page=$nextPageIndex, isInitial=$isInitial")

        return viewModelScope
            .launch {
                _state.update {
                    it.copy(
                        isLoading = isInitial,
                        isLoadingMore = !isInitial,
                        errorMessage = null,
                        currentCategory = targetCategory,
                        currentPlatform = targetPlatform,
                        selectedTopic = targetTopic,
                        repos = if (isInitial) persistentListOf() else it.repos,
                    )
                }

                try {
                    val flow =
                        when (targetCategory) {
                            HomeCategory.TRENDING -> {
                                homeRepository.getTrendingRepositories(
                                    platform = targetPlatform,
                                    page = nextPageIndex,
                                )
                            }

                            HomeCategory.HOT_RELEASE -> {
                                homeRepository.getHotReleaseRepositories(
                                    platform = targetPlatform,
                                    page = nextPageIndex,
                                )
                            }

                            HomeCategory.MOST_POPULAR -> {
                                homeRepository.getMostPopular(
                                    platform = targetPlatform,
                                    page = nextPageIndex,
                                )
                            }
                        }

                    flow.collect { paginatedRepos ->
                        logger.debug(
                            "Received ${paginatedRepos.repos.size} repos, hasMore=${paginatedRepos.hasMore}, nextPage=${paginatedRepos.nextPageIndex}",
                        )

                        this@HomeViewModel.nextPageIndex = paginatedRepos.nextPageIndex

                        val repos =
                            if (targetTopic != null) {
                                paginatedRepos.repos.filter { repo ->
                                    targetTopic.matchesRepo(repo.topics, repo.description, repo.name)
                                }
                            } else {
                                paginatedRepos.repos
                            }

                        val newReposWithStatus = mapReposToUi(repos)

                        _state.update { currentState ->
                            val rawList = currentState.repos + newReposWithStatus
                            val uniqueList = rawList.distinctBy { it.repository.fullName }

                            currentState.copy(
                                repos = uniqueList.toImmutableList(),
                                hasMorePages = paginatedRepos.hasMore,
                                errorMessage =
                                    if (uniqueList.isEmpty() && !paginatedRepos.hasMore) {
                                        getString(Res.string.no_repositories_found)
                                    } else {
                                        null
                                    },
                            )
                        }
                    }

                    logger.debug("Flow completed")
                    _state.update {
                        it.copy(isLoading = false, isLoadingMore = false)
                    }

                    if (targetTopic != null && isInitial) {
                        loadTopicSupplement(targetTopic, targetPlatform)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) {
                        logger.debug("Load cancelled (expected)")
                        throw t
                    }

                    logger.error("Load failed: ${t.message}")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage =
                                t.message
                                    ?: getString(Res.string.home_failed_to_load_repositories),
                        )
                    }
                }
            }.also {
                currentJob = it
            }
    }

    private fun loadTopicSupplement(
        topic: TopicCategory,
        platform: DiscoveryPlatform,
    ) {
        topicSupplementJob?.cancel()
        topicSupplementJob =
            viewModelScope.launch {
                _state.update { it.copy(isLoadingTopicSupplement = true) }

                try {
                    // Phase 1: Load pre-fetched cached topic repos (instant, no API cost)
                    homeRepository
                        .getTopicRepositories(
                            topic = topic,
                            platform = platform,
                        ).collect { paginatedRepos ->
                            if (paginatedRepos.repos.isNotEmpty()) {
                                val cachedReposWithStatus = mapReposToUi(paginatedRepos.repos)

                                _state.update { currentState ->
                                    val merged = (currentState.repos + cachedReposWithStatus)
                                        .distinctBy { it.repository.fullName }

                                    currentState.copy(
                                        repos = merged.toImmutableList(),
                                    )
                                }

                                logger.debug("Loaded ${paginatedRepos.repos.size} cached topic repos for ${topic.name}")
                            }
                        }

                    // Phase 2: Supplement with live GitHub search (fills gaps)
                    homeRepository
                        .searchByTopic(
                            searchKeywords = topic.searchKeywords,
                            platform = platform,
                            page = 1,
                        ).collect { paginatedRepos ->
                            val newReposWithStatus = mapReposToUi(paginatedRepos.repos)

                            _state.update { currentState ->
                                val merged = (currentState.repos + newReposWithStatus)
                                    .distinctBy { it.repository.fullName }

                                currentState.copy(
                                    repos = merged.toImmutableList(),
                                    hasMorePages = currentState.hasMorePages || paginatedRepos.hasMore,
                                )
                            }
                        }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    logger.warn("Topic supplement search failed: ${t.message}")
                } finally {
                    _state.update { it.copy(isLoadingTopicSupplement = false) }
                }
            }
    }

    private suspend fun mapReposToUi(
        repos: List<zed.rainxch.core.domain.model.GithubRepoSummary>,
    ): List<DiscoveryRepositoryUi> {
        val installedAppsMap =
            installedAppsRepository
                .getAllInstalledApps()
                .first()
                .associateBy { it.repoId }

        val favoritesMap =
            favouritesRepository
                .getAllFavorites()
                .first()
                .associateBy { it.repoId }

        val starredReposMap =
            starredRepository
                .getAllStarred()
                .first()
                .associateBy { it.repoId }

        val seenIds = _state.value.seenRepoIds

        return repos.map { repo ->
            val app = installedAppsMap[repo.id]
            val favourite = favoritesMap[repo.id]
            val starred = starredReposMap[repo.id]

            DiscoveryRepositoryUi(
                isInstalled = app != null,
                isFavourite = favourite != null,
                isStarred = starred != null,
                isSeen = repo.id in seenIds,
                isUpdateAvailable = app?.isUpdateAvailable ?: false,
                repository = repo.toUi(),
            )
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> {
                viewModelScope.launch {
                    syncInstalledAppsUseCase()
                    nextPageIndex = 1
                    loadRepos(isInitial = true)
                }
            }

            HomeAction.Retry -> {
                nextPageIndex = 1
                loadRepos(isInitial = true)
            }

            HomeAction.LoadMore -> {
                logger.debug(
                    "LoadMore action: isLoading=${_state.value.isLoading}, isLoadingMore=${_state.value.isLoadingMore}, hasMore=${_state.value.hasMorePages}",
                )

                if (!_state.value.isLoadingMore && !_state.value.isLoading && _state.value.hasMorePages) {
                    loadRepos(isInitial = false)
                }
            }

            is HomeAction.SwitchTopic -> {
                val newTopic = if (_state.value.selectedTopic == action.topic) null else action.topic
                if (_state.value.selectedTopic != newTopic) {
                    nextPageIndex = 1
                    switchCategoryJob?.cancel()
                    switchCategoryJob =
                        viewModelScope.launch {
                            loadRepos(
                                isInitial = true,
                                topic = newTopic,
                                topicExplicitlySet = true,
                            )?.join() ?: return@launch
                            _events.send(HomeEvent.OnScrollToListTop)
                        }
                }
            }

            is HomeAction.SwitchCategory -> {
                if (_state.value.currentCategory != action.category) {
                    nextPageIndex = 1
                    switchCategoryJob?.cancel()
                    switchCategoryJob =
                        viewModelScope.launch {
                            loadRepos(isInitial = true, category = action.category)?.join()
                                ?: return@launch
                            _events.send(HomeEvent.OnScrollToListTop)
                        }
                }
            }

            is HomeAction.OnShareClick -> {
                viewModelScope.launch {
                    runCatching {
                        shareManager.shareText("https://github-store.org/app?repo=${action.repo.fullName}")
                    }.onFailure { t ->
                        logger.error("Failed to share link: ${t.message}")
                        _events.send(
                            OnMessage(getString(Res.string.failed_to_share_link)),
                        )
                        return@launch
                    }

                    if (platform != Platform.ANDROID) {
                        _events.send(OnMessage(getString(Res.string.link_copied_to_clipboard)))
                    }
                }
            }

            is HomeAction.SwitchFilterPlatform -> {
                if (_state.value.currentPlatform != action.platform) {
                    nextPageIndex = 1
                    switchCategoryJob?.cancel()
                    switchCategoryJob =
                        viewModelScope.launch {
                            loadRepos(isInitial = true, platform = action.platform)?.join()
                                ?: return@launch
                            _events.send(HomeEvent.OnScrollToListTop)
                        }
                }
            }

            HomeAction.OnTogglePlatformPopup -> {
                _state.update {
                    it.copy(
                        isPlatformPopupVisible = !it.isPlatformPopupVisible,
                    )
                }
            }

            is HomeAction.OnRepositoryClick -> {
                // Handled in composable
            }

            is HomeAction.OnRepositoryDeveloperClick -> {
                // Handled in composable
            }

            HomeAction.OnSearchClick -> {
                // Handled in composable
            }

            HomeAction.OnSettingsClick -> {
                // Handled in composable
            }

            HomeAction.OnAppsClick -> {
                // Handled in composable
            }
        }
    }

    private fun observeLiquidGlassEnabled() {
        viewModelScope.launch {
            tweaksRepository.getLiquidGlassEnabled().collect { enabled ->
                _state.update {
                    it.copy(isLiquidGlassEnabled = enabled)
                }
            }
        }
    }

    private fun observeSeenRepos() {
        viewModelScope.launch {
            seenReposRepository.getAllSeenRepoIds().collect { ids ->
                _state.update { current ->
                    current.copy(
                        seenRepoIds = ids,
                        repos =
                            current.repos
                                .map { repo ->
                                    repo.copy(isSeen = repo.repository.id in ids)
                                }.toImmutableList(),
                    )
                }
            }
        }
    }

    private fun observeHideSeenEnabled() {
        viewModelScope.launch {
            tweaksRepository.getHideSeenEnabled().collect { enabled ->
                _state.update { it.copy(isHideSeenEnabled = enabled) }
            }
        }
    }

    private fun observeFavourites() {
        viewModelScope.launch {
            favouritesRepository.getAllFavorites().collect { favourites ->
                val favouritesMap = favourites.associateBy { it.repoId }
                _state.update { current ->
                    current.copy(
                        repos =
                            current.repos
                                .map { homeRepo ->
                                    homeRepo.copy(
                                        isFavourite = favouritesMap.containsKey(homeRepo.repository.id),
                                    )
                                }.toImmutableList(),
                    )
                }
            }
        }
    }

    private fun observeStarredRepos() {
        viewModelScope.launch {
            starredRepository.getAllStarred().collect { starredRepos ->
                val starredReposById = starredRepos.associateBy { it.repoId }
                _state.update { current ->
                    current.copy(
                        repos =
                            current.repos
                                .map { homeRepo ->
                                    homeRepo.copy(
                                        isStarred = starredReposById.containsKey(homeRepo.repository.id),
                                    )
                                }.toImmutableList(),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
        topicSupplementJob?.cancel()
    }
}
