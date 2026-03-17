package zed.rainxch.apps.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsState
import zed.rainxch.apps.presentation.LinkStep
import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.apps.presentation.model.GithubAssetUi
import zed.rainxch.githubstore.core.presentation.res.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkAppBottomSheet(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onAction(AppsAction.OnDismissLinkSheet) },
        sheetState = sheetState,
    ) {
        AnimatedContent(
            targetState = state.linkStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "link_step",
        ) { step ->
            when (step) {
                LinkStep.PickApp -> PickAppStep(
                    deviceApps = state.filteredDeviceApps,
                    searchQuery = state.deviceAppSearchQuery,
                    onSearchChange = { onAction(AppsAction.OnDeviceAppSearchChange(it)) },
                    onAppSelected = { onAction(AppsAction.OnDeviceAppSelected(it)) },
                )

                LinkStep.EnterUrl -> EnterUrlStep(
                    selectedApp = state.selectedDeviceApp,
                    repoUrl = state.repoUrl,
                    isValidating = state.isValidatingRepo,
                    validationError = state.repoValidationError,
                    validationStatus = state.linkValidationStatus,
                    onUrlChanged = { onAction(AppsAction.OnRepoUrlChanged(it)) },
                    onConfirm = { onAction(AppsAction.OnValidateAndLinkRepo) },
                    onBack = { onAction(AppsAction.OnBackToAppPicker) },
                )

                LinkStep.PickAsset -> PickAssetStep(
                    assets = state.linkInstallableAssets,
                    selectedAsset = state.linkSelectedAsset,
                    downloadProgress = state.linkDownloadProgress,
                    validationStatus = state.linkValidationStatus,
                    validationError = state.repoValidationError,
                    onAssetSelected = { onAction(AppsAction.OnLinkAssetSelected(it)) },
                    onBack = { onAction(AppsAction.OnBackToEnterUrl) },
                )
            }
        }
    }
}

@Composable
private fun PickAppStep(
    deviceApps: List<DeviceAppUi>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAppSelected: (DeviceAppUi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(Res.string.link_app_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(Res.string.pick_installed_app),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        TextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            placeholder = {
                Text(stringResource(Res.string.search_apps_hint))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                )
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
        ) {
            items(
                items = deviceApps,
                key = { it.packageName },
            ) { app ->
                DeviceAppItem(
                    app = app,
                    onClick = { onAppSelected(app) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }

            if (deviceApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.no_apps_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DeviceAppItem(
    app: DeviceAppUi,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        app.versionName?.let { version ->
            Text(
                text = version,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun EnterUrlStep(
    selectedApp: DeviceAppUi?,
    repoUrl: String,
    isValidating: Boolean,
    validationError: String?,
    validationStatus: String?,
    onUrlChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }

            Text(
                text = stringResource(Res.string.link_app_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Selected app info
        if (selectedApp != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedApp.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = selectedApp.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                selectedApp.versionName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = repoUrl,
            onValueChange = onUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.enter_repo_url)) },
            placeholder = { Text(stringResource(Res.string.repo_url_hint)) },
            singleLine = true,
            isError = validationError != null,
            supportingText = validationError?.let {
                { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(20.dp))

        FilledTonalButton(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = repoUrl.isNotBlank() && !isValidating,
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.validating_repo))
            } else {
                Text(
                    text = stringResource(Res.string.link_and_track),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (isValidating && validationStatus != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = validationStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PickAssetStep(
    assets: List<GithubAssetUi>,
    selectedAsset: GithubAssetUi?,
    downloadProgress: Int?,
    validationStatus: String?,
    validationError: String?,
    onAssetSelected: (GithubAssetUi) -> Unit,
    onBack: () -> Unit,
) {
    val isProcessing = selectedAsset != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = !isProcessing) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }

            Text(
                text = stringResource(Res.string.select_asset_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(Res.string.select_asset_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) {
            items(
                items = assets,
                key = { it.id },
            ) { asset ->
                val isSelected = selectedAsset?.id == asset.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable(enabled = !isProcessing) { onAssetSelected(asset) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = asset.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatFileSize(asset.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (isSelected && downloadProgress != null) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$downloadProgress%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }
        }

        if (validationStatus != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = validationStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (validationError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = validationError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String =
    when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
