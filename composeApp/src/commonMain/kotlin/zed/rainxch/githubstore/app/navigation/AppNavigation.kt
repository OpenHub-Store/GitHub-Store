package zed.rainxch.githubstore.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import io.github.fletchmckee.liquid.rememberLiquidState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import zed.rainxch.apps.presentation.AppsRoot
import zed.rainxch.apps.presentation.AppsViewModel
import zed.rainxch.auth.presentation.AuthenticationRoot
import zed.rainxch.core.presentation.locals.LocalBottomNavigationHeight
import zed.rainxch.core.presentation.locals.LocalBottomNavigationLiquid
import zed.rainxch.details.presentation.DetailsRoot
import zed.rainxch.devprofile.presentation.DeveloperProfileRoot
import zed.rainxch.favourites.presentation.FavouritesRoot
import zed.rainxch.home.presentation.HomeRoot
import zed.rainxch.profile.presentation.ProfileRoot
import zed.rainxch.search.presentation.SearchRoot
import zed.rainxch.starred.presentation.StarredReposRoot

@Composable
fun AppNavigation(navController: NavHostController) {
    val liquidState = rememberLiquidState()
    var bottomNavigationHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val appsViewModel = koinViewModel<AppsViewModel>()
    val appsState by appsViewModel.state.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalBottomNavigationLiquid provides liquidState,
        LocalBottomNavigationHeight provides bottomNavigationHeight,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            NavHost(
                navController = navController,
                startDestination = GithubStoreGraph.HomeScreen,
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
            ) {
                composable<GithubStoreGraph.HomeScreen> {
                    HomeRoot(
                        onNavigateToSearch = {
                            navController.navigate(GithubStoreGraph.SearchScreen)
                        },
                        onNavigateToSettings = {
                            navController.navigate(GithubStoreGraph.ProfileScreen)
                        },
                        onNavigateToApps = {
                            navController.navigate(GithubStoreGraph.AppsScreen)
                        },
                        onNavigateToDetails = { repo ->
                            navController.navigate(
                                GithubStoreGraph.DetailsScreen(
                                    repositoryId = repo.id,
                                ),
                            )
                        },
                        onNavigateToDeveloperProfile = { username ->
                            navController.navigate(
                                GithubStoreGraph.DeveloperProfileScreen(
                                    username = username,
                                ),
                            )
                        },
                    )
                }

                composable<GithubStoreGraph.SearchScreen> {
                    SearchRoot(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onNavigateToDetails = { repo ->
                            navController.navigate(
                                GithubStoreGraph.DetailsScreen(
                                    repositoryId = repo.id,
                                ),
                            )
                        },
                        onNavigateToDetailsFromLink = { owner, repo ->
                            navController.navigate(
                                GithubStoreGraph.DetailsScreen(
                                    owner = owner,
                                    repo = repo,
                                ),
                            )
                        },
                        onNavigateToDeveloperProfile = { username ->
                            navController.navigate(
                                GithubStoreGraph.DeveloperProfileScreen(
                                    username = username,
                                ),
                            )
                        },
                    )
                }

                composable<GithubStoreGraph.DetailsScreen> { backStackEntry ->
                    val args = backStackEntry.toRoute<GithubStoreGraph.DetailsScreen>()
                    DetailsRoot(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onOpenRepositoryInApp = { repoId ->
                            navController.navigate(
                                GithubStoreGraph.DetailsScreen(
                                    repositoryId = repoId,
                                ),
                            )
                        },
                        onNavigateToDeveloperProfile = { username ->
                            navController.navigate(
                                GithubStoreGraph.DeveloperProfileScreen(
                                    username = username,
                                ),
                            )
                        },
                        viewModel =
                            koinViewModel {
                                parametersOf(args.repositoryId, args.owner, args.repo, args.isComingFromUpdate)
                            },
                    )
                }

                composable<GithubStoreGraph.DeveloperProfileScreen> { backStackEntry ->
                    val args = backStackEntry.toRoute<GithubStoreGraph.DeveloperProfileScreen>()
                    DeveloperProfileRoot(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onNavigateToDetails = { repoId ->
                            navController.navigate(
                                GithubStoreGraph.DetailsScreen(
                                    repositoryId = repoId,
                                ),
                            )
                        },
                        viewModel =
                            koinViewModel {
                                parametersOf(args.username)
                            },
                    )
                }

                composable<GithubStoreGraph.AuthenticationScreen> {
                    AuthenticationRoot(
                        onNavigateToHome = {
                            navController.navigate(GithubStoreGraph.HomeScreen) {
                                popUpTo(0) {
                                    inclusive = true
                                }
                            }
                        },
                    )
                }

                composable<GithubStoreGraph.FavouritesScreen> {
                    FavouritesRoot(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onNavigateToDetails = {
                            navController.navigate(GithubStoreGraph.DetailsScreen(it))
                        },
                        onNavigateToDeveloperProfile = { username ->
                            navController.navigate(
                                GithubStoreGraph.DeveloperProfileScreen(
                                    username = username,
                                ),
                            )
                        },
                    )
                }

                composable<GithubStoreGraph.StarredReposScreen> {
                    StarredReposRoot(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onNavigateToDetails = { repoId ->
                            navController.navigate(
                                GithubStoreGraph.DetailsScreen(
                                    repositoryId = repoId,
                                ),
                            )
                        },
                        onNavigateToAuthentication = {
                            navController.navigate(
                                GithubStoreGraph.AuthenticationScreen,
                            )
                        },
                        onNavigateToDeveloperProfile = { username ->
                            navController.navigate(
                                GithubStoreGraph.DeveloperProfileScreen(
                                    username = username,
                                ),
                            )
                        },
                    )
                }

                composable<GithubStoreGraph.ProfileScreen> {
                    ProfileRoot(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onNavigateToAuthentication = {
                            navController.navigate(GithubStoreGraph.AuthenticationScreen)
                        },
                        onNavigateToStarredRepos = {
                            navController.navigate(GithubStoreGraph.StarredReposScreen)
                        },
                        onNavigateToFavouriteRepos = {
                            navController.navigate(GithubStoreGraph.FavouritesScreen)
                        },
                        onNavigateToDevProfile = { username ->
                            navController.navigate(GithubStoreGraph.DeveloperProfileScreen(username))
                        },
                        onNavigateToSponsor = {
                            navController.navigate(GithubStoreGraph.SponsorScreen)
                        },
                    )
                }

                composable<GithubStoreGraph.SponsorScreen> {
                    zed.rainxch.profile.presentation.SponsorScreen(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                    )
                }

                composable<GithubStoreGraph.AppsScreen> {
                    AppsRoot(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onNavigateToRepo = { repoId ->
                            navController.navigate(
                                GithubStoreGraph.DetailsScreen(
                                    repositoryId = repoId,
                                    isComingFromUpdate = true,
                                ),
                            )
                        },
                        viewModel = appsViewModel,
                        state = appsState,
                    )
                }
            }

            val currentScreen = navController.currentBackStackEntryAsState().value.getCurrentScreen()

            currentScreen?.let {
                BottomNavigation(
                    currentScreen = currentScreen,
                    onNavigate = {
                        navController.navigate(it) {
                            popUpTo(GithubStoreGraph.HomeScreen) {
                                saveState = true
                            }

                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    isUpdateAvailable = appsState.apps.any { it.installedApp.isUpdateAvailable },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                            .onGloballyPositioned { coordinates ->
                                bottomNavigationHeight = with(density) { coordinates.size.height.toDp() }
                            },
                )
            }
        }
    }
}
