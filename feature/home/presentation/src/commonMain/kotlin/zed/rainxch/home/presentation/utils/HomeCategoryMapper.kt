package zed.rainxch.home.presentation.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.domain.model.HomeCategory.*
import zed.rainxch.home.domain.model.TopicCategory

@Composable
fun HomeCategory.displayText(): String =
    when (this) {
        TRENDING -> stringResource(Res.string.home_category_trending)
        HOT_RELEASE -> stringResource(Res.string.home_category_hot_release)
        MOST_POPULAR -> stringResource(Res.string.home_category_most_popular)
    }

@Composable
fun TopicCategory.displayText(): String =
    when (this) {
        TopicCategory.PRIVACY -> stringResource(Res.string.home_topic_privacy)
        TopicCategory.MEDIA -> stringResource(Res.string.home_topic_media)
        TopicCategory.PRODUCTIVITY -> stringResource(Res.string.home_topic_productivity)
        TopicCategory.NETWORKING -> stringResource(Res.string.home_topic_networking)
        TopicCategory.DEV_TOOLS -> stringResource(Res.string.home_topic_dev_tools)
    }

fun TopicCategory.icon(): ImageVector =
    when (this) {
        TopicCategory.PRIVACY -> Icons.Outlined.Lock
        TopicCategory.MEDIA -> Icons.Outlined.MusicNote
        TopicCategory.PRODUCTIVITY -> Icons.Outlined.Speed
        TopicCategory.NETWORKING -> Icons.Outlined.Wifi
        TopicCategory.DEV_TOOLS -> Icons.Outlined.Code
    }
