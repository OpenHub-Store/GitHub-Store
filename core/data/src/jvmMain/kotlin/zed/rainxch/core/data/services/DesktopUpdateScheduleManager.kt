package zed.rainxch.core.data.services

import zed.rainxch.core.domain.system.UpdateScheduleManager

/**
 * No-op implementation for Desktop — WorkManager is Android-only.
 */
class DesktopUpdateScheduleManager : UpdateScheduleManager {
    override fun reschedule(intervalHours: Long) {
        // No background scheduler on Desktop
    }
}
