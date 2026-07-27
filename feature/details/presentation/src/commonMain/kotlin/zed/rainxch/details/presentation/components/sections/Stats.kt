package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.domain.model.RepoStats
import zed.rainxch.details.presentation.components.StatItem
import zed.rainxch.details.presentation.components.TextStatItem
import zed.rainxch.githubstore.core.presentation.res.*

fun LazyListScope.stats(
    repoStats: RepoStats,
) {
    item {
        val colors = LocalPersonality.current.colors
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.details_stats_section),
                role = KomiTextRole.Title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = colors.onBackground,
                uppercase = false,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatItem(
                label = stringResource(Res.string.forks),
                stat = repoStats.forks,
                modifier = Modifier.weight(1.5f),
            )
            StatItem(
                label = stringResource(Res.string.stars),
                stat = repoStats.stars,
                modifier = Modifier.weight(2f),
            )
            StatItem(
                label = stringResource(Res.string.issues),
                stat = repoStats.openIssues,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatItem(
                label = stringResource(Res.string.downloads),
                stat = repoStats.totalDownloads,
                modifier = Modifier.weight(1f),
            )
            TextStatItem(
                label = stringResource(Res.string.license),
                value = repoStats.license ?: stringResource(Res.string.license_none),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
