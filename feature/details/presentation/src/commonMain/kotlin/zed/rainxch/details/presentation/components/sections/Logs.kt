package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fletchmckee.liquid.liquefiable
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.details.presentation.DetailsState
import zed.rainxch.details.presentation.model.LogResult
import zed.rainxch.details.presentation.utils.LocalTopbarLiquidState
import zed.rainxch.details.presentation.utils.asText
import zed.rainxch.githubstore.core.presentation.res.*

fun LazyListScope.logs(state: DetailsState) {
    item {
        val liquidState = LocalTopbarLiquidState.current

        HorizontalDivider()

        Text(
            text = stringResource(Res.string.install_logs),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier =
                Modifier
                    .padding(vertical = 8.dp)
                    .liquefiable(liquidState),
            fontWeight = FontWeight.Bold,
        )
    }

    items(state.installLogs) { log ->
        val liquidState = LocalTopbarLiquidState.current

        Text(
            text = "> ${log.result.asText()}: ${log.assetName}",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontStyle = FontStyle.Italic,
                ),
            color =
                if (log.result is LogResult.Error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                },
            modifier = Modifier.liquefiable(liquidState),
        )
    }
}
