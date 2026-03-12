package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.fletchmckee.liquid.liquefiable
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.DetailsState
import zed.rainxch.details.presentation.components.AppHeader
import zed.rainxch.details.presentation.components.ReleaseAssetsPicker
import zed.rainxch.details.presentation.components.SmartInstallButton
import zed.rainxch.details.presentation.components.VersionPicker
import zed.rainxch.details.presentation.components.VersionTypePicker
import zed.rainxch.details.presentation.utils.LocalTopbarLiquidState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.appmanager_description
import zed.rainxch.githubstore.core.presentation.res.external_installer_description
import zed.rainxch.githubstore.core.presentation.res.inspect_with_appmanager
import zed.rainxch.githubstore.core.presentation.res.obtainium_description
import zed.rainxch.githubstore.core.presentation.res.open_in_obtainium
import zed.rainxch.githubstore.core.presentation.res.open_with_external_installer

fun LazyListScope.header(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    item {
        val liquidState = LocalTopbarLiquidState.current

        if (state.repository != null) {
            AppHeader(
                author = state.userProfile,
                release = state.selectedRelease,
                repository = state.repository,
                installedApp = state.installedApp,
                downloadStage = state.downloadStage,
                downloadProgress = state.downloadProgressPercent,
                modifier = Modifier.liquefiable(liquidState),
            )
        }
    }

    // versions type list
    if (state.allReleases.isNotEmpty()) {
        item {
            VersionTypePicker(
                selectedCategory = state.selectedReleaseCategory,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth().animateItem(),
            )
        }
    }

    // version and installable release
    if (state.allReleases.isNotEmpty() || state.installableAssets.isNotEmpty()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReleaseAssetsPicker(
                    assetsList = state.installableAssets,
                    selectedAsset = state.primaryAsset,
                    isPickerVisible = state.isReleaseSelectorVisible,
                    onAction = onAction,
                    modifier = Modifier.weight(.65f),
                )
                VersionPicker(
                    selectedRelease = state.selectedRelease,
                    filteredReleases = state.filteredReleases,
                    isPickerVisible = state.isVersionPickerVisible,
                    onAction = onAction,
                    modifier = Modifier.weight(.35f),
                )
            }
        }
    }

    item {
        val liquidState = LocalTopbarLiquidState.current

        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            SmartInstallButton(
                isDownloading = state.isDownloading,
                isInstalling = state.isInstalling,
                progress = state.downloadProgressPercent,
                primaryAsset = state.primaryAsset,
                state = state,
                onAction = onAction,
            )

            DropdownMenu(
                expanded = state.isInstallDropdownExpanded,
                onDismissRequest = {
                    onAction(DetailsAction.OnToggleInstallDropdown)
                },
                offset = DpOffset(x = 0.dp, y = 20.dp),
            ) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = stringResource(Res.string.open_in_obtainium),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(Res.string.obtainium_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onAction(DetailsAction.OpenInObtainium)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier = Modifier.liquefiable(liquidState),
                )

                Spacer(Modifier.height(8.dp))

                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = stringResource(Res.string.inspect_with_appmanager),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(Res.string.appmanager_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onAction(DetailsAction.OpenInAppManager)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier = Modifier.liquefiable(liquidState),
                )

                Spacer(Modifier.height(8.dp))

                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = stringResource(Res.string.open_with_external_installer),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(Res.string.external_installer_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onAction(DetailsAction.InstallWithExternalApp)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier = Modifier.liquefiable(liquidState),
                )
            }
        }
    }
}
