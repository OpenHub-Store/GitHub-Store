package zed.rainxch.core.domain.system

/**
 * Abstraction for rescheduling background update checks.
 * Android implementation delegates to WorkManager; Desktop is a no-op.
 */
interface UpdateScheduleManager {
    /**
     * Reschedules the periodic update check with a new interval.
     * Takes effect immediately (replaces existing schedule).
     */
    fun reschedule(intervalHours: Long)
}
