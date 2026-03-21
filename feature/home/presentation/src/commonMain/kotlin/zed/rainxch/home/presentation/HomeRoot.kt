package zed.rainxch.home.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.DiscoveryPlatform
import zed.rainxch.core.presentation.components.GithubStoreButton
import zed.rainxch.core.presentation.components.RepositoryCard
import zed.rainxch.core.presentation.locals.LocalBottomNavigationHeight
import zed.rainxch.core.presentation.locals.LocalBottomNavigationLiquid
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.toIcons
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.domain.model.TopicCategory
import zed.rainxch.home.presentation.components.LiquidGlassCategoryChips
import zed.rainxch.home.presentation.locals.LocalHomeTopBarLiquid
import zed.rainxch.home.presentation.utils.displayText
import zed.rainxch.home.presentation.utils.icon

@Composable
fun HomeRoot(
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToDetails: (repoId: Long) -> Unit,
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
                    onNavigateToDetails(action.repo.id)
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

    val isHeaderVisible by listState.isScrollingUp()

    val homeTopbarLiquidState = rememberLiquidState()

    CompositionLocalProvider(
        LocalHomeTopBarLiquid provides homeTopbarLiquidState,
    ) {
        Scaffold(
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
                        .then(
                            if (state.isLiquidGlassEnabled) {
                                Modifier
                                    .liquefiable(liquidState)
                                    .liquefiable(homeTopbarLiquidState)
                            } else {
                                Modifier
                            },
                        ),
            ) {
                AnimatedVisibility(
                    visible = isHeaderVisible,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + fadeIn(tween(200)),
                    exit = slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + fadeOut(tween(150)),
                ) {
                    Column {
                        HomeTopAppBar(
                            currentPlatform = state.currentPlatform,
                            onChangePlatform = {
                                onAction(HomeAction.SwitchFilterPlatform(it))
                            },
                            isPlatformPopupVisible = state.isPlatformPopupVisible,
                            onTogglePlatformPopup = {
                                onAction(HomeAction.OnTogglePlatformPopup)
                            },
                        )

                        FilterChips(state, onAction)

                        TopicChips(
                            selectedTopic = state.selectedTopic,
                            onTopicSelected = { topic ->
                                onAction(HomeAction.SwitchTopic(topic))
                            },
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

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
private fun TopicChips(
    selectedTopic: TopicCategory?,
    onTopicSelected: (TopicCategory) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TopicCategory.entries.forEach { topic ->
            val isSelected = selectedTopic == topic

            val containerColor by animateColorAsState(
                targetValue =
                    if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                animationSpec = tween(250),
                label = "topicChipContainer",
            )

            val labelColor by animateColorAsState(
                targetValue =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                animationSpec = tween(250),
                label = "topicChipLabel",
            )

            FilterChip(
                selected = isSelected,
                onClick = { onTopicSelected(topic) },
                label = {
                    Text(
                        text = topic.displayText(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = topic.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = containerColor,
                    labelColor = labelColor,
                    iconColor = labelColor,
                    selectedContainerColor = containerColor,
                    selectedLabelColor = labelColor,
                    selectedLeadingIconColor = labelColor,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = Color.Transparent,
                    selectedBorderColor = Color.Transparent,
                    enabled = true,
                    selected = isSelected,
                ),
                shape = RoundedCornerShape(12.dp),
            )
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
    val visibleRepos by remember(state.repos, state.isHideSeenEnabled, state.seenRepoIds) {
        derivedStateOf {
            if (state.isHideSeenEnabled && state.seenRepoIds.isNotEmpty()) {
                state.repos.filter { it.repository.id !in state.seenRepoIds }
            } else {
                state.repos
            }
        }
    }

    if (visibleRepos.isNotEmpty()) {
        LazyVerticalStaggeredGrid(
            state = listState,
            columns = StaggeredGridCells.Adaptive(350.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = visibleRepos,
                key = { it.repository.id },
                contentType = { "repo" },
            ) { discoveryRepository ->
                RepositoryCard(
                    discoveryRepositoryUi = discoveryRepository,
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
                            .then(
                                if (state.isLiquidGlassEnabled) {
                                    Modifier
                                        .liquefiable(bottomNavLiquidState)
                                        .liquefiable(homeTopBarLiquidState)
                                } else {
                                    Modifier
                                },
                            ),
                )
            }

            if (state.isLoadingMore || state.isLoadingTopicSupplement) {
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

            if (!state.hasMorePages && !state.isLoadingMore && !state.isLoadingTopicSupplement) {
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
        isLiquidGlassEnabled = state.isLiquidGlassEnabled,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeTopAppBar(
    currentPlatform: DiscoveryPlatform,
    onChangePlatform: (DiscoveryPlatform) -> Unit,
    isPlatformPopupVisible: Boolean,
    onTogglePlatformPopup: () -> Unit,
) {
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
        actions = {
            val icons = currentPlatform.toIcons()

            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onTogglePlatformPopup)
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                icons.forEach { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (isPlatformPopupVisible) {
                Box {
                    PlatformsPopup(
                        onTogglePlatformPopup = onTogglePlatformPopup,
                        onChangePlatform = onChangePlatform,
                        currentPlatform = currentPlatform,
                    )
                }
            }
        },
        modifier = Modifier.padding(12.dp),
    )
}

@Composable
private fun PlatformsPopup(
    onTogglePlatformPopup: () -> Unit,
    onChangePlatform: (DiscoveryPlatform) -> Unit,
    currentPlatform: DiscoveryPlatform,
) {
    Popup(
        onDismissRequest = onTogglePlatformPopup,
    ) {
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(6.dp),
        ) {
            DiscoveryPlatform.entries.forEach { platform ->
                Box(
                    modifier =
                        Modifier
                            .clickable(onClick = {
                                onChangePlatform(platform)
                                onTogglePlatformPopup()
                            })
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                6.dp,
                                Alignment.Start,
                            ),
                    ) {
                        if (currentPlatform == platform) {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Text(
                            text = platform.toLabel(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tracks scroll direction — returns true when the user is scrolling up
 * (or is at the very top of the list), false when scrolling down.
 */
@Composable
private fun LazyStaggeredGridState.isScrollingUp(): State<Boolean> {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }

    return remember(this) {
        derivedStateOf {
            if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) {
                true // at the very top — always show header
            } else if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }
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
