package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.tweaks_storage_downloaded_apks_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_storage_downloaded_apks_clear
import zed.rainxch.githubstore.core.presentation.res.tweaks_storage_downloaded_apks_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_storage_empty_size
import zed.rainxch.githubstore.core.presentation.res.tweaks_storage_using_label
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow

@Composable
fun storageSectionContent(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val emptySize = stringResource(Res.string.tweaks_storage_empty_size)
    val sizeDisplay = state.cacheSize.ifBlank { emptySize }
    val isEmpty = sizeDisplay == emptySize

    SettingsGroup(modifier = modifier) {
        SettingsRow(
            title = stringResource(Res.string.tweaks_storage_downloaded_apks_title),
            subtitle = stringResource(Res.string.tweaks_storage_using_label, sizeDisplay),
            last = true,
            trailing = {
                KomiButton(
                    onClick = { onAction(TweaksAction.OnClearCacheClick) },
                    label = stringResource(Res.string.tweaks_storage_downloaded_apks_clear),
                    variant = KomiButtonVariant.Destructive,
                    size = KomiButtonSize.Sm,
                    enabled = !isEmpty,
                    leadingIcon = Icons.Outlined.DeleteOutline,
                )
            },
        )
    }

    SettingsGroup {
        SettingsRow(
            title = "Export settings",
            subtitle = "Save all settings to a backup file",
            trailing = {
                KomiButton(
                    onClick = { onAction(TweaksAction.OnExportSettings) },
                    label = "Export",
                    variant = KomiButtonVariant.Outline,
                    size = KomiButtonSize.Sm,
                    leadingIcon = Icons.Outlined.FileUpload,
                )
            },
        )
        SettingsRow(
            title = "Import settings",
            subtitle = "Restore settings from a backup file",
            last = true,
            trailing = {
                KomiButton(
                    onClick = { onAction(TweaksAction.OnImportSettings) },
                    label = "Import",
                    variant = KomiButtonVariant.Outline,
                    size = KomiButtonSize.Sm,
                    leadingIcon = Icons.Outlined.FileDownload,
                )
            },
        )
    }
}
