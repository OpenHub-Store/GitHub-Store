package zed.rainxch.starred.presentation.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.*
import kotlin.time.Clock

@Composable
internal fun formatRelativeTime(timestamp: Long): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> stringResource(Res.string.just_now)
        diff < 3600_000 -> stringResource(Res.string.minutes_ago, diff / 60_000)
        diff < 86400_000 -> stringResource(Res.string.hours_ago, diff / 3600_000)
        else -> stringResource(Res.string.days_ago, diff / 86400_000)
    }
}
