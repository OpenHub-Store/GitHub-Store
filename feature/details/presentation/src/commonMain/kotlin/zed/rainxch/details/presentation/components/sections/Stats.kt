package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fletchmckee.liquid.liquefiable
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.details.domain.model.RepoStats
import zed.rainxch.details.presentation.components.StatItem
import zed.rainxch.details.presentation.utils.LocalTopbarLiquidState
import zed.rainxch.githubstore.core.presentation.res.*

fun LazyListScope.stats(repoStats: RepoStats) {
    item {
        val liquidState = LocalTopbarLiquidState.current

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatItem(
                label = stringResource(Res.string.forks),
                stat = repoStats.forks,
                modifier =
                    Modifier
                        .weight(1.5f)
                        .liquefiable(liquidState),
            )

            StatItem(
                label = stringResource(Res.string.stars),
                stat = repoStats.stars,
                modifier =
                    Modifier
                        .weight(2f)
                        .liquefiable(liquidState),
            )

            StatItem(
                label = stringResource(Res.string.issues),
                stat = repoStats.openIssues,
                modifier =
                    Modifier
                        .weight(1f)
                        .liquefiable(liquidState),
            )
        }
    }
}
