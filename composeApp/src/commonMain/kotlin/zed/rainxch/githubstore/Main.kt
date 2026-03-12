package zed.rainxch.githubstore

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.core.presentation.utils.ApplyAndroidSystemBars
import zed.rainxch.githubstore.app.components.RateLimitDialog
import zed.rainxch.githubstore.app.components.SessionExpiredDialog
import zed.rainxch.githubstore.app.deeplink.DeepLinkDestination
import zed.rainxch.githubstore.app.deeplink.DeepLinkParser
import zed.rainxch.githubstore.app.navigation.AppNavigation
import zed.rainxch.githubstore.app.navigation.GithubStoreGraph

@Composable
fun App(deepLinkUri: String? = null) {
    val viewModel: MainViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val navBackStack = rememberNavController()

    LaunchedEffect(deepLinkUri) {
        deepLinkUri?.let { uri ->
            when (val destination = DeepLinkParser.parse(uri)) {
                is DeepLinkDestination.Repository -> {
                    navBackStack.navigate(
                        GithubStoreGraph.DetailsScreen(
                            owner = destination.owner,
                            repo = destination.repo,
                        ),
                    )
                }

                DeepLinkDestination.None -> {
                    // ignore unrecognized deep links
                }
            }
        }
    }

    GithubStoreTheme(
        fontTheme = state.currentFontTheme,
        appTheme = state.currentColorTheme,
        isAmoledTheme = state.isAmoledTheme,
        isDarkTheme = state.isDarkTheme ?: isSystemInDarkTheme(),
    ) {
        ApplyAndroidSystemBars(state.isDarkTheme)

        if (state.showRateLimitDialog) {
            state.rateLimitInfo?.let {
                RateLimitDialog(
                    rateLimitInfo = it,
                    isAuthenticated = state.isLoggedIn,
                    onDismiss = {
                        viewModel.onAction(MainAction.DismissRateLimitDialog)
                    },
                    onSignIn = {
                        viewModel.onAction(MainAction.DismissRateLimitDialog)

                        navBackStack.navigate(GithubStoreGraph.AuthenticationScreen)
                    },
                )
            }
        }

        if (state.showSessionExpiredDialog) {
            SessionExpiredDialog(
                onDismiss = {
                    viewModel.onAction(MainAction.DismissSessionExpiredDialog)
                },
                onSignIn = {
                    viewModel.onAction(MainAction.DismissSessionExpiredDialog)
                    navBackStack.navigate(GithubStoreGraph.AuthenticationScreen)
                },
            )
        }

        AppNavigation(
            navController = navBackStack,
        )
    }
}
