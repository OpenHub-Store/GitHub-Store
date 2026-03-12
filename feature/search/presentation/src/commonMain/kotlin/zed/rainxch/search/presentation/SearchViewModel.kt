package zed.rainxch.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.core.domain.model.RateLimitException
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.ThemesRepository
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.core.domain.utils.ClipboardHelper
import zed.rainxch.core.domain.utils.ShareManager
import zed.rainxch.core.presentation.model.DiscoveryRepository
import zed.rainxch.domain.repository.SearchRepository
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.search.presentation.utils.isEntirelyGithubUrls
import zed.rainxch.search.presentation.utils.parseGithubUrls

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val logger: GitHubStoreLogger,
    private val shareManager: ShareManager,
    private val platform: Platform,
    private val clipboardHelper: ClipboardHelper,
    private val themesRepository: ThemesRepository,
) : ViewModel() {
    private var hasLoadedInitialData = false
    private var currentSearchJob: Job? = null
    private var currentPage = 1
    private var searchDebounceJob: Job? = null

    companion object {
        private const val MIN_QUERY_LENGTH = 3
        private const val DEBOUNCE_MS = 800L
    }

    private val _state = MutableStateFlow(SearchState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    syncSystemState()

                    observeInstalledApps()
                    observeFavouriteApps()
                    observeStarredRepos()
                    observeClipboardSetting()
                    checkClipboardForLinks()

                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = SearchState(),
            )

    private val _events = Channel<SearchEvent>()
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

    private fun observeClipboardSetting() {
        viewModelScope.launch {
            themesRepository.getAutoDetectClipboardLinks().collect { enabled ->
                _state.update { current ->
                    current.copy(
                        autoDetectClipboardEnabled = enabled,
                        clipboardLinks = if (enabled) current.clipboardLinks else emptyList(),
                        isClipboardBannerVisible = if (enabled) current.isClipboardBannerVisible else false,
                    )
                }
                if (enabled) checkClipboardForLinks()
            }
        }
    }

    private fun checkClipboardForLinks() {
        viewModelScope.launch {
            val enabled = themesRepository.getAutoDetectClipboardLinks().first()
            if (!enabled) return@launch

            try {
                val clipText = clipboardHelper.getText() ?: return@launch
                val links = parseGithubUrls(clipText)
                if (links.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            clipboardLinks = links,
                            isClipboardBannerVisible = true,
                        )
                    }
                }
            } catch (e: Exception) {
                logger.debug("Failed to read clipboard: ${e.message}")
            }
        }
    }

    private fun observeInstalledApps() {
        viewModelScope.launch {
            installedAppsRepository.getAllInstalledApps().collect { installedApps ->
                val installedMap = installedApps.associateBy { it.repoId }
                _state.update { current ->
                    current.copy(
                        repositories =
                            current.repositories.map { searchRepo ->
                                val app = installedMap[searchRepo.repository.id]
                                searchRepo.copy(
                                    isInstalled = app != null,
                                    isUpdateAvailable = app?.isUpdateAvailable ?: false,
                                )
                            },
                    )
                }
            }
        }
    }

    private fun observeFavouriteApps() {
        viewModelScope.launch {
            favouritesRepository.getAllFavorites().collect { favoriteRepos ->
                val installedMap = favoriteRepos.associateBy { it.repoId }
                _state.update { current ->
                    current.copy(
                        repositories =
                            current.repositories.map { searchRepo ->
                                val app = installedMap[searchRepo.repository.id]
                                searchRepo.copy(
                                    isFavourite = app != null,
                                )
                            },
                    )
                }
            }
        }
    }

    private fun observeStarredRepos() {
        viewModelScope.launch {
            starredRepository.getAllStarred().collect { starredRepos ->
                val installedMap = starredRepos.associateBy { it.repoId }
                _state.update { current ->
                    current.copy(
                        repositories =
                            current.repositories.map { searchRepo ->
                                val app = installedMap[searchRepo.repository.id]
                                searchRepo.copy(isStarred = app != null)
                            },
                    )
                }
            }
        }
    }

    private fun performSearch(isInitial: Boolean = false) {
        val query = _state.value.query.trim()
        if (query.isBlank() || query.length < MIN_QUERY_LENGTH) {
            if (query.isBlank()) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        repositories = emptyList(),
                        errorMessage = null,
                        totalCount = null,
                    )
                }
            }
            return
        }

        if (isInitial) {
            currentSearchJob?.cancel()
            currentPage = 1
        }

        currentSearchJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        isLoading = isInitial,
                        isLoadingMore = !isInitial,
                        errorMessage = null,
                        repositories = if (isInitial) emptyList() else it.repositories,
                        totalCount = if (isInitial) null else it.totalCount,
                    )
                }

                try {
                    val installedMap =
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

                    searchRepository
                        .searchRepositories(
                            query = _state.value.query,
                            searchPlatform = _state.value.selectedSearchPlatform,
                            language = _state.value.selectedLanguage,
                            sortBy = _state.value.selectedSortBy,
                            sortOrder = _state.value.selectedSortOrder,
                            page = currentPage,
                        ).collect { paginatedRepos ->
                            currentPage = paginatedRepos.nextPageIndex

                            val newReposWithStatus =
                                paginatedRepos.repos.map { repo ->
                                    val app = installedMap[repo.id]
                                    val favourite = favoritesMap[repo.id]
                                    val starred = starredReposMap[repo.id]

                                    DiscoveryRepository(
                                        isInstalled = app != null,
                                        isFavourite = favourite != null,
                                        isStarred = starred != null,
                                        isUpdateAvailable = app?.isUpdateAvailable ?: false,
                                        repository = repo,
                                    )
                                }

                            _state.update { currentState ->
                                val mergedMap = LinkedHashMap<Long, DiscoveryRepository>()

                                currentState.repositories.forEach { r ->
                                    mergedMap[r.repository.id] = r
                                }

                                newReposWithStatus.forEach { r ->
                                    val existing = mergedMap[r.repository.id]
                                    if (existing == null) {
                                        mergedMap[r.repository.id] = r
                                    } else {
                                        mergedMap[r.repository.id] =
                                            existing.copy(
                                                isInstalled = r.isInstalled,
                                                isUpdateAvailable = r.isUpdateAvailable,
                                                isFavourite = r.isFavourite,
                                                isStarred = r.isStarred,
                                                repository = r.repository,
                                            )
                                    }
                                }

                                val allRepos = mergedMap.values.toList()

                                currentState.copy(
                                    repositories = allRepos,
                                    hasMorePages = paginatedRepos.hasMore,
                                    totalCount = allRepos.size,
                                    errorMessage =
                                        if (allRepos.isEmpty() && !paginatedRepos.hasMore) {
                                            getString(Res.string.no_repositories_found)
                                        } else {
                                            null
                                        },
                                )
                            }
                        }

                    _state.update {
                        it.copy(isLoading = false, isLoadingMore = false)
                    }
                } catch (e: RateLimitException) {
                    logger.debug("Rate limit exceeded: ${e.message}")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = e.message,
                        )
                    }
                } catch (e: CancellationException) {
                    logger.debug("Search cancelled (expected): ${e.message}")
                } catch (e: Exception) {
                    logger.error("Search failed: ${e.message}")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = e.message ?: getString(Res.string.search_failed),
                        )
                    }
                }
            }
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.OnPlatformTypeSelected -> {
                if (_state.value.selectedSearchPlatform != action.searchPlatform) {
                    _state.update {
                        it.copy(selectedSearchPlatform = action.searchPlatform)
                    }
                    currentPage = 1
                    searchDebounceJob?.cancel()
                    performSearch(isInitial = true)
                }
            }

            is SearchAction.OnLanguageSelected -> {
                if (_state.value.selectedLanguage != action.language) {
                    _state.update {
                        it.copy(selectedLanguage = action.language)
                    }
                    currentPage = 1
                    searchDebounceJob?.cancel()
                    performSearch(isInitial = true)
                }
            }

            is SearchAction.OnSearchChange -> {
                val links = parseGithubUrls(action.query)
                _state.update {
                    it.copy(
                        query = action.query,
                        detectedLinks = links,
                    )
                }

                searchDebounceJob?.cancel()

                if (isEntirelyGithubUrls(action.query)) {
                    currentSearchJob?.cancel()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = null,
                            repositories = emptyList(),
                            totalCount = null,
                        )
                    }
                    return
                }

                if (action.query.isBlank()) {
                    _state.update {
                        it.copy(
                            repositories = emptyList(),
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = null,
                            totalCount = null,
                        )
                    }
                } else if (action.query.trim().length < MIN_QUERY_LENGTH) {
                    currentSearchJob?.cancel()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = null,
                        )
                    }
                } else {
                    searchDebounceJob =
                        viewModelScope.launch {
                            try {
                                delay(DEBOUNCE_MS)
                                currentPage = 1
                                performSearch(isInitial = true)
                            } catch (_: CancellationException) {
                                logger.debug("Debounce cancelled (expected)")
                            }
                        }
                }
            }

            SearchAction.OnToggleLanguageSheetVisibility -> {
                _state.update {
                    it.copy(isLanguageSheetVisible = !it.isLanguageSheetVisible)
                }
            }

            SearchAction.OnSearchImeClick -> {
                if (_state.value.detectedLinks.isNotEmpty() && isEntirelyGithubUrls(_state.value.query)) {
                    val link = _state.value.detectedLinks.first()
                    viewModelScope.launch {
                        _events.send(SearchEvent.NavigateToRepo(link.owner, link.repo))
                    }
                    return
                }
                searchDebounceJob?.cancel()
                currentPage = 1
                performSearch(isInitial = true)
            }

            is SearchAction.OnShareClick -> {
                viewModelScope.launch {
                    runCatching {
                        shareManager.shareText("https://github-store.org/app?repo=${action.repo.fullName}")
                    }.onFailure { t ->
                        logger.error("Failed to share link: ${t.message}")
                        _events.send(
                            SearchEvent.OnMessage(getString(Res.string.failed_to_share_link)),
                        )
                        return@launch
                    }

                    if (platform != Platform.ANDROID) {
                        _events.send(SearchEvent.OnMessage(getString(Res.string.link_copied_to_clipboard)))
                    }
                }
            }

            is SearchAction.OnSortBySelected -> {
                if (_state.value.selectedSortBy != action.sortBy) {
                    _state.update {
                        it.copy(selectedSortBy = action.sortBy)
                    }
                    currentPage = 1
                    searchDebounceJob?.cancel()
                    performSearch(isInitial = true)
                }
            }

            is SearchAction.OnSortOrderSelected -> {
                if (_state.value.selectedSortOrder != action.sortOrder) {
                    _state.update {
                        it.copy(selectedSortOrder = action.sortOrder)
                    }
                    currentPage = 1
                    searchDebounceJob?.cancel()
                    performSearch(isInitial = true)
                }
            }

            SearchAction.OnToggleSortByDialogVisibility -> {
                _state.update {
                    it.copy(isSortByDialogVisible = !it.isSortByDialogVisible)
                }
            }

            SearchAction.LoadMore -> {
                if (!_state.value.isLoadingMore && !_state.value.isLoading && _state.value.hasMorePages) {
                    performSearch(isInitial = false)
                }
            }

            SearchAction.Retry -> {
                currentPage = 1
                searchDebounceJob?.cancel()
                performSearch(isInitial = true)
            }

            SearchAction.OnClearClick -> {
                _state.update {
                    it.copy(
                        query = "",
                        repositories = emptyList(),
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = null,
                        totalCount = null,
                        detectedLinks = emptyList(),
                    )
                }
            }

            is SearchAction.OpenGithubLink -> {
                viewModelScope.launch {
                    _events.send(SearchEvent.NavigateToRepo(action.owner, action.repo))
                }
            }

            SearchAction.OnFabClick -> {
                viewModelScope.launch {
                    try {
                        val clipText = clipboardHelper.getText()
                        if (clipText.isNullOrBlank()) {
                            _events.send(SearchEvent.OnMessage(getString(Res.string.no_github_link_in_clipboard)))
                            return@launch
                        }
                        val links = parseGithubUrls(clipText)
                        if (links.isEmpty()) {
                            _events.send(SearchEvent.OnMessage(getString(Res.string.no_github_link_in_clipboard)))
                            return@launch
                        }
                        if (links.size == 1) {
                            _events.send(
                                SearchEvent.NavigateToRepo(
                                    links.first().owner,
                                    links.first().repo,
                                ),
                            )
                        } else {
                            _state.update {
                                it.copy(
                                    query = clipText,
                                    detectedLinks = links,
                                    repositories = emptyList(),
                                    totalCount = null,
                                    isLoading = false,
                                    isLoadingMore = false,
                                    errorMessage = null,
                                )
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to read clipboard: ${e.message}")
                        _events.send(SearchEvent.OnMessage(getString(Res.string.no_github_link_in_clipboard)))
                    }
                }
            }

            SearchAction.DismissClipboardBanner -> {
                _state.update {
                    it.copy(isClipboardBannerVisible = false)
                }
            }

            is SearchAction.OnRepositoryClick -> {
                // Handled in composable
            }

            SearchAction.OnNavigateBackClick -> {
                // Handled in composable
            }

            is SearchAction.OnRepositoryDeveloperClick -> {
                // Handled in composable
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentSearchJob?.cancel()
        searchDebounceJob?.cancel()
    }
}
