package zed.rainxch.home.presentation.locals

import androidx.compose.runtime.compositionLocalOf
import io.github.fletchmckee.liquid.LiquidState

val LocalHomeTopBarLiquid =
    compositionLocalOf<LiquidState> {
        error("State isn't initialized!?")
    }
