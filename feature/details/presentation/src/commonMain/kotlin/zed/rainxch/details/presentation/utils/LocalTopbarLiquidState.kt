package zed.rainxch.details.presentation.utils

import androidx.compose.runtime.compositionLocalOf
import io.github.fletchmckee.liquid.LiquidState

internal val LocalTopbarLiquidState =
    compositionLocalOf<LiquidState> {
        error("State not declared")
    }
