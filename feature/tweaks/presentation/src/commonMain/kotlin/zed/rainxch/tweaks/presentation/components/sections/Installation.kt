package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.model.installation.DhizukuAvailability
import zed.rainxch.core.domain.model.installation.InstallerAttribution
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.model.installation.PresetKey
import zed.rainxch.core.domain.model.installation.RootAvailability
import zed.rainxch.core.domain.model.installation.ShizukuAvailability
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.inputs.KomiRadioButton
import zed.rainxch.core.presentation.components.inputs.KomiSlider
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalStatusColors
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.components.shell.SettingsDrillRow
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow

@Composable
fun installSectionContent(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isDesktop()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsGroup {
            InstallerTypeRow(
                type = InstallerType.DEFAULT,
                title = stringResource(Res.string.installer_type_default),
                description = stringResource(Res.string.installer_type_default_description),
                selected = state.installerType == InstallerType.DEFAULT,
                onClick = { onAction(TweaksAction.OnInstallerTypeSelected(InstallerType.DEFAULT)) },
            )
            InstallerTypeRow(
                type = InstallerType.SHIZUKU,
                title = stringResource(Res.string.installer_type_shizuku),
                description = stringResource(Res.string.installer_type_shizuku_description),
                selected = state.installerType == InstallerType.SHIZUKU,
                onClick = { onAction(TweaksAction.OnInstallerTypeSelected(InstallerType.SHIZUKU)) },
                badge = { ShizukuStatusBadge(state.shizukuAvailability) },
            )
            if (state.installerType == InstallerType.SHIZUKU) {
                ShizukuActions(state.shizukuAvailability) { onAction(TweaksAction.OnRequestShizukuPermission) }
            }
            InstallerTypeRow(
                type = InstallerType.DHIZUKU,
                title = stringResource(Res.string.installer_type_dhizuku),
                description = stringResource(Res.string.installer_type_dhizuku_description),
                selected = state.installerType == InstallerType.DHIZUKU,
                onClick = { onAction(TweaksAction.OnInstallerTypeSelected(InstallerType.DHIZUKU)) },
                badge = { DhizukuStatusBadge(state.dhizukuAvailability) },
            )
            if (state.installerType == InstallerType.DHIZUKU) {
                DhizukuActions(state.dhizukuAvailability) { onAction(TweaksAction.OnRequestDhizukuPermission) }
            }
            InstallerTypeRow(
                type = InstallerType.ROOT,
                title = stringResource(Res.string.installer_type_root),
                description = stringResource(Res.string.installer_type_root_description),
                selected = state.installerType == InstallerType.ROOT,
                onClick = { onAction(TweaksAction.OnInstallerTypeSelected(InstallerType.ROOT)) },
                badge = { RootStatusBadge(state.rootAvailability) },
                last = true,
            )
            if (state.installerType == InstallerType.ROOT) {
                RootActions(state.rootAvailability) { onAction(TweaksAction.OnRequestRootPermission) }
            }
        }

        val silentReady =
            (state.installerType == InstallerType.SHIZUKU && state.shizukuAvailability == ShizukuAvailability.READY) ||
                (state.installerType == InstallerType.DHIZUKU && state.dhizukuAvailability == DhizukuAvailability.READY) ||
                (state.installerType == InstallerType.ROOT && state.rootAvailability == RootAvailability.READY)
        if (silentReady) {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(Res.string.auto_update_title),
                    subtitle = stringResource(Res.string.auto_update_description),
                    last = true,
                    trailing = {
                        KomiSwitch(
                            checked = state.autoUpdateEnabled,
                            onCheckedChange = { onAction(TweaksAction.OnAutoUpdateToggled(it)) },
                        )
                    },
                )
            }
            SettingsGroup {
                AttributionRows(state, onAction)
            }
        }
    }
}

@Composable
fun updatesSectionContent(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSkippedUpdates: () -> Unit,
) {
    if (isDesktop()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.showBatteryOptimizationCard) {
            BatteryOptimizationGroup(
                onOpenSettings = { onAction(TweaksAction.OnOpenBatteryOptimizationSettings) },
                onDismiss = { onAction(TweaksAction.OnDismissBatteryOptimizationCard) },
            )
        }
        SettingsGroup {
            SettingsRow(
                title = stringResource(Res.string.update_check_enabled_title),
                subtitle = stringResource(Res.string.update_check_enabled_description),
                trailing = {
                    KomiSwitch(
                        checked = state.updateCheckEnabled,
                        onCheckedChange = { onAction(TweaksAction.OnUpdateCheckEnabledToggled(it)) },
                    )
                },
            )
            IntervalBlock(
                selectedIntervalHours = state.updateCheckIntervalHours,
                enabled = state.updateCheckEnabled,
                onIntervalSelected = { onAction(TweaksAction.OnUpdateCheckIntervalChanged(it)) },
            )
            SettingsRow(
                title = stringResource(Res.string.include_pre_releases_title),
                subtitle = stringResource(Res.string.include_pre_releases_description),
                trailing = {
                    KomiSwitch(
                        checked = state.includePreReleases,
                        onCheckedChange = { onAction(TweaksAction.OnIncludePreReleasesToggled(it)) },
                    )
                },
            )
            SettingsDrillRow(
                title = stringResource(Res.string.skipped_updates_entry_title),
                subtitle = stringResource(Res.string.skipped_updates_entry_description),
                onClick = onNavigateToSkippedUpdates,
                last = true,
            )
        }
    }
}

@Composable
private fun InstallerTypeRow(
    type: InstallerType,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)? = null,
    last: Boolean = false,
) {
    SettingsRow(
        title = title,
        subtitle = description,
        last = last,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                badge?.invoke()
                KomiRadioButton(selected = selected, onClick = onClick)
            }
        },
    )
}

@Composable
private fun AttributionRows(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
) {
    val attribution = state.installerAttribution
    AttributionRadioRow(
        title = stringResource(Res.string.installer_attribution_preset_system),
        selected = attribution is InstallerAttribution.SystemDefault,
        onClick = { onAction(TweaksAction.OnInstallerAttributionSystemDefault) },
    )
    AttributionRadioRow(
        title = stringResource(Res.string.installer_attribution_preset_playstore),
        subtitle = "com.android.vending",
        selected = (attribution as? InstallerAttribution.Preset)?.key == PresetKey.PLAY_STORE,
        onClick = { onAction(TweaksAction.OnInstallerAttributionPresetSelected(PresetKey.PLAY_STORE)) },
    )
    AttributionRadioRow(
        title = stringResource(Res.string.installer_attribution_preset_fdroid),
        subtitle = "org.fdroid.fdroid",
        selected = (attribution as? InstallerAttribution.Preset)?.key == PresetKey.FDROID,
        onClick = { onAction(TweaksAction.OnInstallerAttributionPresetSelected(PresetKey.FDROID)) },
    )
    AttributionRadioRow(
        title = stringResource(Res.string.installer_attribution_preset_obtainium),
        subtitle = "dev.imranr.obtainium.app",
        selected = (attribution as? InstallerAttribution.Preset)?.key == PresetKey.OBTAINIUM,
        onClick = { onAction(TweaksAction.OnInstallerAttributionPresetSelected(PresetKey.OBTAINIUM)) },
    )
    AttributionRadioRow(
        title = stringResource(Res.string.installer_attribution_preset_custom),
        subtitle = (attribution as? InstallerAttribution.Custom)?.packageName,
        selected = attribution is InstallerAttribution.Custom,
        onClick = { onAction(TweaksAction.OnInstallerAttributionCustomToggleExpanded) },
        last = !(state.installerAttributionCustomExpanded || attribution is InstallerAttribution.Custom),
    )
    if (state.installerAttributionCustomExpanded || attribution is InstallerAttribution.Custom) {
        Column(
            modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KomiTextField(
                value = state.installerAttributionCustomDraft,
                onValueChange = { onAction(TweaksAction.OnInstallerAttributionCustomChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.installer_attribution_custom_label),
                placeholder = "com.example.installer",
                error = state.installerAttributionCustomError?.let { stringResource(Res.string.installer_attribution_custom_error) },
            )
            KomiButton(
                onClick = { onAction(TweaksAction.OnInstallerAttributionCustomSave) },
                label = stringResource(Res.string.installer_attribution_custom_apply),
                variant = KomiButtonVariant.Tonal,
                size = KomiButtonSize.Sm,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun AttributionRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    last: Boolean = false,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        last = last,
        onClick = onClick,
        trailing = { KomiRadioButton(selected = selected, onClick = onClick) },
    )
}

@Composable
private fun ShizukuActions(availability: ShizukuAvailability, onRequest: () -> Unit) {
    when (availability) {
        ShizukuAvailability.PERMISSION_NEEDED ->
            ActionButton(stringResource(Res.string.shizuku_grant_permission), onRequest)
        ShizukuAvailability.UNAVAILABLE -> HintRow(stringResource(Res.string.shizuku_install_hint))
        ShizukuAvailability.NOT_RUNNING -> HintRow(stringResource(Res.string.shizuku_start_hint))
        ShizukuAvailability.READY -> Unit
    }
}

@Composable
private fun DhizukuActions(availability: DhizukuAvailability, onRequest: () -> Unit) {
    when (availability) {
        DhizukuAvailability.PERMISSION_NEEDED ->
            ActionButton(stringResource(Res.string.dhizuku_grant_permission), onRequest)
        DhizukuAvailability.UNAVAILABLE -> HintRow(stringResource(Res.string.dhizuku_install_hint))
        DhizukuAvailability.NOT_RUNNING -> HintRow(stringResource(Res.string.dhizuku_start_hint))
        DhizukuAvailability.READY -> Unit
    }
}

@Composable
private fun RootActions(availability: RootAvailability, onRequest: () -> Unit) {
    when (availability) {
        RootAvailability.PERMISSION_NEEDED ->
            ActionButton(stringResource(Res.string.root_grant_permission), onRequest)
        RootAvailability.UNAVAILABLE -> {
            HintRow(stringResource(Res.string.root_unavailable_hint))
            ActionButton(stringResource(Res.string.retry), onRequest)
        }
        RootAvailability.READY -> Unit
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp)) {
        KomiButton(
            onClick = onClick,
            label = label,
            variant = KomiButtonVariant.Tonal,
            size = KomiButtonSize.Sm,
            fullWidth = true,
        )
    }
}

@Composable
private fun HintRow(text: String) {
    KomiText(
        text = text,
        role = KomiTextRole.Body,
        fontSize = 13.sp,
        color = LocalPersonality.current.colors.onSurfaceVariant,
        uppercase = false,
        modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp),
    )
}

@Composable
private fun ShizukuStatusBadge(availability: ShizukuAvailability) {
    val (color, label) =
        when (availability) {
            ShizukuAvailability.READY -> LocalStatusColors.current.statusReady to stringResource(Res.string.shizuku_status_ready)
            ShizukuAvailability.PERMISSION_NEEDED -> LocalStatusColors.current.statusWarning to stringResource(Res.string.shizuku_status_permission_needed)
            ShizukuAvailability.NOT_RUNNING -> LocalStatusColors.current.statusError to stringResource(Res.string.shizuku_status_not_running)
            ShizukuAvailability.UNAVAILABLE -> LocalPersonality.current.colors.outline to stringResource(Res.string.shizuku_status_not_installed)
        }
    StatusDot(color, label)
}

@Composable
private fun DhizukuStatusBadge(availability: DhizukuAvailability) {
    val (color, label) =
        when (availability) {
            DhizukuAvailability.READY -> LocalStatusColors.current.statusReady to stringResource(Res.string.dhizuku_status_ready)
            DhizukuAvailability.PERMISSION_NEEDED -> LocalStatusColors.current.statusWarning to stringResource(Res.string.dhizuku_status_permission_needed)
            DhizukuAvailability.NOT_RUNNING -> LocalStatusColors.current.statusError to stringResource(Res.string.dhizuku_status_not_running)
            DhizukuAvailability.UNAVAILABLE -> LocalPersonality.current.colors.outline to stringResource(Res.string.dhizuku_status_not_installed)
        }
    StatusDot(color, label)
}

@Composable
private fun RootStatusBadge(availability: RootAvailability) {
    val (color, label) =
        when (availability) {
            RootAvailability.READY -> LocalStatusColors.current.statusReady to stringResource(Res.string.root_status_ready)
            RootAvailability.PERMISSION_NEEDED -> LocalStatusColors.current.statusWarning to stringResource(Res.string.root_status_permission_needed)
            RootAvailability.UNAVAILABLE -> LocalPersonality.current.colors.outline to stringResource(Res.string.root_status_unavailable)
        }
    StatusDot(color, label)
}

@Composable
private fun StatusDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)).background(color))
        KomiText(text = label, role = KomiTextRole.Label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium, uppercase = false)
    }
}

private val IntervalStops: List<Long> = listOf(3L, 6L, 12L, 24L, 72L, 168L, 336L, 720L)

@Composable
private fun formatIntervalLabel(hours: Long): String {
    val days = hours / 24
    return when {
        hours < 24 -> stringResource(Res.string.interval_every_hours, hours.toInt())
        hours == 24L -> stringResource(Res.string.interval_daily)
        hours == 168L -> stringResource(Res.string.interval_weekly)
        hours == 336L -> stringResource(Res.string.interval_biweekly)
        hours == 720L -> stringResource(Res.string.interval_monthly)
        else -> stringResource(Res.string.interval_every_days, days.toInt())
    }
}

@Composable
private fun IntervalBlock(
    selectedIntervalHours: Long,
    enabled: Boolean,
    onIntervalSelected: (Long) -> Unit,
) {
    val currentIndex =
        IntervalStops.indexOf(selectedIntervalHours)
            .let { if (it == -1) IntervalStops.indexOf(IntervalStops.minByOrNull { stop -> kotlin.math.abs(stop - selectedIntervalHours) }) else it }
            .coerceAtLeast(0)
    val maxIndex = IntervalStops.lastIndex
    val colors = LocalPersonality.current.colors
    Column(modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 4.dp, bottom = 12.dp)) {
        KomiText(
            text = stringResource(Res.string.update_check_interval_title),
            role = KomiTextRole.Label,
            color = colors.onSurfaceVariant,
            uppercase = false,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        KomiText(
            text = formatIntervalLabel(IntervalStops[currentIndex]),
            role = KomiTextRole.Title,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) colors.primary else colors.onSurfaceVariant,
            uppercase = false,
        )
        Spacer(Modifier.height(6.dp))
        KomiSlider(
            value = currentIndex.toFloat(),
            onValueChange = { v -> onIntervalSelected(IntervalStops[v.toInt().coerceIn(0, maxIndex)]) },
            steps = maxIndex - 1,
            valueRange = 0f..maxIndex.toFloat(),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BatteryOptimizationGroup(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    SettingsGroup {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KomiText(
                text = stringResource(Res.string.battery_optimization_card_title),
                role = KomiTextRole.Title,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                uppercase = false,
            )
            KomiText(
                text = stringResource(Res.string.battery_optimization_card_description),
                role = KomiTextRole.Body,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                uppercase = false,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                KomiButton(
                    onClick = onDismiss,
                    label = stringResource(Res.string.battery_optimization_card_dismiss),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
                KomiButton(
                    onClick = onOpenSettings,
                    label = stringResource(Res.string.battery_optimization_card_open),
                    variant = KomiButtonVariant.Tonal,
                    size = KomiButtonSize.Sm,
                )
            }
        }
    }
}
