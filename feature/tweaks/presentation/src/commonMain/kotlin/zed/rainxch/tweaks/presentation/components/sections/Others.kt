package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.ExpressiveCard
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.components.SectionHeader
import zed.rainxch.tweaks.presentation.components.ToggleSettingCard

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.othersSection(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
) {
    item {
        SectionHeader(
            text = stringResource(Res.string.storage).uppercase(),
        )

        Spacer(Modifier.height(8.dp))

        ExpressiveCard {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(8.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = stringResource(Res.string.downloaded_packages),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Text(
                        text = stringResource(Res.string.downloaded_packages_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = "${stringResource(Res.string.current_size)} ${state.cacheSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                FilledTonalButton(
                    onClick = {
                        onAction(TweaksAction.OnClearCacheClick)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        ToggleSettingCard(
            title = stringResource(Res.string.auto_detect_clipboard_links),
            description = stringResource(Res.string.auto_detect_clipboard_description),
            checked = state.autoDetectClipboardLinks,
            onCheckedChange = { enabled ->
                onAction(TweaksAction.OnAutoDetectClipboardToggled(enabled))
            },
        )

        Spacer(Modifier.height(16.dp))

        ToggleSettingCard(
            title = stringResource(Res.string.hide_seen_title),
            description = stringResource(Res.string.hide_seen_description),
            checked = state.isHideSeenEnabled,
            onCheckedChange = { enabled ->
                onAction(TweaksAction.OnHideSeenToggled(enabled))
            },
        )

        Spacer(Modifier.height(8.dp))

        ClearSeenHistoryCard(
            onClick = {
                onAction(TweaksAction.OnClearSeenRepos)
            },
        )
    }
}

@Composable
private fun ClearSeenHistoryCard(onClick: () -> Unit) {
    ExpressiveCard {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(Res.string.clear_seen_history),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(Res.string.clear_seen_history_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
