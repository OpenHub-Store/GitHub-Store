package zed.rainxch.home.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.GithubRepoSummary
import zed.rainxch.core.presentation.components.GithubStoreButton
import zed.rainxch.core.presentation.components.RepositoryCard
import zed.rainxch.core.presentation.locals.LocalBottomNavigationHeight
import zed.rainxch.core.presentation.locals.LocalBottomNavigationLiquid
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.presentation.components.LiquidGlassCategoryChips
import zed.rainxch.home.presentation.locals.LocalHomeTopBarLiquid

@Composable
fun HomeRoot(
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToDetails: (GithubRepoSummary) -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            HomeEvent.OnScrollToListTop -> {
                scope.launch {
                    listState.animateScrollToItem(0)
                }
            }

            is HomeEvent.OnMessage -> {
                scope.launch {
                    snackbarHost.showSnackbar(event.message)
                }
            }
        }
    }

    HomeScreen(
        state = state,
        snackbarHost = snackbarHost,
        onAction = { action ->
            when (action) {
                HomeAction.OnSearchClick -> {
                    onNavigateToSearch()
                }

                HomeAction.OnSettingsClick -> {
                    onNavigateToSettings()
                }

                HomeAction.OnAppsClick -> {
                    onNavigateToApps()
                }

                is HomeAction.OnRepositoryClick -> {
                    onNavigateToDetails(action.repo)
                }

                is HomeAction.OnRepositoryDeveloperClick -> {
                    onNavigateToDeveloperProfile(action.username)
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
        listState = listState,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    snackbarHost: SnackbarHostState,
    onAction: (HomeAction) -> Unit,
    listState: LazyStaggeredGridState,
) {
    val liquidState = LocalBottomNavigationLiquid.current
    val bottomNavHeight = LocalBottomNavigationHeight.current

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()

            totalItems > 0 &&
                lastVisibleItem != null &&
                lastVisibleItem.index >= (totalItems - 5) &&
                !state.isLoadingMore &&
                !state.isLoading &&
                state.hasMorePages
        }
    }

    val currentOnAction by rememberUpdatedState(onAction)

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            currentOnAction(HomeAction.LoadMore)
        }
    }

    val homeTopbarLiquidState = rememberLiquidState()

    CompositionLocalProvider(
        LocalHomeTopBarLiquid provides homeTopbarLiquidState,
    ) {
        Scaffold(
            topBar = {
                TopAppBar()
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHost,
                    modifier = Modifier.padding(bottom = bottomNavHeight + 16.dp),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 8.dp)
                        .liquefiable(liquidState)
                        .liquefiable(homeTopbarLiquidState),
            ) {
                FilterChips(state, onAction)

                Box(Modifier.fillMaxSize()) {
                    LoadingState(state)

                    ErrorState(state, onAction)

                    MainState(
                        state = state,
                        listState = listState,
                        onAction = onAction,
                        bottomNavLiquidState = liquidState,
                        homeTopBarLiquidState = homeTopbarLiquidState,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainState(
    state: HomeState,
    listState: LazyStaggeredGridState,
    onAction: (HomeAction) -> Unit,
    bottomNavLiquidState: LiquidState,
    homeTopBarLiquidState: LiquidState,
) {
    if (state.repos.isNotEmpty()) {
        LazyVerticalStaggeredGrid(
            state = listState,
            columns = StaggeredGridCells.Adaptive(350.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = state.repos,
                key = { it.repository.id },
                contentType = { "repo" },
            ) { discoveryRepository ->
                RepositoryCard(
                    discoveryRepository = discoveryRepository,
                    onClick = {
                        onAction(HomeAction.OnRepositoryClick(discoveryRepository.repository))
                    },
                    onDeveloperClick = { username ->
                        onAction(HomeAction.OnRepositoryDeveloperClick(username))
                    },
                    onShareClick = {
                        onAction(HomeAction.OnShareClick(discoveryRepository.repository))
                    },
                    modifier =
                        Modifier
                            .animateItem()
                            .liquefiable(bottomNavLiquidState)
                            .liquefiable(homeTopBarLiquidState),
                )
            }

            if (state.isLoadingMore) {
                item(key = "loading_indicator") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = stringResource(Res.string.home_loading_more),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (!state.hasMorePages && !state.isLoadingMore) {
                item(key = "end_message") {
                    Text(
                        text = stringResource(Res.string.home_no_more_repositories),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingState(state: HomeState) {
    if (state.isLoading && state.repos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularWavyProgressIndicator()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.home_finding_repositories),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorState(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
) {
    if (state.errorMessage != null && state.repos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                GithubStoreButton(
                    text = stringResource(Res.string.home_retry),
                    onClick = {
                        onAction(HomeAction.Retry)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterChips(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
) {
    LiquidGlassCategoryChips(
        categories = HomeCategory.entries.toList(),
        selectedCategory = state.currentCategory,
        onCategorySelected = { category ->
            onAction(HomeAction.SwitchCategory(category))
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopAppBar() {
    TopAppBar(
        navigationIcon = {
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xff121212))
                        .padding(4.dp),
                contentScale = ContentScale.Crop,
            )
        },
        title = {
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 4.dp),
                maxLines = 2,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier.padding(12.dp),
    )
}

@Preview
@Composable
private fun Preview() {
    GithubStoreTheme {
        val liquidState = rememberLiquidState()

        CompositionLocalProvider(
            value = LocalBottomNavigationLiquid provides liquidState,
        ) {
            HomeScreen(
                state = HomeState(),
                onAction = {},
                snackbarHost = SnackbarHostState(),
                listState = rememberLazyStaggeredGridState(),
            )
        }
    }
}
