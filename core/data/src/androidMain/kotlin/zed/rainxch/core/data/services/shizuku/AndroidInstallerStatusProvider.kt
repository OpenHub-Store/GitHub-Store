package zed.rainxch.core.data.services.shizuku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import zed.rainxch.core.domain.model.ShizukuAvailability
import zed.rainxch.core.domain.system.InstallerStatusProvider

/**
 * Android implementation of [InstallerStatusProvider].
 * Maps [ShizukuServiceManager.status] to the platform-agnostic [ShizukuAvailability] enum.
 */
class AndroidInstallerStatusProvider(
    private val shizukuServiceManager: ShizukuServiceManager,
    scope: CoroutineScope
) : InstallerStatusProvider {

    override val shizukuAvailability: StateFlow<ShizukuAvailability> =
        shizukuServiceManager.status.map { status ->
            when (status) {
                ShizukuStatus.NOT_INSTALLED -> ShizukuAvailability.UNAVAILABLE
                ShizukuStatus.NOT_RUNNING -> ShizukuAvailability.NOT_RUNNING
                ShizukuStatus.PERMISSION_NEEDED -> ShizukuAvailability.PERMISSION_NEEDED
                ShizukuStatus.READY -> ShizukuAvailability.READY
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ShizukuAvailability.UNAVAILABLE
        )

    override fun requestShizukuPermission() {
        shizukuServiceManager.requestPermission()
    }
}
