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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import zed.rainxch.core.data.services.shizuku.model.ShizukuStatus
import kotlin.coroutines.resume

class ShizukuServiceManager(
    private val context: Context,
) {
    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val bindMutex = Mutex()
    private var serviceConnection: ServiceConnection? = null
    private var boundUserServiceArgs: Shizuku.UserServiceArgs? = null

    @Volatile
    var installerService: IShizukuInstallerService? = null
        private set

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            Logger.d(TAG) { "Shizuku binder received" }
            refreshStatus()
        }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener {
            Logger.d(TAG) { "Shizuku binder dead" }
            installerService = null
            refreshStatus()
        }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            Logger.d(TAG) {
                "Shizuku permission result: requestCode=$requestCode," +
                    " granted=${grantResult == PackageManager.PERMISSION_GRANTED}"
            }
            refreshStatus()
        }

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

    fun refreshStatus() {
        _status.value = computeStatus()
    }

    private fun computeStatus(): ShizukuStatus =
        try {
            val binderAlive = Shizuku.pingBinder()
            Logger.d(TAG) { "computeStatus() — pingBinder=$binderAlive" }
            if (binderAlive) {
                val permResult = Shizuku.checkSelfPermission()
                Logger.d(TAG) {
                    "computeStatus() — checkSelfPermission=$permResult (GRANTED=${PackageManager.PERMISSION_GRANTED})"
                }
                if (permResult != PackageManager.PERMISSION_GRANTED) {
                    ShizukuStatus.PERMISSION_NEEDED
                } else {
                    Logger.d(TAG) { "computeStatus() — READY" }
                    ShizukuStatus.READY
                }
            } else {
                val installed = isShizukuOrSuiInstalled()
                Logger.d(TAG) { "computeStatus() — binder dead, shizukuOrSuiInstalled=$installed" }
                if (installed) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "Error checking Shizuku status: ${e.javaClass.simpleName}: ${e.message}" }
            ShizukuStatus.NOT_RUNNING
        }

    private fun isShizukuOrSuiInstalled(): Boolean =
        isPackageInstalled(SHIZUKU_PACKAGE) || isPackageInstalled(SUI_PACKAGE)

    private fun isPackageInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to request Shizuku permission: ${e.message}" }
        }
    }

    suspend fun getService(): IShizukuInstallerService? {
        Logger.d(TAG) { "getService() — current status=${_status.value}" }
        if (_status.value != ShizukuStatus.READY) {
            Logger.w(TAG) { "getService() — Shizuku not READY (status=${_status.value}), returning null" }
            return null
        }

        return bindMutex.withLock {
            installerService?.let { service ->
                try {
                    val alive = service.asBinder().pingBinder()
                    Logger.d(TAG) { "getService() — cached service ping=$alive" }
                    if (alive) return@withLock service
                    Logger.w(TAG) { "getService() — cached service binder dead, rebinding..." }
                    installerService = null
                } catch (e: Exception) {
                    Logger.w(TAG) { "getService() — cached service error: ${e.message}, rebinding..." }
                    installerService = null
                }
            } ?: run {
                Logger.d(TAG) { "getService() — no cached service, binding..." }
            }

            bindService()
        }
    }

    private suspend fun bindService(): IShizukuInstallerService? {
        Logger.d(TAG) { "bindService() — attempting to bind Shizuku UserService..." }
        return try {
            withTimeoutOrNull(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val componentName =
                        ComponentName(
                            context.packageName,
                            ShizukuInstallerServiceImpl::class.java.name,
                        )
                    Logger.d(TAG) { "bindService() — component: $componentName" }

                    val args =
                        Shizuku
                            .UserServiceArgs(componentName)
                            .daemon(false)
                            .processNameSuffix("installer")
                            .version(2)

                    val connection =
                        object : ServiceConnection {
                            override fun onServiceConnected(
                                name: ComponentName?,
                                binder: IBinder?,
                            ) {
                                Logger.d(
                                    TAG,
                                ) {
                                    "onServiceConnected() — name=$name, binder=${binder?.javaClass?.name}, binderAlive=${binder?.pingBinder()}"
                                }
                                val service = IShizukuInstallerService.Stub.asInterface(binder)
                                installerService = service
                                Logger.d(TAG) { "Shizuku installer service connected and cached" }
                                if (continuation.isActive) {
                                    continuation.resume(service)
                                }
                            }

                            override fun onServiceDisconnected(name: ComponentName?) {
                                installerService = null
                                Logger.d(TAG) { "Shizuku installer service disconnected: $name" }
                            }
                        }

                    serviceConnection = connection
                    boundUserServiceArgs = args

                    Logger.d(TAG) { "Calling Shizuku.bindUserService()..." }
                    Shizuku.bindUserService(args, connection)
                    Logger.d(TAG) { "Shizuku.bindUserService() called, waiting for callback..." }

                    continuation.invokeOnCancellation {
                        Logger.d(TAG) { "bindService() coroutine cancelled, unbinding..." }
                        try {
                            Shizuku.unbindUserService(args, connection, true)
                        } catch (_: Exception) {
                        }
                    }
                }
            }.also { service ->
                if (service == null) {
                    Logger.w(TAG) { "bindService() timed out after ${BIND_TIMEOUT_MS}ms" }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG) { "Failed to bind Shizuku service: ${e.javaClass.simpleName}: ${e.message}" }
            Logger.e(TAG) { e.stackTraceToString() }
            null
        }
    }

    companion object {
        private const val TAG = "ShizukuServiceManager"
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SUI_PACKAGE = "com.sui"
        private const val BIND_TIMEOUT_MS = 15_000L
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }
}
