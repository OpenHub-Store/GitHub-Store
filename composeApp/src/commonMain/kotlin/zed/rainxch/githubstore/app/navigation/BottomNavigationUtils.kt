package zed.rainxch.githubstore.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import zed.rainxch.core.domain.getPlatform
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.githubstore.core.presentation.res.*

data class BottomNavigationItem(
    val titleRes: StringResource,
    val iconOutlined: ImageVector,
    val iconFilled: ImageVector,
    val screen: GithubStoreGraph,
)

object BottomNavigationUtils {
    fun items(): List<BottomNavigationItem> =
        listOf(
            BottomNavigationItem(
                titleRes = Res.string.bottom_nav_home_title,
                iconOutlined = Icons.Outlined.Home,
                iconFilled = Icons.Filled.Home,
                screen = GithubStoreGraph.HomeScreen,
            ),
            BottomNavigationItem(
                titleRes = Res.string.bottom_nav_search_title,
                iconOutlined = Icons.Outlined.Search,
                iconFilled = Icons.Filled.Search,
                screen = GithubStoreGraph.SearchScreen,
            ),
            BottomNavigationItem(
                titleRes = Res.string.bottom_nav_apps_title,
                iconOutlined = Icons.Outlined.Apps,
                iconFilled = Icons.Filled.Apps,
                screen = GithubStoreGraph.AppsScreen,
            ),
            BottomNavigationItem(
                titleRes = Res.string.bottom_nav_profile_title,
                iconOutlined = Icons.Outlined.Person2,
                iconFilled = Icons.Filled.Person2,
                screen = GithubStoreGraph.ProfileScreen,
            ),
            BottomNavigationItem(
                titleRes = Res.string.bottom_nav_profile_tweaks,
                iconOutlined = Icons.Outlined.Settings,
                iconFilled = Icons.Filled.Settings,
                screen = GithubStoreGraph.TweaksScreen,
            ),
        )

    fun allowedScreens(): List<GithubStoreGraph> =
        items()
            .filterNot {
                getPlatform() != Platform.ANDROID &&
                    it.screen == GithubStoreGraph.AppsScreen
            }.map { it.screen }
}
