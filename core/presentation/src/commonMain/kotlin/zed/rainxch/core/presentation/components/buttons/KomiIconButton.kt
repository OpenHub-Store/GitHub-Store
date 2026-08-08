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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.inkFocusRing
import zed.rainxch.core.presentation.personality.manga.decoration.inkPress
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing
import kotlin.math.roundToInt

private val MinTouchTarget = 44.dp

@Composable
fun KomiIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KomiButtonVariant = KomiButtonVariant.Tonal,
    size: KomiIconButtonSize = KomiIconButtonSize.Md,
    enabled: Boolean = true,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            MangaIconButton(
                personality = personality,
                icon = icon,
                contentDescription = contentDescription,
                onClick = onClick,
                modifier = modifier,
                variant = variant,
                size = size,
                enabled = enabled,
            )
        }

        is ClassicPersonality -> {
            ClassicIconButton(
                icon = icon,
                contentDescription = contentDescription,
                onClick = onClick,
                modifier = modifier,
                variant = variant,
                size = size,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun MangaIconButton(
    personality: MangaPersonality,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    variant: KomiButtonVariant,
    size: KomiIconButtonSize,
    enabled: Boolean,
) {
    val colors = personality.colors
    val metrics = size.metrics
    val shape =
        remember(personality.shape.cornerSmall) { RoundedCornerShape(personality.shape.cornerSmall) }
    val alpha = if (enabled) 1f else 0.45f
    val flat = variant == KomiButtonVariant.Text
    val ambientInk = LocalContentColor.current
    val container = mangaButtonContainer(variant, colors)
    val contentColor = mangaButtonContent(variant, colors, ambientInk)
    val borderColor = if (container == Color.Transparent) contentColor else colors.outline
    val stamped = !flat && container != Color.Transparent
    val tapTarget = maxOf(metrics.box, MinTouchTarget)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressProgress =
        animateFloatAsState(if (pressed && enabled) 1f else 0f, label = "komiIconButtonPress")
    val hoverProgress =
        animateFloatAsState(if (hovered && enabled) 1f else 0f, label = "komiIconButtonHover")

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

    Box(
        modifier =
            modifier
                .size(tapTarget)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .inkFocusRing(focused = { focused }, color = colors.primary)
                    .then(pressModifier)
                    .size(metrics.box)
                    .background(color = container.copy(alpha = container.alpha * alpha), shape = shape)
                    .then(
                        if (!flat) {
                            Modifier.border(
                                width = metrics.border,
                                color = borderColor.copy(alpha = alpha),
                                shape = shape,
                            )
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(metrics.icon),
                tint = contentColor.copy(alpha = alpha),
            )
        }
    }
}

@Composable
private fun ClassicIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    variant: KomiButtonVariant,
    size: KomiIconButtonSize,
    enabled: Boolean,
) {
    val colors = LocalPersonality.current.colors
    val metrics = size.metrics
    val sizeModifier = Modifier.size(metrics.box)
    val content: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(metrics.icon),
        )
    }

    when (variant) {
        KomiButtonVariant.Primary -> {
            FilledIconButton(
                onClick = onClick,
                modifier = modifier.then(sizeModifier),
                enabled = enabled,
                content = content,
            )
        }

        KomiButtonVariant.Tonal -> {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = modifier.then(sizeModifier),
                enabled = enabled,
                content = content,
            )
        }

        KomiButtonVariant.Outline -> {
            OutlinedIconButton(
                onClick = onClick,
                modifier = modifier.then(sizeModifier),
                enabled = enabled,
                content = content,
            )
        }

        KomiButtonVariant.Text -> {
            IconButton(
                onClick = onClick,
                modifier = modifier.then(sizeModifier),
                enabled = enabled,
                content = content,
            )
        }

        KomiButtonVariant.Destructive -> {
            FilledIconButton(
                onClick = onClick,
                modifier = modifier.then(sizeModifier),
                enabled = enabled,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.error,
                        contentColor = colors.onError,
                    ),
                content = content,
            )
        }
    }
}

@Composable
private fun PreviewIconRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        KomiIconButton(Icons.Default.ArrowBack, "Back", {}, variant = KomiButtonVariant.Tonal)
        KomiIconButton(Icons.Default.Star, "Star", {}, variant = KomiButtonVariant.Primary)
        KomiIconButton(Icons.Default.Share, "Share", {}, variant = KomiButtonVariant.Outline)
        KomiIconButton(Icons.Default.Close, "Clear", {}, variant = KomiButtonVariant.Text)
        KomiIconButton(Icons.Default.Delete, "Delete", {}, variant = KomiButtonVariant.Destructive)
    }
}

@Composable
private fun PreviewSizeRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiIconButton(Icons.Default.Share, "Share", {}, size = KomiIconButtonSize.Sm)
        KomiIconButton(Icons.Default.Share, "Share", {}, size = KomiIconButtonSize.Md)
        KomiIconButton(Icons.Default.Share, "Share", {}, size = KomiIconButtonSize.Lg)
    }
}

@Preview
@Composable
private fun KomiIconButtonMangaPreview() {
    PersonalityPreview(mangaPersonality()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) { PreviewIconRow() }
    }
}

@Preview
@Composable
private fun KomiIconButtonMangaSizesPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewSizeRow() }
}

@Preview
@Composable
private fun KomiIconButtonMangaNightPreview() {
    PersonalityPreview(
        mangaPersonality(
            paper = MangaPaper.NIGHT,
            accent = MangaAccent.SUN,
        ),
    ) { PreviewIconRow() }
}

@Preview
@Composable
private fun KomiIconButtonClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewIconRow() }
}

@Preview
@Composable
private fun KomiIconButtonClassicDarkPreview() {
    PersonalityPreview(classicPersonality(dark = true)) { PreviewIconRow() }
}
