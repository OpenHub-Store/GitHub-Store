package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.getPlatform
import zed.rainxch.core.domain.model.InstallerType
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.core.domain.model.ShizukuAvailability
import zed.rainxch.core.presentation.components.ExpressiveCard
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.installationSection(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
) {
    if (getPlatform() != Platform.ANDROID) return

    item {
        Spacer(Modifier.height(32.dp))

        SectionHeader(
            text = stringResource(Res.string.section_installation).uppercase()
        )

        Spacer(Modifier.height(8.dp))

        InstallerTypeCard(
            selectedType = state.installerType,
            shizukuAvailability = state.shizukuAvailability,
            onTypeSelected = { type ->
                onAction(TweaksAction.OnInstallerTypeSelected(type))
            },
            onRequestPermission = {
                onAction(TweaksAction.OnRequestShizukuPermission)
            }
        )

        // Auto-update toggle — only shown when Shizuku is selected and ready
        if (state.installerType == InstallerType.SHIZUKU &&
            state.shizukuAvailability == ShizukuAvailability.READY
        ) {
            Spacer(Modifier.height(12.dp))

            AutoUpdateCard(
                enabled = state.autoUpdateEnabled,
                onToggle = { enabled ->
                    onAction(TweaksAction.OnAutoUpdateToggled(enabled))
                }
            )
        }
    }
}

/**
 * Updates section — always visible on Android (not gated on Shizuku).
 * Shows the update check interval picker so all users can configure
 * how often background update checks run.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.updatesSection(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
) {
    if (getPlatform() != Platform.ANDROID) return

    item {
        Spacer(Modifier.height(32.dp))

        SectionHeader(
            text = stringResource(Res.string.section_updates).uppercase()
        )

        Spacer(Modifier.height(8.dp))

        UpdateCheckIntervalCard(
            selectedIntervalHours = state.updateCheckIntervalHours,
            onIntervalSelected = { hours ->
                onAction(TweaksAction.OnUpdateCheckIntervalChanged(hours))
            }
        )

        Spacer(Modifier.height(12.dp))

        PreReleaseToggleCard(
            enabled = state.includePreReleases,
            onToggle = { enabled ->
                onAction(TweaksAction.OnIncludePreReleasesToggled(enabled))
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallerTypeCard(
    selectedType: InstallerType,
    shizukuAvailability: ShizukuAvailability,
    onTypeSelected: (InstallerType) -> Unit,
    onRequestPermission: () -> Unit
) {
    ExpressiveCard {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InstallerOption(
                icon = Icons.Outlined.InstallMobile,
                title = stringResource(Res.string.installer_type_default),
                description = stringResource(Res.string.installer_type_default_description),
                isSelected = selectedType == InstallerType.DEFAULT,
                onClick = { onTypeSelected(InstallerType.DEFAULT) }
            )

            InstallerOption(
                icon = Icons.Outlined.Speed,
                title = stringResource(Res.string.installer_type_shizuku),
                description = stringResource(Res.string.installer_type_shizuku_description),
                isSelected = selectedType == InstallerType.SHIZUKU,
                onClick = { onTypeSelected(InstallerType.SHIZUKU) },
                statusBadge = {
                    ShizukuStatusBadge(
                        availability = shizukuAvailability
                    )
                }
            )

            when (shizukuAvailability) {
                ShizukuAvailability.PERMISSION_NEEDED -> {
                    FilledTonalButton(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.shizuku_grant_permission),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                ShizukuAvailability.UNAVAILABLE -> {
                    HintText(text = stringResource(Res.string.shizuku_install_hint))
                }

                ShizukuAvailability.NOT_RUNNING -> {
                    HintText(text = stringResource(Res.string.shizuku_start_hint))
                }

                ShizukuAvailability.READY -> {
                    // No hint needed
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallerOption(
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    statusBadge: (@Composable () -> Unit)? = null
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                )
                .padding(8.dp),
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        if (statusBadge != null) {
            statusBadge()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShizukuStatusBadge(
    availability: ShizukuAvailability
) {
    val (color, label) = when (availability) {
        ShizukuAvailability.READY -> Pair(
            Color(0xFF4CAF50),
            stringResource(Res.string.shizuku_status_ready)
        )

        ShizukuAvailability.PERMISSION_NEEDED -> Pair(
            Color(0xFFFF9800),
            stringResource(Res.string.shizuku_status_permission_needed)
        )

        ShizukuAvailability.NOT_RUNNING -> Pair(
            Color(0xFFFF5722),
            stringResource(Res.string.shizuku_status_not_running)
        )

        ShizukuAvailability.UNAVAILABLE -> Pair(
            MaterialTheme.colorScheme.outline,
            stringResource(Res.string.shizuku_status_not_installed)
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AutoUpdateCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ExpressiveCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(Res.string.auto_update_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(Res.string.auto_update_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateCheckIntervalCard(
    selectedIntervalHours: Long,
    onIntervalSelected: (Long) -> Unit,
) {
    val intervals = listOf(
        3L to Res.string.interval_3h,
        6L to Res.string.interval_6h,
        12L to Res.string.interval_12h,
        24L to Res.string.interval_24h,
    )

    ExpressiveCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.update_check_interval_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(Res.string.update_check_interval_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                intervals.forEach { (hours, labelRes) ->
                    val isSelected = selectedIntervalHours == hours

                    FilterChip(
                        selected = isSelected,
                        onClick = { onIntervalSelected(hours) },
                        label = {
                            Text(
                                text = stringResource(labelRes),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreReleaseToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ExpressiveCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(Res.string.include_pre_releases_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(Res.string.include_pre_releases_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
    )
}
