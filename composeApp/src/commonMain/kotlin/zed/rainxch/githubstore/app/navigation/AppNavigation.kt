package zed.rainxch.githubstore.app.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import zed.rainxch.apps.presentation.AppsRoot
import zed.rainxch.apps.presentation.AppsViewModel
import zed.rainxch.apps.presentation.import.EXTERNAL_IMPORT_OPEN_LINK_SHEET_KEY
import zed.rainxch.apps.presentation.import.ExternalImportRoot
import zed.rainxch.apps.presentation.starred.StarredPickerRoot
import zed.rainxch.auth.presentation.AuthenticationRoot
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.adaptive.AdaptiveDetailArgs
import zed.rainxch.core.presentation.components.adaptive.AdaptiveListDetailScaffold
import zed.rainxch.core.presentation.components.adaptive.rememberAdaptiveListDetailState
import zed.rainxch.core.presentation.locals.LocalAnimatedVisibilityScope
import zed.rainxch.core.presentation.locals.LocalContentWidth
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.core.presentation.locals.LocalSharedTransitionScope
import zed.rainxch.details.presentation.DetailsRoot
import zed.rainxch.details.presentation.about.AboutRoot
import zed.rainxch.details.presentation.markdownviewer.MarkdownViewerRoot
import zed.rainxch.details.presentation.markdownviewer.MarkdownViewerViewModel
import zed.rainxch.details.presentation.whatsnew.WhatsNewRoot
import zed.rainxch.devprofile.presentation.DeveloperProfileRoot
import zed.rainxch.favourites.presentation.FavouritesRoot
import zed.rainxch.favourites.presentation.import.ImportStarsRoot
import zed.rainxch.feed.presentation.FeedRoot
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.adaptive_pick_repo_subtitle
import zed.rainxch.githubstore.core.presentation.res.adaptive_pick_repo_title
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.presentation.HomeRoot
import zed.rainxch.home.presentation.categorylist.CategoryListRoot
import zed.rainxch.profile.presentation.announcements.AnnouncementsScreen
import zed.rainxch.profile.presentation.announcements.AnnouncementsViewModel
import zed.rainxch.profile.presentation.whatsnew.WhatsNewHistoryScreen
import zed.rainxch.profile.presentation.whatsnew.WhatsNewViewModel
import zed.rainxch.recentlyviewed.presentation.RecentlyViewedRoot
import zed.rainxch.repopages.presentation.issuedetail.IssueDetailRoot
import zed.rainxch.repopages.presentation.issues.IssuesRoot
import zed.rainxch.repopages.presentation.pulls.PullsRoot
import zed.rainxch.repopages.presentation.security.SecurityRoot
import zed.rainxch.search.presentation.SearchRoot
import zed.rainxch.search.presentation.SearchViewModel
import zed.rainxch.starred.presentation.StarredReposRoot
import zed.rainxch.tweaks.presentation.TweaksRoot
import zed.rainxch.tweaks.presentation.appinfo.AppInfoRoot
import zed.rainxch.tweaks.presentation.hidden.HiddenRepositoriesRoot
import zed.rainxch.tweaks.presentation.hosttokens.HostTokensRoot
import zed.rainxch.tweaks.presentation.licenses.LicensesRoot
import zed.rainxch.tweaks.presentation.mirror.MirrorPickerRoot
import zed.rainxch.tweaks.presentation.skipped.SkippedUpdatesRoot

@Composable
fun AppNavigation(
    navController: NavHostController,
    isScrollbarEnabled: Boolean,
    contentWidth: ContentWidth,
) {
    val appsViewModel = koinViewModel<AppsViewModel>()
    val appsState by appsViewModel.state.collectAsStateWithLifecycle()

    val whatsNewViewModel = koinViewModel<WhatsNewViewModel>()
    val announcementsViewModel = koinViewModel<AnnouncementsViewModel>()
    val announcementsUnreadCount by announcementsViewModel.unreadCount.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalScrollbarEnabled provides isScrollbarEnabled,
        LocalContentWidth provides contentWidth,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val rail = maxWidth < 1140.dp
            Row(modifier = Modifier.fillMaxSize()) {
                val desktopDrawerCurrent =
                    navController
                        .currentBackStackEntryAsState()
                        .value
                        .getCurrentScreen()

                if (isDesktop() && desktopDrawerCurrent != null) {
                    DesktopSidebar(
                        currentScreen = desktopDrawerCurrent,
                        onNavigate = { target ->
                            navController.navigate(target) {
                                popUpTo(GithubStoreGraph.ExploreScreen) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        rail = rail,
                        unreadAnnouncementsCount = announcementsUnreadCount,
                    )
                }

                Box(
                    modifier =
                        if (isDesktop()) {
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        } else {
                            Modifier.fillMaxSize()
                        },
                ) {
                    val density = LocalDensity.current
                    var bottomBarHeight by remember { mutableStateOf(0.dp) }
                    val currentScreen =
                        navController.currentBackStackEntryAsState().value.getCurrentScreen()
                    val showBottomBar =
                        currentScreen != null &&
                            !isDesktop() &&
                            BottomNavigationUtils
                                .allowedScreens()
                                .any { it.screen::class == currentScreen::class }

                    SharedTransitionLayout(
                        modifier =
                            Modifier.padding(
                                bottom = if (showBottomBar) bottomBarHeight else 0.dp,
                            ),
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = GithubStoreGraph.ExploreScreen,
                            modifier = Modifier.background(LocalPersonality.current.colors.background),
                            enterTransition = {
                                val from = initialState.bottomNavIndex()
                                val to = targetState.bottomNavIndex()
                                if (from != null && to != null && from != to) {
                                    val sign = if (to > from) 1 else -1
                                    slideInHorizontally(
                                        initialOffsetX = { it * sign },
                                        animationSpec = tween(280),
                                    ) + fadeIn(tween(220))
                                } else {
                                    slideInHorizontally(
                                        initialOffsetX = { it / 6 },
                                        animationSpec = tween(280),
                                    ) + fadeIn(tween(220))
                                }
                            },
                            exitTransition = {
                                val from = initialState.bottomNavIndex()
                                val to = targetState.bottomNavIndex()
                                if (from != null && to != null && from != to) {
                                    val sign = if (to > from) -1 else 1
                                    slideOutHorizontally(
                                        targetOffsetX = { it * sign },
                                        animationSpec = tween(280),
                                    ) + fadeOut(tween(220))
                                } else {
                                    fadeOut(tween(180))
                                }
                            },
                            popEnterTransition = {
                                fadeIn(tween(220))
                            },
                            popExitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { it / 6 },
                                    animationSpec = tween(280),
                                ) + fadeOut(tween(220))
                            },
                        ) {
                            composable<GithubStoreGraph.ExploreScreen> {
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                                    LocalAnimatedVisibilityScope provides this@composable,
                                ) {
                                    val listDetailState = rememberAdaptiveListDetailState()

                                    AdaptiveListDetailScaffold(
                                        state = listDetailState,
                                        emptyPaneTitle = stringResource(Res.string.adaptive_pick_repo_title),
                                        emptyPaneSubtitle = stringResource(Res.string.adaptive_pick_repo_subtitle),
                                        list = { isExpanded ->
                                            FeedRoot(
                                                onNavigateToDetails = { repoId ->
                                                    if (isExpanded) {
                                                        listDetailState.select(
                                                            AdaptiveDetailArgs(repositoryId = repoId),
                                                        )
                                                    } else {
                                                        navController.navigate(
                                                            GithubStoreGraph.DetailsScreen(
                                                                repositoryId = repoId,
                                                            ),
                                                        )
                                                    }
                                                },
                                                onNavigateToSearch = {
                                                    navController.navigate(GithubStoreGraph.SearchScreen())
                                                },
                                                onNavigateToProfile = {
                                                    navController.navigate(GithubStoreGraph.ProfileGraph.ProfileScreen)
                                                },
                                            )
                                        },
                                        detail = { args ->
                                            AdaptiveDetailPaneContent(
                                                args = args,
                                                navController = navController,
                                                onCrossNavToRepo = { newArgs ->
                                                    listDetailState.select(
                                                        newArgs,
                                                    )
                                                },
                                                onClearPane = { listDetailState.clear() },
                                            )
                                        },
                                    )
                                }
                            }

                            composable<GithubStoreGraph.ChartsScreen> {
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                                    LocalAnimatedVisibilityScope provides this@composable,
                                ) {
                                    val listDetailState = rememberAdaptiveListDetailState()

                                    AdaptiveListDetailScaffold(
                                        state = listDetailState,
                                        emptyPaneTitle = stringResource(Res.string.adaptive_pick_repo_title),
                                        emptyPaneSubtitle = stringResource(Res.string.adaptive_pick_repo_subtitle),
                                        list = { isExpanded ->
                                            HomeRoot(
                                                onNavigateToDetails = { repoId ->
                                                    if (isExpanded) {
                                                        listDetailState.select(
                                                            AdaptiveDetailArgs(repositoryId = repoId),
                                                        )
                                                    } else {
                                                        navController.navigate(
                                                            GithubStoreGraph.DetailsScreen(
                                                                repositoryId = repoId,
                                                            ),
                                                        )
                                                    }
                                                },
                                            )
                                        },
                                        detail = { args ->
                                            AdaptiveDetailPaneContent(
                                                args = args,
                                                navController = navController,
                                                onCrossNavToRepo = { newArgs ->
                                                    listDetailState.select(
                                                        newArgs,
                                                    )
                                                },
                                                onClearPane = { listDetailState.clear() },
                                            )
                                        },
                                    )
                                }
                            }

                            composable<GithubStoreGraph.CategoryListScreen> { backStackEntry ->
                                val args =
                                    backStackEntry.toRoute<GithubStoreGraph.CategoryListScreen>()
                                val category =
                                    runCatching {
                                        HomeCategory.valueOf(args.category)
                                    }.getOrDefault(HomeCategory.HOT_RELEASE)

                                CategoryListRoot(
                                    category = category,
                                    onNavigateBack = { navController.navigateUp() },
                                    onNavigateToDetails = { repoId ->
                                        navController.navigate(
                                            GithubStoreGraph.DetailsScreen(repositoryId = repoId),
                                        )
                                    },
                                )
                            }

                            composable<GithubStoreGraph.SearchScreen> { backStackEntry ->
                                val args = backStackEntry.toRoute<GithubStoreGraph.SearchScreen>()
                                val initialPlatform =
                                    args.initialPlatform?.let { name ->
                                        runCatching {
                                            DiscoveryPlatform.valueOf(name)
                                        }.getOrNull()
                                    }
                                val listDetailState = rememberAdaptiveListDetailState()
                                val pickRepoTitle =
                                    stringResource(Res.string.adaptive_pick_repo_title)
                                val pickRepoSubtitle =
                                    stringResource(Res.string.adaptive_pick_repo_subtitle)
                                val searchViewModel: SearchViewModel =
                                    koinViewModel {
                                        parametersOf(initialPlatform)
                                    }
                                AdaptiveListDetailScaffold(
                                    state = listDetailState,
                                    emptyPaneTitle = pickRepoTitle,
                                    emptyPaneSubtitle = pickRepoSubtitle,
                                    list = { isExpanded ->
                                        SearchRoot(
                                            onNavigateBack = {
                                                navController.navigateUp()
                                            },
                                            onNavigateToDetails = { repoId, sourceHost ->
                                                if (isExpanded) {
                                                    listDetailState.select(
                                                        AdaptiveDetailArgs(
                                                            repositoryId = repoId,
                                                            sourceHost = sourceHost,
                                                        ),
                                                    )
                                                } else {
                                                    navController.navigate(
                                                        GithubStoreGraph.DetailsScreen(
                                                            repositoryId = repoId,
                                                            sourceHost = sourceHost,
                                                        ),
                                                    )
                                                }
                                            },
                                            onNavigateToDetailsFromLink = { owner, repo ->
                                                if (isExpanded) {
                                                    listDetailState.select(
                                                        AdaptiveDetailArgs(
                                                            owner = owner,
                                                            repo = repo,
                                                        ),
                                                    )
                                                } else {
                                                    navController.navigate(
                                                        GithubStoreGraph.DetailsScreen(
                                                            owner = owner,
                                                            repo = repo,
                                                        ),
                                                    )
                                                }
                                            },
                                            onNavigateToDeveloperProfile = { username ->
                                                navController.navigate(
                                                    GithubStoreGraph.DeveloperProfileScreen(
                                                        username = username,
                                                    ),
                                                )
                                            },
                                            viewModel = searchViewModel,
                                        )
                                    },
                                    detail = { detailArgs ->
                                        AdaptiveDetailPaneContent(
                                            args = detailArgs,
                                            navController = navController,
                                            onCrossNavToRepo = { newArgs ->
                                                listDetailState.select(
                                                    newArgs,
                                                )
                                            },
                                            onClearPane = { listDetailState.clear() },
                                        )
                                    },
                                )
                            }

                            composable<GithubStoreGraph.DetailsScreen> { backStackEntry ->
                                val animatedScope = this
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                                    LocalAnimatedVisibilityScope provides animatedScope,
                                ) {
                                    val args =
                                        backStackEntry.toRoute<GithubStoreGraph.DetailsScreen>()
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
                                        onNavigateToSearchByPlatform = { platform ->
                                            navController.navigate(
                                                GithubStoreGraph.SearchScreen(
                                                    initialPlatform = platform.name,
                                                ),
                                            )
                                        },
                                        onNavigateToAbout = { repoId, owner, repo, sourceHost, translateTo ->
                                            navController.navigate(
                                                GithubStoreGraph.DetailsAboutScreen(
                                                    repositoryId = repoId,
                                                    owner = owner,
                                                    repo = repo,
                                                    sourceHost = sourceHost,
                                                    translateTo = translateTo,
                                                ),
                                            )
                                        },
                                        onNavigateToWhatsNew = { repoId, owner, repo, sourceHost ->
                                            navController.navigate(
                                                GithubStoreGraph.DetailsWhatsNewScreen(
                                                    repositoryId = repoId,
                                                    owner = owner,
                                                    repo = repo,
                                                    sourceHost = sourceHost,
                                                ),
                                            )
                                        },
                                        onNavigateToIssues = { owner, repo ->
                                            navController.navigate(
                                                GithubStoreGraph.RepoIssuesScreen(
                                                    owner = owner,
                                                    repo = repo,
                                                ),
                                            )
                                        },
                                        onNavigateToSecurity = { owner, repo ->
                                            navController.navigate(
                                                GithubStoreGraph.RepoSecurityScreen(
                                                    owner = owner,
                                                    repo = repo,
                                                ),
                                            )
                                        },
                                        onNavigateToMarkdownViewer = { url ->
                                            navController.navigate(
                                                GithubStoreGraph.MarkdownViewerScreen(
                                                    url,
                                                ),
                                            )
                                        },
                                        viewModel =
                                            koinViewModel {
                                                parametersOf(
                                                    args.repositoryId,
                                                    args.owner,
                                                    args.repo,
                                                    args.isComingFromUpdate,
                                                    args.sourceHost,
                                                )
                                            },
                                    )
                                }
                            }

                            composable<GithubStoreGraph.DetailsAboutScreen>(
                                enterTransition = {
                                    slideInHorizontally(
                                        initialOffsetX = { fullWidth -> fullWidth },
                                        animationSpec = tween(durationMillis = 280),
                                    )
                                },
                                exitTransition = {
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> -fullWidth / 4 },
                                        animationSpec = tween(durationMillis = 280),
                                    )
                                },
                                popEnterTransition = {
                                    slideInHorizontally(
                                        initialOffsetX = { fullWidth -> -fullWidth / 4 },
                                        animationSpec = tween(durationMillis = 280),
                                    )
                                },
                                popExitTransition = {
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> fullWidth },
                                        animationSpec = tween(durationMillis = 280),
                                    )
                                },
                            ) { backStackEntry ->
                                val args =
                                    backStackEntry.toRoute<GithubStoreGraph.DetailsAboutScreen>()
                                AboutRoot(
                                    repositoryId = args.repositoryId,
                                    owner = args.owner,
                                    repo = args.repo,
                                    sourceHost = args.sourceHost,
                                    translateTo = args.translateTo,
                                    onNavigateBack = { navController.navigateUp() },
                                    onNavigateToMarkdownViewer = { url ->
                                        navController.navigate(
                                            GithubStoreGraph.MarkdownViewerScreen(
                                                url,
                                            ),
                                        )
                                    },
                                )
                            }

                            composable<GithubStoreGraph.DetailsWhatsNewScreen>(
                                enterTransition = {
                                    slideInHorizontally(
                                        initialOffsetX = { fullWidth -> fullWidth },
                                        animationSpec = tween(durationMillis = 280),
                                    )
                                },
                                exitTransition = {
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> -fullWidth / 4 },
                                        animationSpec = tween(durationMillis = 280),
                                    )
                                },
                                popEnterTransition = {
                                    slideInHorizontally(
                                        initialOffsetX = { fullWidth -> -fullWidth / 4 },
                                        animationSpec = tween(durationMillis = 280),
                                    )
                                },
                                popExitTransition = {
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> fullWidth },
                                        animationSpec = tween(durationMillis = 280),
                                    )
                                },
                            ) { backStackEntry ->
                                val args =
                                    backStackEntry.toRoute<GithubStoreGraph.DetailsWhatsNewScreen>()
                                WhatsNewRoot(
                                    repositoryId = args.repositoryId,
                                    owner = args.owner,
                                    repo = args.repo,
                                    sourceHost = args.sourceHost,
                                    onNavigateBack = { navController.navigateUp() },
                                )
                            }

                            composable<GithubStoreGraph.RepoIssuesScreen> { backStackEntry ->
                                val args =
                                    backStackEntry.toRoute<GithubStoreGraph.RepoIssuesScreen>()
                                IssuesRoot(
                                    onNavigateBack = { navController.navigateUp() },
                                    onOpenIssue = { issueNumber ->
                                        navController.navigate(
                                            GithubStoreGraph.RepoIssueDetailScreen(
                                                owner = args.owner,
                                                repo = args.repo,
                                                issueNumber = issueNumber,
                                            ),
                                        )
                                    },
                                    viewModel =
                                        koinViewModel {
                                            parametersOf(
                                                args.owner,
                                                args.repo,
                                            )
                                        },
                                )
                            }

                            composable<GithubStoreGraph.RepoIssueDetailScreen> { backStackEntry ->
                                val args =
                                    backStackEntry.toRoute<GithubStoreGraph.RepoIssueDetailScreen>()
                                IssueDetailRoot(
                                    onNavigateBack = { navController.navigateUp() },
                                    viewModel =
                                        koinViewModel {
                                            parametersOf(
                                                args.owner,
                                                args.repo,
                                                args.issueNumber,
                                            )
                                        },
                                )
                            }

                            composable<GithubStoreGraph.RepoSecurityScreen> { backStackEntry ->
                                val args =
                                    backStackEntry.toRoute<GithubStoreGraph.RepoSecurityScreen>()
                                SecurityRoot(
                                    onNavigateBack = { navController.navigateUp() },
                                    viewModel =
                                        koinViewModel {
                                            parametersOf(
                                                args.owner,
                                                args.repo,
                                            )
                                        },
                                )
                            }

                            composable<GithubStoreGraph.RepoPullsScreen> { backStackEntry ->
                                val args =
                                    backStackEntry.toRoute<GithubStoreGraph.RepoPullsScreen>()
                                PullsRoot(
                                    onNavigateBack = { navController.navigateUp() },
                                    onOpenPull = { number ->
                                        navController.navigate(
                                            GithubStoreGraph.RepoIssueDetailScreen(
                                                owner = args.owner,
                                                repo = args.repo,
                                                issueNumber = number,
                                            ),
                                        )
                                    },
                                    viewModel =
                                        koinViewModel {
                                            parametersOf(
                                                args.owner,
                                                args.repo,
                                            )
                                        },
                                )
                            }

                            composable<GithubStoreGraph.MarkdownViewerScreen> { backStackEntry ->
                                val args =
                                    backStackEntry.toRoute<GithubStoreGraph.MarkdownViewerScreen>()

                                MarkdownViewerRoot(
                                    onNavigateBack = { navController.navigateUp() },
                                    onNavigateToMarkdownViewer = { url ->
                                        navController.navigate(
                                            GithubStoreGraph.MarkdownViewerScreen(url),
                                        )
                                    },
                                    viewModel =
                                        koinViewModel<MarkdownViewerViewModel> {
                                            parametersOf(args.url)
                                        },
                                )
                            }

                            composable<GithubStoreGraph.DeveloperProfileScreen> { backStackEntry ->
                                val args =
                                    backStackEntry.toRoute<GithubStoreGraph.DeveloperProfileScreen>()
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
                                    onNavigateToUser = { username ->
                                        navController.navigate(
                                            GithubStoreGraph.DeveloperProfileScreen(username = username),
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
                                        navController.navigate(GithubStoreGraph.ExploreScreen) {
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
                                    onNavigateToImportStars = {
                                        navController.navigate(GithubStoreGraph.ImportStarsScreen)
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

                            composable<GithubStoreGraph.StarredPickerScreen> {
                                StarredPickerRoot(
                                    onNavigateBack = { navController.navigateUp() },
                                    onNavigateToDetails = { repoId, owner, repo ->
                                        navController.navigate(
                                            GithubStoreGraph.DetailsScreen(
                                                repositoryId = repoId,
                                                owner = owner,
                                                repo = repo,
                                            ),
                                        )
                                    },
                                )
                            }

                            composable<GithubStoreGraph.ImportStarsScreen> {
                                ImportStarsRoot(
                                    onNavigateBack = { navController.navigateUp() },
                                    onNavigateToDetails = { repoId, owner, repo ->
                                        navController.navigate(
                                            GithubStoreGraph.DetailsScreen(
                                                repositoryId = repoId,
                                                owner = owner,
                                                repo = repo,
                                            ),
                                        )
                                    },
                                )
                            }

                            profileGraph(
                                navController = navController,
                                unreadAnnouncementsCount = announcementsViewModel.unreadCount,
                            )

                            composable<GithubStoreGraph.RecentlyViewedScreen> {
                                RecentlyViewedRoot(
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
                                    onNavigateToDeveloperProfile = { username ->
                                        navController.navigate(
                                            GithubStoreGraph.DeveloperProfileScreen(
                                                username = username,
                                            ),
                                        )
                                    },
                                )
                            }

                            composable<GithubStoreGraph.MirrorPickerScreen> {
                                MirrorPickerRoot(
                                    onNavigateBack = { navController.popBackStack() },
                                )
                            }

                            composable<GithubStoreGraph.WhatsNewHistoryScreen> {
                                val historyEntries by whatsNewViewModel.historyEntries.collectAsStateWithLifecycle()
                                WhatsNewHistoryScreen(
                                    entries = historyEntries,
                                    onNavigateBack = { navController.navigateUp() },
                                )
                            }

                            composable<GithubStoreGraph.AnnouncementsScreen> {
                                val announcementsState by announcementsViewModel.state.collectAsStateWithLifecycle()
                                AnnouncementsScreen(
                                    state = announcementsState,
                                    onAction = announcementsViewModel::onAction,
                                    onNavigateBack = { navController.navigateUp() },
                                )
                            }

                            composable<GithubStoreGraph.TweaksScreen> {
                                TweaksRoot(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToHostTokens = {
                                        navController.navigate(GithubStoreGraph.HostTokensScreen) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateToMirrorPicker = {
                                        navController.navigate(GithubStoreGraph.MirrorPickerScreen) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateToSkippedUpdates = {
                                        navController.navigate(GithubStoreGraph.SkippedUpdatesScreen) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateToHiddenRepositories = {
                                        navController.navigate(GithubStoreGraph.HiddenRepositoriesScreen) {
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            }

                            composable<GithubStoreGraph.AboutScreen> {
                                AppInfoRoot(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToLicenses = {
                                        navController.navigate(GithubStoreGraph.LicensesScreen) {
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            }

                            composable<GithubStoreGraph.LicensesScreen> {
                                LicensesRoot(
                                    onNavigateBack = { navController.popBackStack() },
                                )
                            }

                            composable<GithubStoreGraph.SkippedUpdatesScreen> {
                                SkippedUpdatesRoot(
                                    onNavigateBack = { navController.popBackStack() },
                                )
                            }

                            composable<GithubStoreGraph.HiddenRepositoriesScreen> {
                                HiddenRepositoriesRoot(
                                    onNavigateBack = { navController.popBackStack() },
                                )
                            }

                            composable<GithubStoreGraph.HostTokensScreen> {
                                HostTokensRoot(
                                    onNavigateBack = { navController.popBackStack() },
                                )
                            }

                            composable<GithubStoreGraph.AppsScreen> { backStackEntry ->
                                LaunchedEffect(backStackEntry) {
                                    val handle = backStackEntry.savedStateHandle
                                    val openLinkSheet =
                                        handle.get<Boolean>(EXTERNAL_IMPORT_OPEN_LINK_SHEET_KEY)
                                    if (openLinkSheet == true) {
                                        handle.remove<Boolean>(EXTERNAL_IMPORT_OPEN_LINK_SHEET_KEY)
                                        appsViewModel.onAction(zed.rainxch.apps.presentation.AppsAction.OnAddByLinkClick)
                                    }
                                }
                                val listDetailState = rememberAdaptiveListDetailState()
                                val pickRepoTitle =
                                    stringResource(Res.string.adaptive_pick_repo_title)
                                val pickRepoSubtitle =
                                    stringResource(Res.string.adaptive_pick_repo_subtitle)
                                AdaptiveListDetailScaffold(
                                    state = listDetailState,
                                    emptyPaneTitle = pickRepoTitle,
                                    emptyPaneSubtitle = pickRepoSubtitle,
                                    list = { isExpanded ->
                                        AppsRoot(
                                            onNavigateBack = {
                                                navController.navigateUp()
                                            },
                                            onNavigateToRepo = { repoId, sourceHost, owner, repo ->
                                                if (isExpanded) {
                                                    listDetailState.select(
                                                        AdaptiveDetailArgs(
                                                            repositoryId = repoId,
                                                            isComingFromUpdate = true,
                                                            sourceHost = sourceHost,
                                                            owner = owner,
                                                            repo = repo,
                                                        ),
                                                    )
                                                } else {
                                                    navController.navigate(
                                                        GithubStoreGraph.DetailsScreen(
                                                            repositoryId = repoId,
                                                            isComingFromUpdate = true,
                                                            sourceHost = sourceHost,
                                                            owner = owner.orEmpty(),
                                                            repo = repo.orEmpty(),
                                                        ),
                                                    )
                                                }
                                            },
                                            onNavigateToExternalImport = {
                                                navController.navigate(GithubStoreGraph.ExternalImportScreen)
                                            },
                                            onNavigateToStarredPicker = {
                                                navController.navigate(GithubStoreGraph.StarredPickerScreen)
                                            },
                                            viewModel = appsViewModel,
                                            state = appsState,
                                        )
                                    },
                                    detail = { detailArgs ->
                                        AdaptiveDetailPaneContent(
                                            args = detailArgs,
                                            navController = navController,
                                            onCrossNavToRepo = { newArgs ->
                                                listDetailState.select(
                                                    newArgs,
                                                )
                                            },
                                            onClearPane = { listDetailState.clear() },
                                        )
                                    },
                                )
                            }

                            composable<GithubStoreGraph.ExternalImportScreen> {
                                ExternalImportRoot(
                                    onNavigateBack = {
                                        navController.navigateUp()
                                    },
                                    onNavigateToDetails = { repoId ->
                                        navController.navigate(
                                            GithubStoreGraph.DetailsScreen(
                                                repositoryId = repoId,
                                                isComingFromUpdate = true,
                                            ),
                                        )
                                    },
                                    onAddManually = {
                                        navController.previousBackStackEntry
                                            ?.savedStateHandle
                                            ?.set(EXTERNAL_IMPORT_OPEN_LINK_SHEET_KEY, true)
                                        navController.navigateUp()
                                    },
                                )
                            }
                        }
                    }

                    if (showBottomBar) {
                        BottomNavigation(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .onGloballyPositioned {
                                        bottomBarHeight = with(density) { it.size.height.toDp() }
                                    },
                            currentScreen = currentScreen,
                            onNavigate = {
                                navController.navigate(it) {
                                    popUpTo(GithubStoreGraph.ExploreScreen) {
                                        saveState = true
                                    }

                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            isUpdateAvailable = appsState.apps.any { it.installedApp.isUpdateAvailable },
                            hasUnreadAnnouncements = announcementsUnreadCount > 0,
                        )
                    }
                }
            }
        }
    }
}
