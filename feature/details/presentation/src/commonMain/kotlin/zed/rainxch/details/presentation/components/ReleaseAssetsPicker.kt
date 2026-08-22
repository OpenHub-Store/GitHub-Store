package zed.rainxch.details.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import zed.rainxch.core.presentation.utils.formatFileSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubUser
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.githubstore.core.presentation.res.Res

@Composable
fun ReleaseAssetsPicker(
    onAction: (DetailsAction) -> Unit,
    assetsList: ImmutableList<GithubAsset>,
    modifier: Modifier = Modifier,
    selectedAsset: GithubAsset? = null,
    isPickerVisible: Boolean = false,
    pinnedVariant: String? = null,
    showAllPlatforms: Boolean = false,
    crossPlatformAssets: ImmutableList<GithubAsset> = persistentListOf(),
) {
    val colors = LocalPersonality.current.colors

    val isPickerEnabled = assetsList.isNotEmpty() || crossPlatformAssets.isNotEmpty()

    ReleaseAssetsItemsPicker(
        showPicker = isPickerVisible,
        assetsList = assetsList,
        crossPlatformAssets = crossPlatformAssets,
        showAllPlatforms = showAllPlatforms,
        selectedAsset = selectedAsset,
        pinnedVariant = pinnedVariant,
        onDismiss = { onAction(DetailsAction.ToggleReleaseAssetsPicker) },
        onSelect = { onAction(DetailsAction.SelectDownloadAsset(it)) },
        onUnpin = { onAction(DetailsAction.UnpinPreferredVariant) },
        onToggleShowAllPlatforms = { enabled ->
            onAction(DetailsAction.OnToggleShowAllPlatforms(enabled))
        },
        onDownloadForTransfer = { asset ->
            onAction(DetailsAction.OnDownloadForTransfer(asset.downloadUrl))
        },
    )

    val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    Column(
        modifier = modifier.wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.assets_title),
            role = KomiTextRole.Label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = colors.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .border(
                    width = 1.dp,
                    color = colors.outline,
                    shape = rowShape,
                )
                .background(colors.surface)
                .clickable(enabled = isPickerEnabled) {
                    onAction(DetailsAction.ToggleReleaseAssetsPicker)
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .heightIn(min = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiText(
                text = selectedAsset?.name ?: stringResource(Res.string.no_assets_selected),
                role = KomiTextRole.Stamp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f),
                uppercase = false,
            )
            KomiIcon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = stringResource(Res.string.select_version),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ReleaseAssetsItemsPicker(
    assetsList: ImmutableList<GithubAsset>,
    crossPlatformAssets: ImmutableList<GithubAsset>,
    showAllPlatforms: Boolean,
    selectedAsset: GithubAsset?,
    pinnedVariant: String?,
    showPicker: Boolean,
    onDismiss: () -> Unit,
    onSelect: (GithubAsset) -> Unit,
    onUnpin: () -> Unit,
    onToggleShowAllPlatforms: (Boolean) -> Unit,
    onDownloadForTransfer: (GithubAsset) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!showPicker) return

    val colors = LocalPersonality.current.colors
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }

    ReleaseAssetsAboutDialog(
        showDialog = showInfoDialog,
        onDismiss = { showInfoDialog = false },
    )

    val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    KomiSheet(onDismiss = onDismiss, placement = KomiSheetPlacement.Bottom, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                    KomiText(
                        text = stringResource(Res.string.assets_title),
                        role = KomiTextRole.Title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        color = colors.onSurface,
                        uppercase = false,
                    )
                }
                KomiIconButton(
                    icon = Icons.Outlined.Info,
                    contentDescription = stringResource(Res.string.icon_content_description_info),
                    onClick = { showInfoDialog = true },
                    variant = KomiButtonVariant.Text,
                )
            }

            if (!pinnedVariant.isNullOrBlank()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KomiText(
                        text = stringResource(Res.string.variant_picker_pinned, pinnedVariant),
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    KomiButton(
                        onClick = onUnpin,
                        label = stringResource(Res.string.variant_picker_unpin),
                        variant = KomiButtonVariant.Text,
                        size = KomiButtonSize.Sm,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(rowShape)
                    .border(
                        width = 1.dp,
                        color = colors.outline,
                        shape = rowShape,
                    )
                    .background(colors.surface)
                    .clickable(onClick = { onToggleShowAllPlatforms(!showAllPlatforms) })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KomiIcon(
                    imageVector = Icons.Outlined.Devices,
                    contentDescription = null,
                    tint = colors.onSurface,
                )
                Spacer(Modifier.size(12.dp))
                KomiText(
                    text = stringResource(Res.string.show_all_platforms_label),
                    role = KomiTextRole.Body,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                KomiSwitch(
                    checked = showAllPlatforms,
                    onCheckedChange = onToggleShowAllPlatforms,
                )
            }

            val groups = remember(crossPlatformAssets) {
                crossPlatformAssets
                    .groupBy {
                        zed.rainxch.core.domain.utils.assetPlatformOf(it.name)
                    }
                    .filterKeys { it != null }
                    .mapKeys { it.key!! }
            }
            val installableIds = remember(assetsList) {
                assetsList.map { it.id }.toSet()
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .animateContentSize(animationSpec = tween(durationMillis = 250))
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                if (showAllPlatforms && groups.isNotEmpty()) {

                    val sectionOrder =
                        listOf(
                            DiscoveryPlatform.Android to Res.string.platform_section_android,
                            DiscoveryPlatform.Windows to Res.string.platform_section_windows,
                            DiscoveryPlatform.Macos to Res.string.platform_section_macos,
                            DiscoveryPlatform.Linux to Res.string.platform_section_linux,
                        ).sortedByDescending { (platform, _) ->
                            groups[platform]?.any { it.id in installableIds } == true
                        }
                    sectionOrder.forEach { (platform, labelRes) ->
                        val assets = groups[platform].orEmpty()
                        if (assets.isEmpty()) return@forEach
                        val isCurrentDevice =
                            assets.any { it.id in installableIds }
                        item(key = "section-${platform.name}") {
                            PlatformSectionCard(
                                platformLabel = stringResource(labelRes),
                                isCurrentDevice = isCurrentDevice,
                                installableIds = installableIds,
                                assets = assets,
                                selectedAsset = selectedAsset,
                                pinnedVariant = pinnedVariant,
                                onAssetClick = { asset ->
                                    if (asset.id in installableIds) {
                                        onSelect(asset)
                                    } else {
                                        onDownloadForTransfer(asset)
                                    }
                                },
                            )
                        }
                    }
                } else if (assetsList.isNotEmpty()) {
                    items(items = assetsList, key = { it.id }) { asset ->
                        val variantTag = AssetVariant.extract(asset.name)
                        val isPinned = !pinnedVariant.isNullOrBlank() &&
                                variantTag?.equals(pinnedVariant, ignoreCase = true) == true
                        ReleaseAssetItem(
                            asset = asset,
                            isSelected = asset.id == selectedAsset?.id,
                            isPinned = isPinned,
                            onClick = { onSelect(asset) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    item {
                        KomiText(
                            text = stringResource(Res.string.no_assets_in_list),
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseAssetsAboutDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
) {
    if (!showDialog) return

    val colors = LocalPersonality.current.colors
    val dialogShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    KomiDialog(onDismissRequest = onDismiss, modifier = modifier, properties = properties) {
        Column(
            modifier = Modifier
                .clip(dialogShape)
                .background(colors.surface)
                .border(
                    width = 1.5.dp,
                    color = colors.outline,
                    shape = dialogShape,
                )
                .padding(24.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.multiple_assets_info_dialog_title),
                role = KomiTextRole.Title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = colors.onSurface,
                uppercase = false,
            )
            Spacer(Modifier.size(12.dp))
            KomiText(
                text = stringResource(Res.string.multiple_assets_info_dialog_text),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReleaseAssetItem(
    asset: GithubAsset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Row(
        modifier =
            modifier
                .clickable(
                    onClickLabel = stringResource(Res.string.assets_selection_label),
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KomiText(
                    text = asset.name,
                    role = KomiTextRole.Stamp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    color =
                        if (isSelected) {
                            colors.primary
                        } else {
                            colors.onSurface
                        },
                    modifier = Modifier.weight(1f, fill = false),
                    uppercase = false,
                )
                if (isPinned) {
                    Spacer(Modifier.width(6.dp))
                    KomiText(
                        text = stringResource(Res.string.variant_picker_pinned_badge),
                        role = KomiTextRole.Label,
                        fontSize = 11.sp,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(shape.cornerSmall))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        uppercase = false,
                    )
                }
            }
            KomiText(
                text = formatFileSize(asset.size),
                role = KomiTextRole.Body,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            KomiIcon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}


@Preview
@Composable
private fun ReleaseAssetsPickerItemPreview() {
    ReleaseAssetItem(
        asset =
            GithubAsset(
                id = -1,
                name = "Random",
                contentType = "",
                size = 20 * 1024,
                downloadUrl = "",
                uploader = GithubUser(id = -1, login = "", avatarUrl = "", htmlUrl = ""),
            ),
        onClick = {},
        isSelected = false,
    )
}

@Composable
private fun PlatformSectionCard(
    platformLabel: String,
    isCurrentDevice: Boolean,
    installableIds: Set<Long>,
    assets: List<GithubAsset>,
    selectedAsset: GithubAsset?,
    pinnedVariant: String?,
    onAssetClick: (GithubAsset) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        elevation = KomiSurfaceElevation.Flat,
        paper = if (isCurrentDevice) {
            zed.rainxch.core.presentation.components.surfaces.KomiSurfacePaper.Surface
        } else {
            zed.rainxch.core.presentation.components.surfaces.KomiSurfacePaper.Background
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KomiText(
                    text = platformLabel,
                    role = KomiTextRole.Title,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                    uppercase = false,
                )
                SectionChip(
                    label =
                        if (isCurrentDevice) {
                            stringResource(Res.string.section_chip_your_device)
                        } else {
                            stringResource(Res.string.section_chip_for_transfer)
                        },
                    isPrimary = isCurrentDevice,
                )
            }

            KomiHorizontalDivider(
                color = colors.outlineVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            assets.forEachIndexed { index, asset ->
                val isInstallableHere = asset.id in installableIds
                val variantTag = AssetVariant.extract(asset.name)
                val isPinned =
                    isInstallableHere &&
                            !pinnedVariant.isNullOrBlank() &&
                            variantTag?.equals(pinnedVariant, ignoreCase = true) == true
                ReleaseAssetItem(
                    asset = asset,
                    isSelected = isInstallableHere && asset.id == selectedAsset?.id,
                    isPinned = isPinned,
                    onClick = { onAssetClick(asset) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (index < assets.lastIndex) {
                    KomiHorizontalDivider(
                        color = colors.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionChip(
    label: String,
    isPrimary: Boolean,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val container = colors.primaryContainer
    val content = colors.onPrimaryContainer
    KomiText(
        text = label,
        role = KomiTextRole.Label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(shape.cornerSmall))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        uppercase = false,
    )
}
