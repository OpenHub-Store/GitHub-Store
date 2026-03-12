package zed.rainxch.search.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fletchmckee.liquid.liquefiable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.GithubRepoSummary
import zed.rainxch.core.presentation.components.GithubStoreButton
import zed.rainxch.core.presentation.components.RepositoryCard
import zed.rainxch.core.presentation.locals.LocalBottomNavigationHeight
import zed.rainxch.core.presentation.locals.LocalBottomNavigationLiquid
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.domain.model.ProgrammingLanguage
import zed.rainxch.domain.model.SearchPlatform
import zed.rainxch.domain.model.SortBy
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.clipboard_link_detected
import zed.rainxch.githubstore.core.presentation.res.detected_links
import zed.rainxch.githubstore.core.presentation.res.dismiss
import zed.rainxch.githubstore.core.presentation.res.language_label
import zed.rainxch.githubstore.core.presentation.res.open_github_link
import zed.rainxch.githubstore.core.presentation.res.open_in_app
import zed.rainxch.githubstore.core.presentation.res.results_found
import zed.rainxch.githubstore.core.presentation.res.retry
import zed.rainxch.githubstore.core.presentation.res.search_repositories_hint
import zed.rainxch.githubstore.core.presentation.res.sort_label
import zed.rainxch.search.presentation.components.LanguageFilterBottomSheet
import zed.rainxch.search.presentation.components.SortByBottomSheet
import zed.rainxch.search.presentation.utils.ParsedGithubLink
import zed.rainxch.search.presentation.utils.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (GithubRepoSummary) -> Unit,
    onNavigateToDetailsFromLink: (owner: String, repo: String) -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SearchEvent.OnMessage -> {
                scope.launch {
                    snackbarHost.showSnackbar(event.message)
                }
            }

            is SearchEvent.NavigateToRepo -> {
                onNavigateToDetailsFromLink(event.owner, event.repo)
            }
        }
    }

    SearchScreen(
        state = state,
        snackbarHost = snackbarHost,
        onAction = { action ->
            when (action) {
                is SearchAction.OnRepositoryClick -> {
                    onNavigateToDetails(action.repository)
                }

                SearchAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                is SearchAction.OnRepositoryDeveloperClick -> {
                    onNavigateToDeveloperProfile(action.username)
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
    )

    if (state.isLanguageSheetVisible) {
        LanguageFilterBottomSheet(
            selectedLanguage = state.selectedLanguage,
            onLanguageSelected = { language ->
                viewModel.onAction(SearchAction.OnLanguageSelected(language))
            },
            onDismissRequest = {
                viewModel.onAction(SearchAction.OnToggleLanguageSheetVisibility)
            },
        )
    }

    if (state.isSortByDialogVisible) {
        SortByBottomSheet(
            sortByOptions = SortBy.entries,
            selectedSortBy = state.selectedSortBy,
            selectedSortOrder = state.selectedSortOrder,
            onSortBySelected = { sortBy ->
                viewModel.onAction(SearchAction.OnSortBySelected(sortBy))
            },
            onSortOrderSelected = { sortOrder ->
                viewModel.onAction(SearchAction.OnSortOrderSelected(sortOrder))
            },
            onDismissRequest = {
                viewModel.onAction(SearchAction.OnToggleSortByDialogVisibility)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    state: SearchState,
    snackbarHost: SnackbarHostState,
    onAction: (SearchAction) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyStaggeredGridState()
    val liquidState = LocalBottomNavigationLiquid.current
    val bottomNavHeight = LocalBottomNavigationHeight.current

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo

            if (totalItems == 0 ||
                state.isLoadingMore ||
                state.isLoading ||
                !state.hasMorePages
            ) {
                return@derivedStateOf false
            }

            val lastVisibleItem = visibleItems.lastOrNull() ?: return@derivedStateOf false
            val viewportEndOffset = layoutInfo.viewportEndOffset

            val hasEmptySpaceAtBottom =
                lastVisibleItem.index == totalItems - 1 &&
                    lastVisibleItem.offset.y + lastVisibleItem.size.height < viewportEndOffset

            val threshold = (totalItems * 0.8f).toInt()
            val isNearEnd = lastVisibleItem.index >= threshold

            isNearEnd || hasEmptySpaceAtBottom
        }
    }

    val currentOnAction by rememberUpdatedState(onAction)

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            currentOnAction(SearchAction.LoadMore)
        }
    }

    LaunchedEffect(listState.layoutInfo.totalItemsCount, listState.layoutInfo.viewportEndOffset) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val lastVisible = visibleItems.lastOrNull()

        if (lastVisible != null &&
            layoutInfo.totalItemsCount > 0 &&
            !state.isLoadingMore &&
            !state.isLoading &&
            state.hasMorePages
        ) {
            val hasEmptySpace =
                lastVisible.index == layoutInfo.totalItemsCount - 1 &&
                    lastVisible.offset.y + lastVisible.size.height < layoutInfo.viewportEndOffset

            if (hasEmptySpace) {
                delay(100)
                currentOnAction(SearchAction.LoadMore)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            SearchTopbar(
                onAction = onAction,
                state = state,
                focusRequester = focusRequester,
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.padding(bottom = bottomNavHeight + 16.dp),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    onAction(SearchAction.OnFabClick)
                },
                modifier = Modifier.padding(bottom = bottomNavHeight + 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )

                    Text(
                        text = stringResource(Res.string.open_github_link),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.liquefiable(liquidState),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
        ) {
            // Clipboard banner
            AnimatedVisibility(
                visible = state.isClipboardBannerVisible && state.clipboardLinks.isNotEmpty(),
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                ClipboardBanner(
                    links = state.clipboardLinks,
                    onOpenLink = { link ->
                        onAction(SearchAction.OpenGithubLink(link.owner, link.repo))
                    },
                    onDismiss = {
                        onAction(SearchAction.DismissClipboardBanner)
                    },
                )
            }

            // Detected links from search query
            AnimatedVisibility(
                visible = state.detectedLinks.isNotEmpty(),
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                DetectedLinksSection(
                    links = state.detectedLinks,
                    onOpenLink = { link ->
                        onAction(SearchAction.OpenGithubLink(link.owner, link.repo))
                    },
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(SearchPlatform.entries) { sortBy ->
                    FilterChip(
                        selected = state.selectedSearchPlatform == sortBy,
                        label = {
                            Text(
                                text = sortBy.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        },
                        onClick = {
                            onAction(SearchAction.OnPlatformTypeSelected(sortBy))
                        },
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.language_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )

                    FilterChip(
                        selected = state.selectedLanguage != ProgrammingLanguage.All,
                        onClick = {
                            onAction(SearchAction.OnToggleLanguageSheetVisibility)
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(state.selectedLanguage.label()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )

                    if (state.selectedLanguage != ProgrammingLanguage.All) {
                        IconButton(
                            onClick = {
                                onAction(SearchAction.OnLanguageSelected(ProgrammingLanguage.All))
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.sort_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )

                    FilterChip(
                        selected = state.selectedSortBy != SortBy.BestMatch,
                        onClick = {
                            onAction(SearchAction.OnToggleSortByDialogVisibility)
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(state.selectedSortBy.label()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (state.totalCount != null) {
                Text(
                    text =
                        stringResource(
                            Res.string.results_found,
                            state.totalCount,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                )
            }

            Box(Modifier.fillMaxSize()) {
                if (state.isLoading && state.repositories.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().imePadding(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator()
                    }
                }

                if (state.errorMessage != null && state.repositories.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.errorMessage,
                            )

                            Spacer(Modifier.height(8.dp))

                            GithubStoreButton(
                                text = stringResource(Res.string.retry),
                                onClick = {
                                    onAction(SearchAction.Retry)
                                },
                            )
                        }
                    }
                }

                if (state.repositories.isNotEmpty()) {
                    LazyVerticalStaggeredGrid(
                        state = listState,
                        columns = StaggeredGridCells.Adaptive(350.dp),
                        verticalItemSpacing = 12.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .liquefiable(liquidState),
                    ) {
                        items(
                            items = state.repositories,
                            key = { it.repository.id },
                        ) { discoveryRepository ->
                            RepositoryCard(
                                discoveryRepository = discoveryRepository,
                                onClick = {
                                    onAction(SearchAction.OnRepositoryClick(discoveryRepository.repository))
                                },
                                onDeveloperClick = { username ->
                                    onAction(SearchAction.OnRepositoryDeveloperClick(username))
                                },
                                onShareClick = {
                                    onAction(SearchAction.OnShareClick(discoveryRepository.repository))
                                },
                                modifier =
                                    Modifier
                                        .animateItem()
                                        .liquefiable(liquidState),
                            )
                        }

                        item {
                            if (state.isLoadingMore) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardBanner(
    links: List<ParsedGithubLink>,
    onOpenLink: (ParsedGithubLink) -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.clipboard_link_detected),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.dismiss),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            links.forEach { link ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenLink(link) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "${link.owner}/${link.repo}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(Res.string.open_in_app),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectedLinksSection(
    links: List<ParsedGithubLink>,
    onOpenLink: (ParsedGithubLink) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
    ) {
        Text(
            text = stringResource(Res.string.detected_links),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        links.forEach { link ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                onClick = { onOpenLink(link) },
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "${link.owner}/${link.repo}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(Res.string.open_in_app),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTopbar(
    onAction: (SearchAction) -> Unit,
    state: SearchState,
    focusRequester: FocusRequester,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = state.query,
            onValueChange = { value ->
                onAction(SearchAction.OnSearchChange(value))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        onAction(SearchAction.OnClearClick)
                    },
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                    )
                }
            },
            placeholder = {
                Text(
                    text = stringResource(Res.string.search_repositories_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
            keyboardActions =
                KeyboardActions(
                    onSearch = { onAction(SearchAction.OnSearchImeClick) },
                ),
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            shape = CircleShape,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
        )
    }
}

@Preview
@Composable
private fun Preview() {
    GithubStoreTheme {
        SearchScreen(
            state = SearchState(),
            snackbarHost = SnackbarHostState(),
            onAction = {},
        )
    }
}
