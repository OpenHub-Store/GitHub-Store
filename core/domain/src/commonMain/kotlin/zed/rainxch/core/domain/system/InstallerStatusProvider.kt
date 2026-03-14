package zed.rainxch.core.domain.system

import kotlinx.coroutines.flow.StateFlow
import zed.rainxch.core.domain.model.ShizukuAvailability

interface InstallerStatusProvider {
    val shizukuAvailability: StateFlow<ShizukuAvailability>
    fun requestShizukuPermission()
}
