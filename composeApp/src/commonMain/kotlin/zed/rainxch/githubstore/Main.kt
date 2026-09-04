package zed.rainxch.githubstore

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.toMangaAccent
import zed.rainxch.core.presentation.personality.toMangaPaper
import zed.rainxch.core.presentation.personality.utils.PersonalityTheme
import zed.rainxch.githubstore.app.components.RateLimitDialog
import zed.rainxch.githubstore.app.components.SessionExpiredDialog
import zed.rainxch.githubstore.app.navigation.AppNavigation
import zed.rainxch.githubstore.app.navigation.GithubStoreGraph
import zed.rainxch.githubstore.app.navigation.getCurrentScreen
import zed.rainxch.githubstore.app.whatsnew.WhatsNewSheet
import zed.rainxch.githubstore.utils.HandleDesktopToolbarDeeplinks
import zed.rainxch.githubstore.utils.HandleKeyboardEvents
import zed.rainxch.profile.presentation.whatsnew.WhatsNewViewModel

@Composable
fun App(
    deepLinkUri: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    onResolvedDarkTheme: (Boolean) -> Unit = {},
) {
    val mainViewModel: MainViewModel = koinViewModel()
    val whatsNewViewModel: WhatsNewViewModel = koinViewModel()

    val mainState by mainViewModel.state.collectAsStateWithLifecycle()

    val navController = rememberNavController()

    setSingletonImageLoaderFactory { context ->
        ImageLoader
            .Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    // Nothing below may run before persisted appearance preferences load: the
    // deep-link effect navigates a NavHost that is only composed after this
    // gate, and the theme would be built from untrusted defaults. Android
    // covers the wait with the splash; desktop shows a plain window frame.
    if (!mainState.isAppearanceLoaded) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val currentScreen = navController.currentBackStackEntryAsState().value.getCurrentScreen()

    HandleKeyboardEvents(navController)

    HandleDesktopToolbarDeeplinks(
        deepLinkUri = deepLinkUri,
        onDeepLinkConsumed = onDeepLinkConsumed,
        navController = navController,
    )

    val onAuthScreen = currentScreen is GithubStoreGraph.AuthenticationScreen
    LaunchedEffect(onAuthScreen, mainState.showRateLimitDialog) {
        if (onAuthScreen && mainState.showRateLimitDialog) {
            mainViewModel.onAction(MainAction.DismissRateLimitDialog)
        }
    }

    val resolvedDarkTheme = mainState.isDarkTheme ?: isSystemInDarkTheme()
    LaunchedEffect(resolvedDarkTheme) { onResolvedDarkTheme(resolvedDarkTheme) }

    val personality =
        when (mainState.personality) {
            AppPersonality.MANGA -> {
                mangaPersonality(
                    paper = mainState.mangaPaper.toMangaPaper(),
                    accent = mainState.accent.toMangaAccent(),
                )
            }

            AppPersonality.CLASSIC -> {
                classicPersonality(
                    dark = resolvedDarkTheme,
                    amoled = mainState.isAmoledTheme,
                    accent = mainState.accent,
                )
            }
        }

    PersonalityTheme(personality, languageTag = mainState.appLanguageTag) {
        AppNavigation(
            navController = navController,
            isScrollbarEnabled = mainState.isScrollbarEnabled,
            contentWidth = mainState.contentWidth,
        )

        if (mainState.showRateLimitDialog && mainState.rateLimitInfo != null && !onAuthScreen) {
            RateLimitDialog(
                rateLimitInfo = mainState.rateLimitInfo!!,
                isAuthenticated = mainState.isLoggedIn,
                onDismiss = {
                    mainViewModel.onAction(MainAction.DismissRateLimitDialog)
                },
                onSignIn = {
                    mainViewModel.onAction(MainAction.DismissRateLimitDialog)

                    navController.navigate(GithubStoreGraph.AuthenticationScreen)
                },
            )
        }

        if (mainState.showSessionExpiredDialog) {
            SessionExpiredDialog(
                onDismiss = {
                    mainViewModel.onAction(MainAction.DismissSessionExpiredDialog)
                },
                onSignIn = {
                    mainViewModel.onAction(MainAction.DismissSessionExpiredDialog)
                    navController.navigate(GithubStoreGraph.AuthenticationScreen)
                },
            )
        }

        val pendingEntry by whatsNewViewModel.pendingEntry.collectAsStateWithLifecycle()

        pendingEntry?.let { entryToShow ->
            val onHomeScreen = currentScreen is GithubStoreGraph.ExploreScreen

            if (onHomeScreen && !mainState.showRateLimitDialog) {
                val hasHistory by whatsNewViewModel.hasHistory.collectAsStateWithLifecycle()

                WhatsNewSheet(
                    entry = entryToShow,
                    showHistoryAction = hasHistory,
                    onDismiss = { whatsNewViewModel.markSeen() },
                    onViewHistory = {
                        whatsNewViewModel.markSeen()
                        navController.navigate(GithubStoreGraph.WhatsNewHistoryScreen)
                    },
                )
            }
        }
    }
}
