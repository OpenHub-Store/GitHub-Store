package zed.rainxch.core.presentation.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun formatCount(count: Int): String =
    when {
        count >= 1_000_000 -> stringResource(Res.string.count_millions, count / 1_000_000)
        count >= 1000 -> stringResource(Res.string.count_thousands, count / 1000)
        else -> count.toString()
    }
