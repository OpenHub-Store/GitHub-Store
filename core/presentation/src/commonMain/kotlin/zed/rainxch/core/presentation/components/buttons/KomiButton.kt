package zed.rainxch.core.presentation.components.buttons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.inkFocusRing
import zed.rainxch.core.presentation.personality.manga.decoration.inkPress
import zed.rainxch.core.presentation.personality.manga.decoration.speedLines
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing
import kotlin.math.roundToInt

@Composable
fun KomiButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    variant: KomiButtonVariant = KomiButtonVariant.Primary,
    size: KomiButtonSize = KomiButtonSize.Md,
    enabled: Boolean = true,
    loading: Boolean = false,
    emphasized: Boolean = false,
    fullWidth: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            MangaButton(
                personality = personality,
                onClick = onClick,
                label = label,
                modifier = modifier,
                variant = variant,
                size = size,
                enabled = enabled,
                loading = loading,
                emphasized = emphasized,
                fullWidth = fullWidth,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
            )
        }

        is ClassicPersonality -> {
            ClassicButton(
                personality = personality,
                onClick = onClick,
                label = label,
                modifier = modifier,
                variant = variant,
                size = size,
                enabled = enabled,
                loading = loading,
                fullWidth = fullWidth,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
            )
        }
    }
}

@Composable
private fun MangaButton(
    personality: MangaPersonality,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier,
    variant: KomiButtonVariant,
    size: KomiButtonSize,
    enabled: Boolean,
    loading: Boolean,
    emphasized: Boolean,
    fullWidth: Boolean,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
) {
    val colors = personality.colors
    val metrics = buttonMetrics(size)
    val shape =
        remember(personality.shape.cornerSmall) { RoundedCornerShape(personality.shape.cornerSmall) }
    val active = enabled && !loading
    val alpha = if (active) 1f else 0.45f

    val flat = variant == KomiButtonVariant.Text
    val ambientInk = LocalContentColor.current
    val container = mangaButtonContainer(variant, colors)
    val contentColor = mangaButtonContent(variant, colors, ambientInk)
    val borderColor = if (container == Color.Transparent) contentColor else colors.outline
    val stamped = !flat && container != Color.Transparent
    val sweep =
        emphasized && active &&
            (variant == KomiButtonVariant.Primary || variant == KomiButtonVariant.Destructive)
    val clipNeeded = personality.shape.cornerSmall > 0.dp || sweep

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressProgress =
        animateFloatAsState(if (pressed && active) 1f else 0f, label = "komiButtonPress")
    val hoverProgress =
        animateFloatAsState(if (hovered && active) 1f else 0f, label = "komiButtonHover")

    val pressModifier =
        if (!stamped) {
            Modifier.offset { IntOffset(0, (1.dp.toPx() * pressProgress.value).roundToInt()) }
        } else {
            Modifier.inkPress(
                pressProgress = { pressProgress.value },
                hoverProgress = { hoverProgress.value },
                shadow = DpOffset(metrics.shadow, metrics.shadow),
                shadowColor = colors.shadow.copy(alpha = alpha),
                shape = shape,
            )
        }

    Row(
        modifier =
            modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .inkFocusRing(focused = { focused }, color = colors.primary)
                .then(pressModifier)
                .then(if (clipNeeded) Modifier.clip(shape) else Modifier)
                .background(color = container.copy(alpha = container.alpha * alpha), shape = shape)
                .then(
                    if (sweep) {
                        Modifier.speedLines(
                            color = contentColor,
                            opacity = 0.16f,
                        )
                    } else {
                        Modifier
                    },
                ).then(
                    if (!flat) {
                        Modifier.border(
                            width = metrics.border,
                            color = borderColor.copy(alpha = alpha),
                            shape = shape,
                        )
                    } else {
                        Modifier
                    },
                ).clickable(
                    enabled = active,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ).height(metrics.height)
                .padding(horizontal = metrics.hPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiButtonContent(
            label = label,
            contentColor = contentColor.copy(alpha = alpha),
            iconSize = metrics.icon,
            gap = metrics.gap,
            fontSize = metrics.font,
            loading = loading,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }
}

@Composable
private fun ClassicButton(
    personality: ClassicPersonality,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier,
    variant: KomiButtonVariant,
    size: KomiButtonSize,
    enabled: Boolean,
    loading: Boolean,
    fullWidth: Boolean,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
) {
    val colors = personality.colors
    val metrics = buttonMetrics(size)
    val active = enabled && !loading
    val contentPadding = PaddingValues(horizontal = metrics.hPadding)
    val buttonModifier =
        modifier
            .heightIn(min = metrics.height)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)

    val content: @Composable RowScope.() -> Unit = {
        KomiButtonContent(
            label = label,
            contentColor = LocalContentColor.current,
            iconSize = metrics.icon,
            gap = metrics.gap,
            fontSize = TextUnit.Unspecified,
            loading = loading,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }

    when (variant) {
        KomiButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = active,
                contentPadding = contentPadding,
                content = content,
            )
        }

        KomiButtonVariant.Tonal -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = active,
                contentPadding = contentPadding,
                content = content,
            )
        }

        KomiButtonVariant.Outline -> {
            OutlinedButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = active,
                contentPadding = contentPadding,
                content = content,
            )
        }

        KomiButtonVariant.Text -> {
            TextButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = active,
                contentPadding = contentPadding,
                content = content,
            )
        }

        KomiButtonVariant.Destructive -> {
            Button(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = active,
                contentPadding = contentPadding,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = colors.error,
                        contentColor = colors.onError,
                    ),
                content = content,
            )
        }
    }
}

@Composable
private fun RowScope.KomiButtonContent(
    label: String,
    contentColor: Color,
    iconSize: Dp,
    gap: Dp,
    fontSize: TextUnit,
    loading: Boolean,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                strokeWidth = 2.dp,
                color = contentColor,
            )
            Spacer(modifier = Modifier.width(gap))
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
            Spacer(modifier = Modifier.width(gap))
        }

        KomiText(
            text = label,
            role = KomiTextRole.Label,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = fontSize,
        )

        if (trailingIcon != null && !loading) {
            Spacer(modifier = Modifier.width(gap))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

private data class ButtonMetrics(
    val height: Dp,
    val hPadding: Dp,
    val font: TextUnit,
    val icon: Dp,
    val gap: Dp,
    val shadow: Dp,
    val border: Dp,
)

private fun buttonMetrics(size: KomiButtonSize): ButtonMetrics =
    when (size) {
        KomiButtonSize.Sm -> ButtonMetrics(34.dp, 14.dp, 12.sp, 15.dp, 7.dp, 3.dp, 1.5.dp)
        KomiButtonSize.Md -> ButtonMetrics(44.dp, 20.dp, 13.5.sp, 18.dp, 9.dp, 4.dp, 2.5.dp)
        KomiButtonSize.Lg -> ButtonMetrics(54.dp, 26.dp, 16.sp, 22.dp, 10.dp, 5.dp, 3.dp)
    }

@Composable
private fun PreviewButtonStack() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        KomiButton(
            onClick = {},
            label = "Install",
            variant = KomiButtonVariant.Primary,
            leadingIcon = Icons.Default.Add,
        )
        KomiButton(
            onClick = {},
            label = "Star",
            variant = KomiButtonVariant.Tonal,
            leadingIcon = Icons.Default.Check,
        )
        KomiButton(onClick = {}, label = "Share", variant = KomiButtonVariant.Outline)
        KomiButton(onClick = {}, label = "Details", variant = KomiButtonVariant.Text)
        KomiButton(onClick = {}, label = "Uninstall", variant = KomiButtonVariant.Destructive)
        KomiButton(
            onClick = {},
            label = "Install Now · 78 MB",
            variant = KomiButtonVariant.Primary,
            emphasized = true,
            fullWidth = true,
            leadingIcon = Icons.Default.Add,
        )
    }
}

@Preview
@Composable
private fun KomiButtonMangaPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewButtonStack() }
}

@Preview
@Composable
private fun KomiButtonMangaNightPreview() {
    PersonalityPreview(
        mangaPersonality(
            paper = MangaPaper.NIGHT,
            accent = MangaAccent.SUN,
        ),
    ) { PreviewButtonStack() }
}

@Preview
@Composable
private fun KomiButtonMangaStatesPreview() {
    PersonalityPreview(mangaPersonality()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            KomiButton(onClick = {}, label = "Loading", loading = true)
            KomiButton(onClick = {}, label = "Disabled", enabled = false)
            KomiButton(onClick = {}, label = "Small", size = KomiButtonSize.Sm)
            KomiButton(onClick = {}, label = "Large", size = KomiButtonSize.Lg)
        }
    }
}

@Preview
@Composable
private fun KomiButtonClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewButtonStack() }
}

@Preview
@Composable
private fun KomiButtonClassicDarkPreview() {
    PersonalityPreview(classicPersonality(dark = true)) { PreviewButtonStack() }
}
