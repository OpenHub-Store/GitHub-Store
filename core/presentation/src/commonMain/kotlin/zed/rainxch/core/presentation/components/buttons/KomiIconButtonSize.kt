package zed.rainxch.core.presentation.components.buttons

import androidx.compose.ui.unit.dp

enum class KomiIconButtonSize {
    Sm,
    Md,
    Lg,
}

val KomiIconButtonSize.metrics: KomiIconButtonMetrics
    get() =
        when (this) {
            KomiIconButtonSize.Sm -> KomiIconButtonMetrics(box = 34.dp, icon = 18.dp, shadow = 3.dp, border = 1.5.dp)
            KomiIconButtonSize.Md -> KomiIconButtonMetrics(box = 42.dp, icon = 20.dp, shadow = 4.dp, border = 2.5.dp)
            KomiIconButtonSize.Lg -> KomiIconButtonMetrics(box = 48.dp, icon = 22.dp, shadow = 4.dp, border = 2.5.dp)
        }
