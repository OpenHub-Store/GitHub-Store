package zed.rainxch.core.data.services

import android.content.Context
import zed.rainxch.core.domain.system.UpdateScheduleManager

class AndroidUpdateScheduleManager(
    private val context: Context,
) : UpdateScheduleManager {
    override fun reschedule(intervalHours: Long) {
        UpdateScheduler.reschedule(context, intervalHours)
    }
}
