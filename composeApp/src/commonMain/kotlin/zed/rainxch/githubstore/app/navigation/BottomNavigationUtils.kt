package zed.rainxch.githubstore.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person2
import androidx.compose.material.icons.outlined.Search
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
        )

    fun allowedScreens(): List<GithubStoreGraph> =
        items()
            .filterNot {
                getPlatform() != Platform.ANDROID &&
                    it.screen == GithubStoreGraph.AppsScreen
            }.map { it.screen }
}
