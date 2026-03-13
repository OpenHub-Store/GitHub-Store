package zed.rainxch.core.data.services.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Status of the Shizuku service connection.
 */
enum class ShizukuStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_NEEDED,
    READY
}

/**
 * Manages the Shizuku lifecycle: availability detection, permission management,
 * and UserService binding for the installer service.
 */
class ShizukuServiceManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "ShizukuServiceManager"
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }

    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private var serviceConnection: ServiceConnection? = null

    @Volatile
    var installerService: IShizukuInstallerService? = null
        private set

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Logger.d(TAG) { "Shizuku binder received" }
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Logger.d(TAG) { "Shizuku binder dead" }
        installerService = null
        refreshStatus()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            Logger.d(TAG) { "Shizuku permission result: requestCode=$requestCode, granted=${grantResult == PackageManager.PERMISSION_GRANTED}" }
            refreshStatus()
        }

    /**
     * Initialize Shizuku listeners. Call once during app startup (from DI).
     */
    fun initialize() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to register Shizuku listeners: ${e.message}" }
        }
        refreshStatus()
    }

    /**
     * Cleanup Shizuku listeners. Call during app teardown.
     */
    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {}
        unbindService()
    }

    /**
     * Refresh the current Shizuku status by checking all prerequisites.
     */
    fun refreshStatus() {
        _status.value = computeStatus()
    }

    private fun computeStatus(): ShizukuStatus {
        if (!isShizukuInstalled()) return ShizukuStatus.NOT_INSTALLED

        return try {
            if (!Shizuku.pingBinder()) return ShizukuStatus.NOT_RUNNING
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return ShizukuStatus.PERMISSION_NEEDED
            }
            ShizukuStatus.READY
        } catch (e: Exception) {
            Logger.w(TAG) { "Error checking Shizuku status: ${e.message}" }
            ShizukuStatus.NOT_RUNNING
        }
    }

    private fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Request Shizuku permission. Shows the Shizuku permission dialog.
     */
    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to request Shizuku permission: ${e.message}" }
        }
    }

    /**
     * Get or bind the Shizuku installer service.
     * Returns null if Shizuku is not ready.
     */
    suspend fun getService(): IShizukuInstallerService? {
        if (_status.value != ShizukuStatus.READY) return null

        // Return cached service if still alive
        installerService?.let { service ->
            try {
                service.asBinder().pingBinder()
                return service
            } catch (_: Exception) {
                installerService = null
            }
        }

        return bindService()
    }

    private suspend fun bindService(): IShizukuInstallerService? {
        return try {
            suspendCancellableCoroutine { continuation ->
                val args = Shizuku.UserServiceArgs(
                    ComponentName(
                        context.packageName,
                        ShizukuInstallerServiceImpl::class.java.name
                    )
                )
                    .daemon(false)
                    .processNameSuffix("installer")
                    .version(1)

                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val service = IShizukuInstallerService.Stub.asInterface(binder)
                        installerService = service
                        serviceConnection = this
                        Logger.d(TAG) { "Shizuku installer service connected" }
                        if (continuation.isActive) {
                            continuation.resume(service)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        installerService = null
                        Logger.d(TAG) { "Shizuku installer service disconnected" }
                    }
                }

                Shizuku.bindUserService(args, connection)

                continuation.invokeOnCancellation {
                    try {
                        Shizuku.unbindUserService(args, connection, true)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to bind Shizuku service: ${e.message}" }
            null
        }
    }

    private fun unbindService() {
        installerService = null
        serviceConnection = null
    }
}
