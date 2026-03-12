package zed.rainxch.core.presentation.locals

import androidx.compose.runtime.compositionLocalOf
import io.github.fletchmckee.liquid.LiquidState

val LocalBottomNavigationLiquid =
    compositionLocalOf<LiquidState> {
        error("State not declared")
    }
