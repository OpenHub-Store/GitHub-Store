package zed.rainxch.core.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GithubStoreButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    style: GithubButtonStyle = GithubButtonStyle.Filled,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = style.colors(),
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        contentPadding =
            if (icon != null) {
                PaddingValues(start = 16.dp, end = 24.dp, top = 10.dp, bottom = 10.dp)
            } else {
                PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

enum class GithubButtonStyle {
    Filled,
    Tonal,
    Outlined,
    Text,
    ;

    @Composable
    fun colors() =
        when (this) {
            Filled -> ButtonDefaults.buttonColors()
            Tonal -> ButtonDefaults.filledTonalButtonColors()
            Outlined -> ButtonDefaults.outlinedButtonColors()
            Text -> ButtonDefaults.textButtonColors()
        }
}
