package zed.rainxch.details.presentation.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import zed.rainxch.core.domain.utils.VersionMath

class SmartInstallButtonLogicTest {
    private fun isUpdateForSelectedRelease(
        updateAvailable: Boolean,
        pendingInstall: Boolean,
        latestVersion: String?,
        selectedTag: String?,
    ): Boolean =
        updateAvailable &&
            !pendingInstall &&
            selectedTag != null &&
            VersionMath.isExactSameVersion(latestVersion, selectedTag)

    @Test
    fun nightly_update_is_not_shown_on_stable_selected_release() {
        assertFalse(
            isUpdateForSelectedRelease(
                updateAvailable = true,
                pendingInstall = false,
                latestVersion = "nightly",
                selectedTag = "v2.0.0",
            ),
        )
    }

    @Test
    fun nightly_update_is_shown_on_nightly_selected_release() {
        assertTrue(
            isUpdateForSelectedRelease(
                updateAvailable = true,
                pendingInstall = false,
                latestVersion = "nightly",
                selectedTag = "nightly",
            ),
        )
    }
}
