@file:OptIn(ExperimentalTime::class)

package zed.rainxch.starred.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.starred.presentation.components.StarredRepositoryItem
import zed.rainxch.starred.presentation.utils.formatRelativeTime
import kotlin.time.ExperimentalTime

@Composable
fun StarredReposRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repoId: Long) -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    onNavigateToAuthentication: () -> Unit,
    viewModel: StarredReposViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    StarredScreen(
        state = state,
        onAction = { action ->
            when (action) {
                StarredReposAction.OnNavigateBackClick -> onNavigateBack()
                is StarredReposAction.OnRepositoryClick -> onNavigateToDetails(action.repository.repoId)
                is StarredReposAction.OnDeveloperProfileClick -> onNavigateToDeveloperProfile(action.username)
                StarredReposAction.OnSignInClick -> onNavigateToAuthentication()
                else -> viewModel.onAction(action)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StarredScreen(
    state: StarredReposState,
    onAction: (StarredReposAction) -> Unit,
) {
    val pullRefreshState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            StarredTopBar(
                lastSyncTime = state.lastSyncTime,
                isSyncing = state.isSyncing,
                onAction = onAction,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                !state.isAuthenticated -> {
                    EmptyStateContent(
                        title = stringResource(Res.string.sign_in_required),
                        message = stringResource(Res.string.sign_in_with_github_for_stars),
                        icon = Icons.Default.Star,
                        actionText = stringResource(Res.string.sign_in_with_github),
                        onActionClick = {
                            onAction(StarredReposAction.OnSignInClick)
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.isLoading -> {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.starredRepositories.isEmpty() && !state.isSyncing -> {
                    EmptyStateContent(
                        title = stringResource(Res.string.no_starred_repos),
                        message = stringResource(Res.string.star_repos_hint),
                        icon = Icons.Default.Star,
                        actionText = if (state.errorMessage != null) stringResource(Res.string.retry) else null,
                        onActionClick =
                            if (state.errorMessage != null) {
                                {
                                    onAction(StarredReposAction.OnRetrySync)
                                }
                            } else {
                                null
                            },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    PullToRefreshBox(
                        isRefreshing = state.isSyncing,
                        onRefresh = {
                            onAction(StarredReposAction.OnRefresh)
                        },
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(350.dp),
                            verticalItemSpacing = 12.dp,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = state.starredRepositories,
                                key = { it.repoId },
                            ) { repo ->
                                StarredRepositoryItem(
                                    repository = repo,
                                    onToggleFavoriteClick = {
                                        onAction(StarredReposAction.OnToggleFavorite(repo))
                                    },
                                    onItemClick = {
                                        onAction(StarredReposAction.OnRepositoryClick(repo))
                                    },
                                    onDevProfileClick = {
                                        onAction(StarredReposAction.OnDeveloperProfileClick(repo.repoOwner))
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            state.errorMessage?.let { message ->
                Snackbar(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    action = {
                        TextButton(
                            onClick = {
                                onAction(StarredReposAction.OnRetrySync)
                            },
                        ) {
                            Text(
                                text = stringResource(Res.string.retry),
                            )
                        }
                    },
                    dismissAction = {
                        IconButton(
                            onClick = {
                                onAction(StarredReposAction.OnDismissError)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.dismiss),
                            )
                        }
                    },
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StarredTopBar(
    lastSyncTime: Long?,
    isSyncing: Boolean,
    onAction: (StarredReposAction) -> Unit,
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = stringResource(Res.string.starred_repositories),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (lastSyncTime != null && !isSyncing) {
                        Text(
                            text =
                                "${stringResource(Res.string.last_synced)}:" +
                                    " ${formatRelativeTime(lastSyncTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = { onAction(StarredReposAction.OnNavigateBackClick) },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.navigate_back),
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            actions = {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .padding(end = 12.dp),
                        strokeWidth = 2.dp,
                    )
                }
            },
        )
    }
}

@Composable
private fun EmptyStateContent(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onActionClick) {
                Text(actionText)
            }
        }
    }
}

@Preview
@Composable
private fun PreviewStarred() {
    GithubStoreTheme {
        StarredScreen(
            state =
                StarredReposState(
                    starredRepositories = persistentListOf(),
                    isAuthenticated = true,
                ),
            onAction = {},
        )
    }
}
