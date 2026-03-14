package zed.rainxch.profile.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fletchmckee.liquid.liquefiable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.locals.LocalBottomNavigationHeight
import zed.rainxch.core.presentation.locals.LocalBottomNavigationLiquid
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.profile.presentation.components.LogoutDialog
import zed.rainxch.profile.presentation.components.sections.about
import zed.rainxch.profile.presentation.components.sections.installationSection
import zed.rainxch.profile.presentation.components.sections.logout
import zed.rainxch.profile.presentation.components.sections.networkSection
import zed.rainxch.profile.presentation.components.sections.othersSection
import zed.rainxch.profile.presentation.components.sections.profile
import zed.rainxch.profile.presentation.components.sections.settings

@Composable
fun ProfileRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDevProfile: (username: String) -> Unit,
    onNavigateToAuthentication: () -> Unit,
    onNavigateToStarredRepos: () -> Unit,
    onNavigateToFavouriteRepos: () -> Unit,
    onNavigateToSponsor: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ProfileEvent.OnLogoutSuccessful -> {
                coroutineScope.launch {
                    snackbarState.showSnackbar(getString(Res.string.logout_success))

                    onNavigateBack()
                }
            }

            is ProfileEvent.OnLogoutError -> {
                coroutineScope.launch {
                    snackbarState.showSnackbar(event.message)
                }
            }

            ProfileEvent.OnProxySaved -> {
                coroutineScope.launch {
                    snackbarState.showSnackbar(getString(Res.string.proxy_saved))
                }
            }

            is ProfileEvent.OnProxySaveError -> {
                coroutineScope.launch {
                    snackbarState.showSnackbar(event.message)
                }
            }

            ProfileEvent.OnCacheCleared -> {
                coroutineScope.launch {
                    snackbarState.showSnackbar(getString(Res.string.cache_cleared))
                }
            }

            is ProfileEvent.OnCacheClearError -> {
                coroutineScope.launch {
                    snackbarState.showSnackbar(event.message)
                }
            }
        }
    }

    ProfileScreen(
        state = state,
        onAction = { action ->
            when (action) {
                ProfileAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                ProfileAction.OnLoginClick -> {
                    onNavigateToAuthentication()
                }

                ProfileAction.OnFavouriteReposClick -> {
                    onNavigateToFavouriteRepos()
                }

                ProfileAction.OnStarredReposClick -> {
                    onNavigateToStarredRepos()
                }

                is ProfileAction.OnRepositoriesClick -> {
                    onNavigateToDevProfile(action.username)
                }

                ProfileAction.OnSponsorClick -> {
                    onNavigateToSponsor()
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
        snackbarState = snackbarState,
    )

    if (state.isLogoutDialogVisible) {
        LogoutDialog(
            onDismissRequest = {
                viewModel.onAction(ProfileAction.OnLogoutDismiss)
            },
            onLogout = {
                viewModel.onAction(ProfileAction.OnLogoutConfirmClick)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
    snackbarState: SnackbarHostState,
) {
    val liquidState = LocalBottomNavigationLiquid.current
    val bottomNavHeight = LocalBottomNavigationHeight.current
    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarState,
                modifier = Modifier.padding(bottom = bottomNavHeight + 16.dp),
            )
        },
        topBar = {
            TopAppBar()
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.liquefiable(liquidState),
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
        ) {
            profile(
                state = state,
                onAction = onAction,
            )

            item {
                Spacer(Modifier.height(32.dp))
            }

            settings(
                state = state,
                onAction = onAction,
            )

            item {
                Spacer(Modifier.height(32.dp))
            }

            networkSection(
                state = state,
                onAction = onAction,
            )

            item {
                Spacer(Modifier.height(12.dp))
            }

            installationSection(
                state = state,
                onAction = onAction
            )

            item {
                Spacer(Modifier.height(16.dp))
            }

            othersSection(
                state = state,
                onAction = onAction,
            )

            item {
                Spacer(Modifier.height(32.dp))
            }

            about(
                versionName = state.versionName,
                onAction = onAction,
            )

            item {
                Spacer(Modifier.height(32.dp))
            }

            if (state.isUserLoggedIn) {
                logout(
                    onAction = onAction,
                )
            }

            item {
                Spacer(Modifier.height(bottomNavHeight + 32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopAppBar() {
    TopAppBar(
        title = {
            Text(
                text = stringResource(Res.string.profile_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

@Preview
@Composable
private fun Preview() {
    GithubStoreTheme {
        ProfileScreen(
            state = ProfileState(),
            onAction = {},
            snackbarState = SnackbarHostState(),
        )
    }
}
