package zed.rainxch.favourites.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.favourites.presentation.components.FavouriteRepositoryItem
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun FavouritesRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repoId: Long) -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    viewModel: FavouritesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FavouritesScreen(
        state = state,
        onAction = { action ->
            when (action) {
                FavouritesAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                is FavouritesAction.OnRepositoryClick -> {
                    onNavigateToDetails(action.favouriteRepository.repoId)
                }

                is FavouritesAction.OnDeveloperProfileClick -> {
                    onNavigateToDeveloperProfile(action.username)
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
fun FavouritesScreen(
    state: FavouritesState,
    onAction: (FavouritesAction) -> Unit,
) {
    Scaffold(
        topBar = {
            FavouritesTopbar(onAction)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            LazyVerticalStaggeredGrid(
                columns =
                    StaggeredGridCells.Adaptive(
                        350.dp,
                    ),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = state.favouriteRepositories,
                    key = { it.repoId },
                ) { repo ->
                    FavouriteRepositoryItem(
                        favouriteRepository = repo,
                        onToggleFavouriteClick = {
                            onAction(FavouritesAction.OnToggleFavorite(repo))
                        },
                        onItemClick = {
                            onAction(FavouritesAction.OnRepositoryClick(repo))
                        },
                        onDevProfileClick = {
                            onAction(FavouritesAction.OnDeveloperProfileClick(repo.repoOwner))
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (state.isLoading) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavouritesTopbar(onAction: (FavouritesAction) -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(Res.string.favourites),
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = {
                    onAction(FavouritesAction.OnNavigateBackClick)
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.navigate_back),
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    )
}

@Preview
@Composable
private fun Preview() {
    GithubStoreTheme {
        FavouritesScreen(
            state = FavouritesState(),
            onAction = {},
        )
    }
}
