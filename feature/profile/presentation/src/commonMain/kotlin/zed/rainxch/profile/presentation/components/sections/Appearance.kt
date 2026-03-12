package zed.rainxch.profile.presentation.components.sections

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.AppTheme
import zed.rainxch.core.domain.model.FontTheme
import zed.rainxch.core.presentation.components.ExpressiveCard
import zed.rainxch.core.presentation.theme.isDynamicColorAvailable
import zed.rainxch.core.presentation.utils.displayName
import zed.rainxch.core.presentation.utils.primaryColor
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.profile.presentation.ProfileAction
import zed.rainxch.profile.presentation.ProfileState
import zed.rainxch.profile.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.appearanceSection(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
) {
    item {
        SectionHeader(
            text = stringResource(Res.string.section_appearance),
        )

        VerticalSpacer(8.dp)

        ThemeSelectionCard(
            isDarkTheme = state.isDarkTheme,
            onDarkThemeChange = { isDarkTheme ->
                onAction(ProfileAction.OnDarkThemeChange(isDarkTheme))
            },
        )

        VerticalSpacer(12.dp)

        ThemeColorCard(
            selectedThemeColor = state.selectedThemeColor,
            onThemeColorSelected = { theme ->
                onAction(ProfileAction.OnThemeColorSelected(theme))
            },
        )

        VerticalSpacer(16.dp)

        if (state.isDarkTheme == true || (state.isDarkTheme == null && isSystemInDarkTheme())) {
            ToggleSettingCard(
                title = stringResource(Res.string.amoled_black_theme),
                description = stringResource(Res.string.amoled_black_description),
                checked = state.isAmoledThemeEnabled,
                onCheckedChange = { enabled ->
                    onAction(ProfileAction.OnAmoledThemeToggled(enabled))
                },
            )

            VerticalSpacer(8.dp)
        }

        ToggleSettingCard(
            title = stringResource(Res.string.system_font),
            description = stringResource(Res.string.system_font_description),
            checked = state.selectedFontTheme == FontTheme.SYSTEM,
            onCheckedChange = { enabled ->
                onAction(
                    ProfileAction.OnFontThemeSelected(
                        if (enabled) {
                            FontTheme.SYSTEM
                        } else {
                            FontTheme.CUSTOM
                        },
                    ),
                )
            },
        )

        VerticalSpacer(8.dp)

        ToggleSettingCard(
            title = stringResource(Res.string.auto_detect_clipboard_links),
            description = stringResource(Res.string.auto_detect_clipboard_description),
            checked = state.autoDetectClipboardLinks,
            onCheckedChange = { enabled ->
                onAction(ProfileAction.OnAutoDetectClipboardToggled(enabled))
            },
        )
    }
}

@Composable
private fun VerticalSpacer(height: Dp) {
    Spacer(Modifier.height(height))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeSelectionCard(
    isDarkTheme: Boolean?,
    onDarkThemeChange: (Boolean?) -> Unit,
) {
    ExpressiveCard {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeModeOption(
                icon = Icons.Default.LightMode,
                label = stringResource(Res.string.theme_light),
                isSelected = isDarkTheme != null && !isDarkTheme,
                onClick = { onDarkThemeChange(false) },
                modifier = Modifier.weight(1f),
            )

            ThemeModeOption(
                icon = Icons.Default.DarkMode,
                label = stringResource(Res.string.theme_dark),
                isSelected = isDarkTheme == true,
                onClick = { onDarkThemeChange(true) },
                modifier = Modifier.weight(1f),
            )

            ThemeModeOption(
                icon = Icons.Default.Colorize,
                label = stringResource(Res.string.theme_system),
                isSelected = isDarkTheme == null,
                onClick = { onDarkThemeChange(null) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModeOption(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
    )

    Column(
        modifier =
            modifier
                .scale(scale)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ).clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint =
                if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )

        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeColorCard(
    selectedThemeColor: AppTheme,
    onThemeColorSelected: (AppTheme) -> Unit,
) {
    ExpressiveCard {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.theme_color),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )

            VerticalSpacer(12.dp)

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val availableThemes =
                    if (isDynamicColorAvailable()) {
                        AppTheme.entries
                    } else {
                        AppTheme.entries.filter { it != AppTheme.DYNAMIC }
                    }

                items(availableThemes) { theme ->
                    ThemeColorOption(
                        theme = theme,
                        isSelected = selectedThemeColor == theme,
                        onClick = { onThemeColorSelected(theme) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeColorOption(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
    )

    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .scale(scale)
                    .clip(
                        if (isSelected) {
                            MaterialShapes.Cookie9Sided.toShape()
                        } else {
                            CircleShape
                        },
                    ).background(
                        color = theme.primaryColor ?: MaterialTheme.colorScheme.primary,
                    ).then(
                        if (theme == AppTheme.DYNAMIC) {
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.outline,
                                if (isSelected) {
                                    MaterialShapes.Cookie9Sided.toShape()
                                } else {
                                    CircleShape
                                },
                            )
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription =
                        stringResource(
                            Res.string.selected_color,
                            theme.displayName,
                        ),
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        Text(
            text = theme.displayName,
            style = MaterialTheme.typography.labelLarge,
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ToggleSettingCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ExpressiveCard {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        onValueChange = onCheckedChange,
                        role = Role.Switch,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                    ).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )

                VerticalSpacer(4.dp)

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}
