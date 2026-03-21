package zed.rainxch.core.presentation.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private const val SCROLL_THRESHOLD_PX = 200

@Composable
fun LazyStaggeredGridState.isScrollingUp(): State<Boolean> {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    var accumulatedDelta by remember(this) { mutableIntStateOf(0) }
    var headerVisible by remember(this) { mutableStateOf(true) }

    return remember(this) {
        derivedStateOf {
            if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) {
                accumulatedDelta = 0
                headerVisible = true
            } else {
                val delta =
                    when {
                        firstVisibleItemIndex > previousIndex -> SCROLL_THRESHOLD_PX
                        firstVisibleItemIndex < previousIndex -> -SCROLL_THRESHOLD_PX
                        else -> firstVisibleItemScrollOffset - previousScrollOffset
                    }

                if ((delta > 0 && accumulatedDelta >= 0) || (delta < 0 && accumulatedDelta <= 0)) {
                    accumulatedDelta += delta
                } else {
                    accumulatedDelta = delta
                }

                if (accumulatedDelta > SCROLL_THRESHOLD_PX) {
                    headerVisible = false
                    accumulatedDelta = 0
                } else if (accumulatedDelta < -SCROLL_THRESHOLD_PX) {
                    headerVisible = true
                    accumulatedDelta = 0
                }
            }

            previousIndex = firstVisibleItemIndex
            previousScrollOffset = firstVisibleItemScrollOffset
            headerVisible
        }
    }
}

@Composable
fun LazyListState.isScrollingUp(): State<Boolean> {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    var accumulatedDelta by remember(this) { mutableIntStateOf(0) }
    var headerVisible by remember(this) { mutableStateOf(true) }

    return remember(this) {
        derivedStateOf {
            if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) {
                accumulatedDelta = 0
                headerVisible = true
            } else {
                val delta =
                    when {
                        firstVisibleItemIndex > previousIndex -> SCROLL_THRESHOLD_PX
                        firstVisibleItemIndex < previousIndex -> -SCROLL_THRESHOLD_PX
                        else -> firstVisibleItemScrollOffset - previousScrollOffset
                    }

                if ((delta > 0 && accumulatedDelta >= 0) || (delta < 0 && accumulatedDelta <= 0)) {
                    accumulatedDelta += delta
                } else {
                    accumulatedDelta = delta
                }

                if (accumulatedDelta > SCROLL_THRESHOLD_PX) {
                    headerVisible = false
                    accumulatedDelta = 0
                } else if (accumulatedDelta < -SCROLL_THRESHOLD_PX) {
                    headerVisible = true
                    accumulatedDelta = 0
                }
            }

            previousIndex = firstVisibleItemIndex
            previousScrollOffset = firstVisibleItemScrollOffset
            headerVisible
        }
    }
}
