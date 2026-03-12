package zed.rainxch.devprofile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.components.GithubStoreButton
import zed.rainxch.devprofile.domain.model.RepoFilterType
import zed.rainxch.devprofile.presentation.components.DeveloperRepoItem
import zed.rainxch.devprofile.presentation.components.FilterSortControls
import zed.rainxch.devprofile.presentation.components.ProfileInfoCard
import zed.rainxch.devprofile.presentation.components.StatsRow
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun DeveloperProfileRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repoId: Long) -> Unit,
    viewModel: DeveloperProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    DeveloperProfileScreen(
        state = state,
        onAction = { action ->
            when (action) {
                DeveloperProfileAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                is DeveloperProfileAction.OnRepositoryClick -> {
                    onNavigateToDetails(action.repoId)
                }

                is DeveloperProfileAction.OnOpenLink -> {
                    val url = action.url.trim()
                    val allowed = url.startsWith("https://") || url.startsWith("http://")
                    if (allowed) uriHandler.openUri(url)
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeveloperProfileScreen(
    state: DeveloperProfileState,
    onAction: (DeveloperProfileAction) -> Unit,
) {
    Scaffold(
        topBar = {
            DevProfileTopbar(
                state = state,
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
                state.isLoading -> {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.errorMessage != null && state.profile == null -> {
                    ErrorContent(
                        message = state.errorMessage,
                        onRetry = { onAction(DeveloperProfileAction.OnRetry) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.profile != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            ProfileInfoCard(
                                profile = state.profile,
                                onAction = onAction,
                            )
                        }

                        item {
                            StatsRow(profile = state.profile)
                        }

                        item {
                            FilterSortControls(
                                currentFilter = state.currentFilter,
                                currentSort = state.currentSort,
                                searchQuery = state.searchQuery,
                                repoCount = state.filteredRepositories.size,
                                totalCount = state.repositories.size,
                                onAction = onAction,
                            )
                        }

                        if (state.isLoadingRepos) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularWavyProgressIndicator()
                                }
                            }
                        } else if (state.filteredRepositories.isEmpty()) {
                            item {
                                EmptyReposContent(
                                    filter = state.currentFilter,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                )
                            }
                        } else {
                            items(
                                items = state.filteredRepositories,
                                key = { it.id },
                            ) { repo ->
                                DeveloperRepoItem(
                                    repository = repo,
                                    onItemClick = {
                                        onAction(DeveloperProfileAction.OnRepositoryClick(repo.id))
                                    },
                                    onToggleFavorite = {
                                        onAction(DeveloperProfileAction.OnToggleFavorite(repo))
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            if (state.errorMessage != null && state.profile != null) {
                Snackbar(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    action = {
                        TextButton(
                            onClick = {
                                onAction(DeveloperProfileAction.OnRetry)
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
                                onAction(DeveloperProfileAction.OnDismissError)
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
                        text = state.errorMessage,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyReposContent(
    filter: RepoFilterType,
    modifier: Modifier = Modifier,
) {
    val message =
        when (filter) {
            RepoFilterType.WITH_RELEASES -> stringResource(Res.string.no_repos_with_releases)
            RepoFilterType.INSTALLED -> stringResource(Res.string.no_installed_repos)
            RepoFilterType.FAVORITES -> stringResource(Res.string.no_favorite_repos)
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DevProfileTopbar(
    state: DeveloperProfileState,
    onAction: (DeveloperProfileAction) -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = { onAction(DeveloperProfileAction.OnNavigateBackClick) },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.navigate_back),
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        title = {
            Text(
                text = state.username,
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        actions = {
            state.profile?.htmlUrl?.let {
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = {
                        onAction(DeveloperProfileAction.OnOpenLink(it))
                    },
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = stringResource(Res.string.open_repository),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.error_generic, message),
            maxLines = 3,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        GithubStoreButton(
            text = stringResource(Res.string.retry),
            onClick = {
                onRetry()
            },
        )
    }
}
