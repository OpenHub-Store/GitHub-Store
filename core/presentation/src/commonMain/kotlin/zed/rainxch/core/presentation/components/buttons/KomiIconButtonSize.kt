package zed.rainxch.core.presentation.components.buttons

import androidx.compose.ui.unit.dp

/**
 * Defines the size configurations for icon buttons.
 */
enum class KomiIconButtonSize {
    /** Small size (e.g., 34.dp box). */
    Sm,
    /** Medium size (e.g., 42.dp box). */
    Md,
    /** Large size (e.g., 48.dp box). */
    Lg,
}

/**
 * Returns the layout/border/shadow metrics corresponding to this icon button size.
 */
val KomiIconButtonSize.metrics: KomiIconButtonMetrics
    get() =
        when (this) {
            KomiIconButtonSize.Sm -> KomiIconButtonMetrics(box = 34.dp, icon = 18.dp, shadow = 3.dp, border = 1.5.dp)
            KomiIconButtonSize.Md -> KomiIconButtonMetrics(box = 42.dp, icon = 20.dp, shadow = 4.dp, border = 2.5.dp)
            KomiIconButtonSize.Lg -> KomiIconButtonMetrics(box = 48.dp, icon = 22.dp, shadow = 4.dp, border = 2.5.dp)
        }
