package zed.rainxch.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.BrowseFilterStore
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.installation.hasActualUpdate
import zed.rainxch.core.domain.model.installation.isReallyInstalled
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.HiddenReposRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.domain.repository.SearchHistoryRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.core.domain.helpers.ClipboardHelper
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.core.presentation.utils.toUi
import zed.rainxch.domain.repository.SearchRepository
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_share_link
import zed.rainxch.githubstore.core.presentation.res.link_copied_to_clipboard
import zed.rainxch.githubstore.core.presentation.res.no_github_link_in_clipboard
import zed.rainxch.githubstore.core.presentation.res.explore_error
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded_retry_in
import zed.rainxch.githubstore.core.presentation.res.search_failed
import zed.rainxch.search.presentation.mappers.toDomain
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi
import zed.rainxch.search.presentation.model.SearchSourceUi
import zed.rainxch.search.presentation.model.SortByUi
import zed.rainxch.search.presentation.utils.isEntirelyGithubSearchInput
import zed.rainxch.search.presentation.utils.parseGithubUrls
import zed.rainxch.search.presentation.utils.parseGithubSearchInput

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val logger: KomiStoreLogger,
    private val shareManager: ShareManager,
    private val platform: Platform,
    private val clipboardHelper: ClipboardHelper,
    private val tweaksRepository: TweaksRepository,
    private val seenReposRepository: SeenReposRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val userSessionRepository: UserSessionRepository,
    private val hiddenReposRepository: HiddenReposRepository,
    private val browseFilterStore: BrowseFilterStore,
    private val initialPlatform: DiscoveryPlatform? = null,
) : ViewModel() {
    private var hasLoadedInitialData = false
    private var currentSearchJob: Job? = null
    private var currentPage = 1
    private var explorePage = 1
    private var lastExploreQuery = ""

    @Volatile
    private var currentUserLogin: String? = null

    private val exploreLog = logger.withTag("SearchExplore")

    companion object {
        private const val MIN_QUERY_LENGTH = 2
    }

    private val _state = MutableStateFlow(SearchState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    initialPlatform?.let { platform ->
                        _state.update { it.copy(selectedSearchPlatform = platform) }
                    }

                    observeCurrentUser()
                    syncSystemState()

                    observeInstalledApps()
                    observeFavouriteApps()
                    observeStarredRepos()
                    observeSeenRepos()
                    observeHiddenRepos()
                    observeHideSeenEnabled()
                    observeCustomForgeHosts()
                    observeClipboardSetting()
                    observeSearchHistory()
                    checkClipboardForLinks()
                    if (isDesktop()) observeBrowseFilter()

                    hasLoadedInitialData = true
                }
            }
            .map {
                it.copy(
                    visibleRepos = computeVisibleRepos(it),
                    activeFilterCount = computeActiveFilterCount(it),
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = SearchState(),
            )

    private fun computeVisibleRepos(state: SearchState): ImmutableList<DiscoveryRepositoryUi> {
        val hidden = state.hiddenRepoIds
        val needsHideSeenFilter = state.isHideSeenEnabled && state.seenRepoIds.isNotEmpty()
        if (hidden.isEmpty() && !needsHideSeenFilter) return state.repositories
        return state.repositories
            .filter { repo ->
                repo.repository.id !in hidden &&
                        (!needsHideSeenFilter || repo.repository.id !in state.seenRepoIds)
            }
            .toImmutableList()
    }

    private fun computeActiveFilterCount(state: SearchState): Int {
        var count = 0
        if (state.selectedSource != SearchSourceUi.GitHub) count++
        if (state.selectedSearchPlatform != DiscoveryPlatform.All) count++
        if (state.selectedLanguage != ProgrammingLanguageUi.All) count++
        if (state.selectedSortBy != SortByUi.BestMatch) count++
        return count
    }

    private fun observeBrowseFilter() {
        viewModelScope.launch {
            browseFilterStore.platform.collect { p ->
                onAction(SearchAction.OnPlatformTypeSelected(p))
            }
        }
    }

    private fun observeSeenRepos() {
        viewModelScope.launch {
            seenReposRepository.getAllSeenRepoIds().collect { ids ->
                _state.update { current ->
                    current.copy(
                        seenRepoIds = ids,
                        repositories =
                            current.repositories
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

    private fun observeCustomForgeHosts() {
        viewModelScope.launch {
            tweaksRepository.getCustomForgeHosts().collect { hosts ->
                val base = listOf(SearchSourceUi.GitHub, SearchSourceUi.Codeberg)
                val extra = hosts
                    .filter { it.isNotBlank() && !it.equals("codeberg.org", ignoreCase = true) }
                    .sorted()
                    .map { SearchSourceUi.CustomForge(it) }
                val all = (base + extra).toImmutableList()
                _state.update { current ->
                    val stillValid = all.contains(current.selectedSource)
                    current.copy(
                        availableSources = all,
                        selectedSource = if (stillValid) current.selectedSource else SearchSourceUi.GitHub,
                    )
                }
            }
        }
    }

    private fun observeHiddenRepos() {
        viewModelScope.launch {
            hiddenReposRepository.getAllHiddenRepoIds().collect { ids ->
                _state.update { it.copy(hiddenRepoIds = ids) }
            }
        }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            userSessionRepository.getUser().collect { user ->
                currentUserLogin = user?.username
                val login = user?.username
                _state.update { current ->
                    current.copy(
                        repositories =
                            current.repositories
                                .map { repo ->
                                    repo.copy(
                                        isCurrentUserOwner = login != null &&
                                                repo.repository.owner.login.equals(
                                                    login,
                                                    ignoreCase = true
                                                ),
                                    )
                                }
                                .toImmutableList(),
                    )
                }
            }
        }
    }

    private val _events = Channel<SearchEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private fun syncSystemState() {
        viewModelScope.launch {
            try {
                val result = syncInstalledAppsUseCase()
                if (result.isFailure) {
                    logger.warn("Initial sync had issues: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Initial sync failed: ${e.message}")
            }
        }
    }

    private fun observeClipboardSetting() {
        viewModelScope.launch {
            tweaksRepository.getAutoDetectClipboardLinks().collect { enabled ->
                _state.update { current ->
                    current.copy(
                        autoDetectClipboardEnabled = enabled,
                        clipboardLinks = if (enabled) current.clipboardLinks else persistentListOf(),
                        isClipboardBannerVisible = if (enabled) current.isClipboardBannerVisible else false,
                    )
                }
                if (enabled) checkClipboardForLinks()
            }
        }
    }

    private fun checkClipboardForLinks() {
        viewModelScope.launch {
            val enabled = tweaksRepository.getAutoDetectClipboardLinks().first()
            if (!enabled) return@launch

            try {
                val clipText = clipboardHelper.getText() ?: return@launch
                val links = parseGithubUrls(clipText).toImmutableList()
                if (links.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            clipboardLinks = links,
                            isClipboardBannerVisible = true,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.debug("Failed to read clipboard: ${e.message}")
            }
        }
    }

    private fun observeSearchHistory() {
        viewModelScope.launch {
            searchHistoryRepository.getRecentSearches().collect { searches ->
                _state.update {
                    it.copy(recentSearches = searches.toImmutableList())
                }
            }
        }
    }

    private fun saveSearchToHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return
        viewModelScope.launch {
            searchHistoryRepository.addSearch(trimmed)
        }
    }

    private fun observeInstalledApps() {
        viewModelScope.launch {
            installedAppsRepository
                .getAllInstalledApps()
                .collect { installedApps ->
                    val installedMap = installedApps.groupBy { it.repoId }
                    _state.update { current ->
                        current.copy(
                            repositories =
                                current.repositories
                                    .map { searchRepo ->
                                        val apps = installedMap[searchRepo.repository.id].orEmpty()
                                        searchRepo.copy(
                                            isInstalled = apps.any { it.isReallyInstalled() },
                                            isUpdateAvailable = apps.any { it.hasActualUpdate() },
                                        )
                                    }.toImmutableList(),
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
                            current.repositories
                                .map { searchRepo ->
                                    val app = installedMap[searchRepo.repository.id]
                                    searchRepo.copy(
                                        isFavourite = app != null,
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
                val installedMap = starredRepos.associateBy { it.repoId }
                _state.update { current ->
                    current.copy(
                        repositories =
                            current.repositories
                                .map { searchRepo ->
                                    val app = installedMap[searchRepo.repository.id]
                                    searchRepo.copy(isStarred = app != null)
                                }.toImmutableList(),
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
                        repositories = persistentListOf(),
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
            explorePage = 1
            lastExploreQuery = query
            _state.update {
                it.copy(
                    exploreStatus = SearchState.ExploreStatus.IDLE,
                    passthroughAttempted = null,
                )
            }
        }

        currentSearchJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = isInitial,
                    isLoadingMore = !isInitial,
                    errorMessage = null,
                    repositories =
                        if (isInitial) {
                            persistentListOf()
                        } else {
                            it.repositories
                        },
                    totalCount = if (isInitial) null else it.totalCount,
                    passthroughAttempted =
                        if (isInitial) null else it.passthroughAttempted,
                )
            }

            try {
                val installedMap =
                    installedAppsRepository
                        .getAllInstalledApps()
                        .first()
                        .groupBy { it.repoId }
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
                        platform = _state.value.selectedSearchPlatform,
                        language = _state.value.selectedLanguage.toDomain(),
                        sortBy = _state.value.selectedSortBy.toDomain(),
                        sortOrder = _state.value.selectedSortOrder.toDomain(),
                        page = currentPage,
                        source = _state.value.selectedSource.toDomain(),
                    ).collect { paginatedRepos ->
                        currentPage = paginatedRepos.nextPageIndex

                        val seenIds = _state.value.seenRepoIds
                        val currentLogin = currentUserLogin

                        val newReposWithStatus =
                            paginatedRepos.repos.map { repo ->
                                val apps = installedMap[repo.id].orEmpty()
                                val favourite = favoritesMap[repo.id]
                                val starred = starredReposMap[repo.id]

                                DiscoveryRepositoryUi(
                                    isInstalled = apps.any { it.isReallyInstalled() },
                                    isFavourite = favourite != null,
                                    isStarred = starred != null,
                                    isSeen = repo.id in seenIds,
                                    isCurrentUserOwner =
                                        currentLogin != null &&
                                                repo.owner.login.equals(
                                                    currentLogin,
                                                    ignoreCase = true
                                                ),
                                    isUpdateAvailable = apps.any { it.hasActualUpdate() },
                                    repository = repo.toUi(),
                                )
                            }

                        _state.update { currentState ->
                            val mergedMap = LinkedHashMap<Long, DiscoveryRepositoryUi>()

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

                            val allRepos = mergedMap.values.toImmutableList()

                            currentState.copy(
                                repositories = allRepos,
                                hasMorePages = paginatedRepos.hasMore,
                                totalCount = allRepos.size,
                                errorMessage = null,
                                passthroughAttempted = paginatedRepos.passthroughAttempted,
                            )
                        }
                    }

                _state.update {
                    it.copy(isLoading = false, isLoadingMore = false)
                }
            } catch (e: RateLimitException) {
                logger.debug("Rate limit exceeded: ${e.message}")
                val seconds = e.rateLimitInfo.timeUntilReset().inWholeSeconds
                val message = if (seconds > 0L) {
                    getString(Res.string.rate_limit_exceeded_retry_in, seconds.toInt())
                } else {
                    getString(Res.string.rate_limit_exceeded)
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = message,
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

                    performSearch(isInitial = true)
                }
            }

            is SearchAction.OnSourceSelected -> {
                if (_state.value.selectedSource != action.source) {
                    _state.update { it.copy(selectedSource = action.source) }
                    currentPage = 1
                    performSearch(isInitial = true)
                }
            }

            is SearchAction.OnLanguageSelected -> {
                if (_state.value.selectedLanguage != action.language) {
                    _state.update {
                        it.copy(selectedLanguage = action.language)
                    }
                    currentPage = 1

                    performSearch(isInitial = true)
                }
            }

            is SearchAction.OnSearchChange -> {
                val links = parseGithubSearchInput(action.query)
                _state.update {
                    it.copy(
                        query = action.query,
                        detectedLinks = links,
                    )
                }

                if (action.query.isBlank()) {
                    currentSearchJob?.cancel()
                    _state.update {
                        it.copy(
                            repositories = persistentListOf(),
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = null,
                            totalCount = null,
                        )
                    }
                } else if (isEntirelyGithubSearchInput(action.query)) {
                    currentSearchJob?.cancel()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = null,
                            repositories = persistentListOf(),
                            totalCount = null,
                        )
                    }
                }
            }

            SearchAction.OnToggleLanguageSheetVisibility -> {
                _state.update {
                    it.copy(isLanguageSheetVisible = !it.isLanguageSheetVisible)
                }
            }

            SearchAction.OnToggleFiltersSheet -> {
                _state.update {
                    it.copy(isFiltersSheetVisible = !it.isFiltersSheetVisible)
                }
            }

            SearchAction.OnSearchImeClick -> {
                if (
                    _state.value.detectedLinks.isNotEmpty() &&
                    isEntirelyGithubSearchInput(_state.value.query)
                ) {
                    val link = _state.value.detectedLinks.first()
                    viewModelScope.launch {
                        _events.send(SearchEvent.NavigateToRepo(link.owner, link.repo))
                    }
                    return
                }

                currentPage = 1
                saveSearchToHistory(_state.value.query)
                performSearch(isInitial = true)
            }

            is SearchAction.OnShareClick -> {
                viewModelScope.launch {
                    runCatching {
                        shareManager.shareText("https://github-store.org/app?repo=${action.repo.fullName}")
                    }.onFailure { t ->
                        if (t is CancellationException) throw t
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

                    performSearch(isInitial = true)
                }
            }

            is SearchAction.OnSortOrderSelected -> {
                if (_state.value.selectedSortOrder != action.sortOrder) {
                    _state.update {
                        it.copy(selectedSortOrder = action.sortOrder)
                    }
                    currentPage = 1

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

                performSearch(isInitial = true)
            }

            SearchAction.OnClearClick -> {
                _state.update {
                    it.copy(
                        query = "",
                        repositories = persistentListOf(),
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = null,
                        totalCount = null,
                        detectedLinks = persistentListOf(),
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
                                    repositories = persistentListOf(),
                                    totalCount = null,
                                    isLoading = false,
                                    isLoadingMore = false,
                                    errorMessage = null,
                                )
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
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

            is SearchAction.OnRepositoryClick -> Unit

            SearchAction.OnNavigateBackClick -> Unit

            is SearchAction.OnRepositoryDeveloperClick -> Unit

            is SearchAction.OnHistoryItemClick -> {
                _state.update {
                    it.copy(
                        query = action.query,
                        detectedLinks = persistentListOf(),
                    )
                }

                currentPage = 1
                saveSearchToHistory(action.query)
                performSearch(isInitial = true)
            }

            is SearchAction.OnRemoveHistoryItem -> {
                viewModelScope.launch {
                    searchHistoryRepository.removeSearch(action.query)
                }
            }

            SearchAction.OnClearAllHistory -> {
                viewModelScope.launch {
                    searchHistoryRepository.clearAll()
                }
            }

            SearchAction.ExploreFromGithub -> {
                performExplore()
            }

            SearchAction.OnDisableHideSeenForResults -> {
                viewModelScope.launch {
                    tweaksRepository.setHideSeenEnabled(false)
                }
            }

            is SearchAction.OnHideRepository -> {
                val repo = action.repo
                viewModelScope.launch {
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
            }

            is SearchAction.OnUndoHideRepository -> {
                viewModelScope.launch {
                    try {
                        hiddenReposRepository.unhide(action.repoId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logger.warn("Unhide repository failed for ${action.repoId}: ${e.message}")
                    }
                }
            }

            is SearchAction.OnMarkAsSeen -> {
                val repo = action.repo
                viewModelScope.launch {
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
            }

            is SearchAction.OnMarkAsUnseen -> {
                viewModelScope.launch {
                    try {
                        seenReposRepository.removeFromHistory(action.repoId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logger.warn("Mark as unseen failed for ${action.repoId}: ${e.message}")
                    }
                }
            }
        }
    }

    private fun performExplore() {
        val query = _state.value.query.trim()
        val platformUi = _state.value.selectedSearchPlatform
        val prevStatus = _state.value.exploreStatus

        exploreLog.debug(
            "click: query='$query' platform=$platformUi " +
                    "page=$explorePage lastQuery='$lastExploreQuery' status=$prevStatus",
        )

        if (query.isBlank()) {
            exploreLog.debug("skipped: query is blank")
            return
        }
        if (prevStatus == SearchState.ExploreStatus.LOADING) {
            exploreLog.debug("skipped: already LOADING")
            return
        }

        if (query != lastExploreQuery) {
            exploreLog.debug(
                "query changed ('$lastExploreQuery' -> '$query'); resetting page to 1",
            )
            explorePage = 1
            lastExploreQuery = query
        }

        viewModelScope.launch {
            _state.update { it.copy(exploreStatus = SearchState.ExploreStatus.LOADING) }

            try {
                val exploreResult = searchRepository.exploreFromGithub(
                    query = query,
                    platform = platformUi,
                    page = explorePage,
                )
                val existingCount = _state.value.repositories.size
                exploreLog.debug(
                    "response: items=${exploreResult.repos.size} " +
                            "returnedPage=${exploreResult.page} hasMore=${exploreResult.hasMore} " +
                            "existingVisible=$existingCount",
                )

                val before = _state.value.repositories.size
                if (exploreResult.repos.isNotEmpty()) {
                    appendExploreResults(exploreResult.repos)
                }
                val added = _state.value.repositories.size - before
                val dupes = exploreResult.repos.size - added

                if (exploreResult.hasMore) {
                    explorePage++
                    exploreLog.debug(
                        "-> IDLE: appended=$added dupes=$dupes nextPage=$explorePage",
                    )
                    _state.update { it.copy(exploreStatus = SearchState.ExploreStatus.IDLE) }
                } else {
                    exploreLog.debug(
                        "-> EXHAUSTED: appended=$added dupes=$dupes " +
                                "rawItems=${exploreResult.repos.size}",
                    )
                    _state.update { it.copy(exploreStatus = SearchState.ExploreStatus.EXHAUSTED) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exploreLog.error("failed: ${e::class.simpleName}: ${e.message}", e)
                _state.update { it.copy(exploreStatus = SearchState.ExploreStatus.IDLE) }
                _events.send(SearchEvent.OnMessage(getString(Res.string.explore_error)))
            }
        }
    }

    private suspend fun appendExploreResults(
        newRepos: List<GithubRepoSummary>,
    ) {
        val installedMap =
            installedAppsRepository.getAllInstalledApps().first().groupBy { it.repoId }
        val favoritesMap = favouritesRepository.getAllFavorites().first().associateBy { it.repoId }
        val starredMap = starredRepository.getAllStarred().first().associateBy { it.repoId }
        val seenIds = _state.value.seenRepoIds
        val currentLogin = currentUserLogin

        val existingIds = _state.value.repositories.map { it.repository.id }.toSet()

        val deduped = newRepos
            .filter { it.id !in existingIds }
            .map { repo ->
                val apps = installedMap[repo.id].orEmpty()
                DiscoveryRepositoryUi(
                    isInstalled = apps.any { it.isReallyInstalled() },
                    isFavourite = favoritesMap[repo.id] != null,
                    isStarred = starredMap[repo.id] != null,
                    isSeen = repo.id in seenIds,
                    isCurrentUserOwner =
                        currentLogin != null &&
                                repo.owner.login.equals(currentLogin, ignoreCase = true),
                    isUpdateAvailable = apps.any { it.hasActualUpdate() },
                    repository = repo.toUi(),
                )
            }

        if (deduped.isNotEmpty()) {
            _state.update { current ->
                current.copy(
                    repositories = (current.repositories + deduped).toImmutableList(),
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentSearchJob?.cancel()
    }
}
