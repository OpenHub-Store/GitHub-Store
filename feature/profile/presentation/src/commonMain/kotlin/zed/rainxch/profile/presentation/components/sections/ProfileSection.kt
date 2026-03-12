package zed.rainxch.profile.presentation.components.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zed.rainxch.profile.presentation.ProfileAction
import zed.rainxch.profile.presentation.ProfileState

fun LazyListScope.profile(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
) {
    accountSection(
        state = state,
        onAction = onAction,
    )

    item {
        Spacer(Modifier.height(20.dp))
    }

    options(
        isUserLoggedIn = state.isUserLoggedIn,
        onAction = onAction,
    )
}
