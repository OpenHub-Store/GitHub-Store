package zed.rainxch.core.data.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zed.rainxch.core.domain.model.ShizukuAvailability
import zed.rainxch.core.domain.system.InstallerStatusProvider

/**
 * Desktop (JVM) no-op implementation of [InstallerStatusProvider].
 * Shizuku is Android-only, so this always reports UNAVAILABLE.
 */
class DesktopInstallerStatusProvider : InstallerStatusProvider {
    override val shizukuAvailability: StateFlow<ShizukuAvailability> =
        MutableStateFlow(ShizukuAvailability.UNAVAILABLE).asStateFlow()

    override fun requestShizukuPermission() {
        // No-op on desktop
    }
}
