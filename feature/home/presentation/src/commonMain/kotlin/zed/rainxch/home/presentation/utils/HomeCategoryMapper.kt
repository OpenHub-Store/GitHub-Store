package zed.rainxch.home.presentation.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.domain.model.HomeCategory.*

@Composable
fun HomeCategory.displayText(): String =
    when (this) {
        TRENDING -> stringResource(Res.string.home_category_trending)
        HOT_RELEASE -> stringResource(Res.string.home_category_hot_release)
        MOST_POPULAR -> stringResource(Res.string.home_category_most_popular)
    }
