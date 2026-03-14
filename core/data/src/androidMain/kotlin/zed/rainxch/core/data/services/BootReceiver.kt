package zed.rainxch.core.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger

/**
 * Reschedules periodic update checks after device reboot.
 * Registered statically in AndroidManifest.xml.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Logger.i { "BootReceiver: Device booted, scheduling update checks" }
            UpdateScheduler.schedule(context)
        }
    }
}
