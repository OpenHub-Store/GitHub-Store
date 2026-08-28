package zed.rainxch.details.presentation.components

import zed.rainxch.core.presentation.utils.formatFileSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.DetailsState
import zed.rainxch.details.presentation.model.AttestationStatus
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.details.presentation.utils.extractArchitectureFromName
import zed.rainxch.details.presentation.utils.isExactArchitectureMatch
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.architecture_compatible
import zed.rainxch.githubstore.core.presentation.res.cancel_download
import zed.rainxch.githubstore.core.presentation.res.checking_attestation
import zed.rainxch.githubstore.core.presentation.res.downloading
import zed.rainxch.githubstore.core.presentation.res.install_latest
import zed.rainxch.githubstore.core.presentation.res.install_ready
import zed.rainxch.githubstore.core.presentation.res.install_version
import zed.rainxch.githubstore.core.presentation.res.installing
import zed.rainxch.githubstore.core.presentation.res.not_available
import zed.rainxch.githubstore.core.presentation.res.open_app
import zed.rainxch.githubstore.core.presentation.res.show_install_options
import zed.rainxch.githubstore.core.presentation.res.unable_to_verify_attestation
import zed.rainxch.githubstore.core.presentation.res.uninstall
import zed.rainxch.githubstore.core.presentation.res.update_to_version
import zed.rainxch.githubstore.core.presentation.res.updating
import zed.rainxch.githubstore.core.presentation.res.verified_build
import zed.rainxch.githubstore.core.presentation.res.verifying

private val ButtonHeight = 56.dp

@Composable
fun SmartInstallButton(
    isDownloading: Boolean,
    isInstalling: Boolean,
    progress: Int?,
    primaryAsset: GithubAsset?,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    state: DetailsState,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val installedApp = state.installedApp
    val isInstalled = installedApp != null && !installedApp.isPendingInstall
    val isUpdateAvailable =
        installedApp?.isUpdateAvailable == true && !installedApp.isPendingInstall

    val normInstalled = installedApp?.installedVersion?.trim()?.takeIf { it.isNotBlank() }
    val normSelected = state.selectedRelease?.tagName?.trim()?.takeIf { it.isNotBlank() }
    val displaySelected = normSelected?.let { tag ->
        VersionMath.normalizeVersion(tag).takeIf { it.isNotBlank() } ?: tag
    }
    val isSameVersionInstalled =
        isInstalled &&
            normInstalled != null &&
            normSelected != null &&
            VersionMath.isExactSameVersion(normInstalled, normSelected)

    val enabled = remember(primaryAsset, isDownloading, isInstalling) {
        primaryAsset != null && !isDownloading && !isInstalling
    }
    val isActiveDownload = state.isDownloading || state.downloadStage != DownloadStage.IDLE

    if (isSameVersionInstalled && !isActiveDownload) {
        Column(modifier = modifier) {
            InstalledSplitRow(
                onUninstall = { onAction(DetailsAction.OnRequestUninstall) },
                onOpenApp = { onAction(DetailsAction.OpenApp) },
            )
            AttestationBadge(attestationStatus = state.attestationStatus)
        }
        return
    }

    val accent = when {
        !enabled && !isActiveDownload -> colors.surfaceContainerHigh
        else -> colors.primary
    }
    val onAccent = when {
        !enabled && !isActiveDownload -> colors.onSurface.copy(alpha = 0.45f)
        else -> colors.onPrimary
    }

    val buttonText = when {
        !enabled && primaryAsset == null -> stringResource(Res.string.not_available)
        state.isPendingInstallReady -> stringResource(Res.string.install_ready)
        isUpdateAvailable -> stringResource(
            Res.string.update_to_version,
            installedApp.latestVersion.toString(),
        )
        isInstalled &&
            normInstalled != null &&
            normSelected != null &&
            !VersionMath.isExactSameVersion(normInstalled, normSelected) -> {
            stringResource(Res.string.install_version, displaySelected ?: normSelected)
        }
        normSelected != null &&
            state.allReleases.firstOrNull()?.tagName?.let { latestTag ->
                !VersionMath.isExactSameVersion(latestTag, normSelected)
            } == true -> {
            stringResource(Res.string.install_version, displaySelected ?: normSelected)
        }
        else -> stringResource(Res.string.install_latest)
    }

    val hasTrailing = isActiveDownload || state.isObtainiumEnabled

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val primaryShape = if (hasTrailing) {
                RoundedCornerShape(
                    topStart = shape.corner,
                    bottomStart = shape.corner,
                    topEnd = shape.cornerSmall,
                    bottomEnd = shape.cornerSmall,
                )
            } else {
                RoundedCornerShape(shape.corner)
            }
            PrimaryAction(
                modifier = Modifier.weight(1f),
                shape = primaryShape,
                accent = accent,
                onAccent = onAccent,
                enabled = enabled,
                isActiveDownload = isActiveDownload,
                isUpdateAvailable = isUpdateAvailable,
                isInstalled = isInstalled,
                buttonText = buttonText,
                primaryAsset = primaryAsset,
                state = state,
                progress = progress,
                onClick = {
                    if (!state.isDownloading && state.downloadStage == DownloadStage.IDLE) {
                        if (isUpdateAvailable) {
                            onAction(DetailsAction.UpdateApp)
                        } else {
                            onAction(DetailsAction.InstallPrimary)
                        }
                    }
                },
            )

            if (isActiveDownload) {
                TrailingActionPill(
                    container = colors.error,
                    content = colors.onError,
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.cancel_download),
                    onClick = { onAction(DetailsAction.CancelCurrentDownload) },
                )
            } else if (state.isObtainiumEnabled) {
                TrailingActionPill(
                    container = if (enabled) accent else colors.surfaceContainerHigh,
                    content = onAccent,
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.show_install_options),
                    onClick = { onAction(DetailsAction.OnToggleInstallDropdown) },
                )
            }
        }

        AttestationBadge(attestationStatus = state.attestationStatus)
    }
}

@Composable
private fun PrimaryAction(
    modifier: Modifier,
    shape: RoundedCornerShape,
    accent: Color,
    onAccent: Color,
    enabled: Boolean,
    isActiveDownload: Boolean,
    isUpdateAvailable: Boolean,
    isInstalled: Boolean,
    buttonText: String,
    primaryAsset: GithubAsset?,
    state: DetailsState,
    progress: Int?,
    onClick: () -> Unit,
) {
    val pct = progress?.coerceIn(0, 100) ?: 0
    val targetFraction = if (isActiveDownload && state.downloadStage == DownloadStage.DOWNLOADING) {
        pct / 100f
    } else if (isActiveDownload) {
        1f
    } else {
        0f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 350),
        label = "install-progress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .clip(shape)
            .drawBehind {
                drawRect(accent.copy(alpha = if (isActiveDownload) 0.35f else 1f))
                if (isActiveDownload) {
                    drawRect(
                        color = accent,
                        size = Size(size.width * animatedFraction, size.height),
                    )
                }
            }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (isActiveDownload) {
                DownloadingLabel(
                    state = state,
                    progress = progress,
                    contentColor = onAccent,
                    isUpdateAvailable = isUpdateAvailable,
                )
            } else {
                IdleLabel(
                    text = buttonText,
                    enabled = enabled,
                    contentColor = onAccent,
                    isUpdateAvailable = isUpdateAvailable,
                    isInstalled = isInstalled,
                    primaryAsset = primaryAsset,
                    state = state,
                )
            }
        }
    }
}

@Composable
private fun IdleLabel(
    text: String,
    enabled: Boolean,
    contentColor: Color,
    isUpdateAvailable: Boolean,
    isInstalled: Boolean,
    primaryAsset: GithubAsset?,
    state: DetailsState,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val leadingIcon = when {
                isUpdateAvailable -> Icons.Default.Update
                isInstalled -> Icons.Default.CheckCircle
                enabled -> Icons.Default.Download
                else -> null
            }
            if (leadingIcon != null) {
                KomiIcon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor,
                )
            }
            KomiText(
                text = text,
                role = KomiTextRole.Title,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                uppercase = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (primaryAsset != null) {
            val assetArch = extractArchitectureFromName(primaryAsset.name)
            val systemArch = state.systemArchitecture
            val sizeText = formatFileSize(primaryAsset.size)
            val archLabel = assetArch ?: systemArch.name.lowercase()
            val subtitle = "$archLabel  ·  $sizeText"
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KomiText(
                    text = subtitle,
                    role = KomiTextRole.Label,
                    color = contentColor.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    uppercase = false,
                )
                if (assetArch != null && isExactArchitectureMatch(
                        assetName = primaryAsset.name.lowercase(),
                        systemArch = systemArch,
                    )
                ) {
                    Spacer(modifier = Modifier.width(4.dp))
                    KomiIcon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(Res.string.architecture_compatible),
                        tint = contentColor.copy(alpha = 0.78f),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadingLabel(
    state: DetailsState,
    progress: Int?,
    contentColor: Color,
    isUpdateAvailable: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val label = when (state.downloadStage) {
            DownloadStage.DOWNLOADING -> if (isUpdateAvailable) {
                stringResource(Res.string.updating)
            } else {
                stringResource(Res.string.downloading)
            }
            DownloadStage.VERIFYING -> stringResource(Res.string.verifying)
            DownloadStage.INSTALLING -> if (isUpdateAvailable) {
                stringResource(Res.string.updating)
            } else {
                stringResource(Res.string.installing)
            }
            DownloadStage.IDLE -> ""
        }
        KomiText(
            text = label,
            role = KomiTextRole.Title,
            fontSize = 15.sp,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            uppercase = false,
        )
        if (state.downloadStage == DownloadStage.DOWNLOADING) {
            Spacer(Modifier.height(2.dp))
            val progressText = if (state.totalBytes != null && state.totalBytes > 0) {
                "${formatFileSize(state.downloadedBytes)} / ${formatFileSize(state.totalBytes)}"
            } else {
                "${progress ?: 0}%"
            }
            KomiText(
                text = progressText,
                role = KomiTextRole.Label,
                fontSize = 11.sp,
                color = contentColor.copy(alpha = 0.78f),
                uppercase = false,
            )
        }
    }
}

@Composable
private fun TrailingActionPill(
    container: Color,
    content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val shape = LocalPersonality.current.shape
    val pillShape = RoundedCornerShape(
        topStart = shape.cornerSmall,
        bottomStart = shape.cornerSmall,
        topEnd = shape.corner,
        bottomEnd = shape.corner,
    )
    Box(
        modifier = Modifier
            .size(width = ButtonHeight, height = ButtonHeight)
            .clip(pillShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KomiIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun InstalledSplitRow(
    onUninstall: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val leftShape = RoundedCornerShape(
            topStart = shape.corner,
            bottomStart = shape.corner,
            topEnd = shape.cornerSmall,
            bottomEnd = shape.cornerSmall,
        )
        val rightShape = RoundedCornerShape(
            topStart = shape.cornerSmall,
            bottomStart = shape.cornerSmall,
            topEnd = shape.corner,
            bottomEnd = shape.corner,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight)
                .clip(leftShape)
                .border(
                    width = 1.dp,
                    color = colors.error.copy(alpha = 0.55f),
                    shape = leftShape,
                )
                .clickable(onClick = onUninstall),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KomiIcon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.error,
                )
                KomiText(
                    text = stringResource(Res.string.uninstall),
                    role = KomiTextRole.Title,
                    color = colors.error,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    uppercase = false,
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight)
                .clip(rightShape)
                .background(colors.primary)
                .clickable(onClick = onOpenApp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KomiIcon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.onPrimary,
                )
                KomiText(
                    text = stringResource(Res.string.open_app),
                    role = KomiTextRole.Title,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    uppercase = false,
                )
            }
        }
    }
}

@Composable
private fun AttestationBadge(attestationStatus: AttestationStatus) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    AnimatedVisibility(
        visible = attestationStatus == AttestationStatus.VERIFIED ||
            attestationStatus == AttestationStatus.CHECKING ||
            attestationStatus == AttestationStatus.UNABLE_TO_VERIFY,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val (container, content, icon, label) = when (attestationStatus) {
            AttestationStatus.VERIFIED -> AttestationVisual(
                container = colors.primaryContainer,
                content = colors.onPrimaryContainer,
                icon = Icons.Filled.VerifiedUser,
                label = stringResource(Res.string.verified_build),
            )
            AttestationStatus.UNABLE_TO_VERIFY -> AttestationVisual(
                container = colors.surfaceContainerHigh,
                content = colors.onSurfaceVariant,
                icon = Icons.Outlined.Warning,
                label = stringResource(Res.string.unable_to_verify_attestation),
            )
            AttestationStatus.CHECKING -> AttestationVisual(
                container = colors.surfaceContainerHigh,
                content = colors.onSurfaceVariant,
                icon = null,
                label = stringResource(Res.string.checking_attestation),
            )
            else -> AttestationVisual(
                container = Color.Transparent,
                content = Color.Transparent,
                icon = null,
                label = "",
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(shape.cornerSmall))
                .background(container)
                .border(
                    width = 0.5.dp,
                    color = colors.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(shape.cornerSmall),
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (attestationStatus == AttestationStatus.CHECKING) {
                    KomiCircularProgress(
                        modifier = Modifier.size(13.dp),
                        color = content,
                    )
                } else if (icon != null) {
                    KomiIcon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = content,
                    )
                }
                KomiText(
                    text = label,
                    role = KomiTextRole.Label,
                    fontSize = 11.sp,
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                    uppercase = false,
                )
            }
        }
    }
}

private data class AttestationVisual(
    val container: Color,
    val content: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val label: String,
)
