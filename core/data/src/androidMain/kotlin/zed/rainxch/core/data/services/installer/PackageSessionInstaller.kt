package zed.rainxch.core.data.services.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object PackageSessionInstaller {
    const val STATUS_SUCCESS = 0
    const val STATUS_FAILURE = -1
    const val STATUS_PENDING_USER_ACTION_REQUIRED = -2
    const val STATUS_ABORTED = -3

    private const val TAG = "PkgSessionInstaller"
    private const val INSTALL_REPLACE_EXISTING = 0x00000002
    private const val INSTALL_TIMEOUT_SECONDS = 120L
    private const val INSTALL_POLL_MS = 400L
    private const val UNINSTALL_TIMEOUT_SECONDS = 30L
    private const val ACTION_INSTALL_RESULT = "zed.rainxch.githubstore.SESSION_INSTALL"
    private const val ACTION_UNINSTALL_RESULT = "zed.rainxch.githubstore.SESSION_UNINSTALL"

    fun appUserId(): Int = android.os.Process.myUid() / 100_000

    fun currentApplicationOrNull(): Context? {
        return try {
            val cls = Class.forName("android.app.ActivityThread")
            cls.getMethod("currentApplication").invoke(null) as? Context
        } catch (e: Exception) {
            try {
                val cls = Class.forName("android.app.AppGlobals")
                cls.getMethod("getInitialApplication").invoke(null) as? Context
            } catch (_: Exception) {
                logE("Failed to obtain Application context", e)
                null
            }
        }
    }

    fun readApkIdentity(context: Context, filePath: String): Pair<String?, Long> {
        val info = try {
            context.packageManager.getPackageArchiveInfo(filePath, 0)
        } catch (e: Exception) {
            logW("getPackageArchiveInfo($filePath) failed: ${e.message}")
            null
        } ?: return null to -1L
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return info.packageName to versionCode
    }

    fun installFromPfd(
        context: Context,
        pfd: ParcelFileDescriptor,
        fileSize: Long,
        packageName: String?,
        expectedVersionCode: Long,
        installerPackageName: String?,
        callerPackageName: String?,
        userId: Int,
        originatingUid: Int,
        privileged: Boolean,
        installReason: Int = -1,
    ): Int {
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            return install(
                context = context,
                input = input,
                fileSize = fileSize,
                packageName = packageName,
                expectedVersionCode = expectedVersionCode,
                installerPackageName = installerPackageName,
                callerPackageName = callerPackageName,
                userId = userId,
                originatingUid = originatingUid,
                privileged = privileged,
                installReason = installReason,
            )
        }
    }

    fun install(
        context: Context,
        input: InputStream,
        fileSize: Long,
        packageName: String?,
        expectedVersionCode: Long,
        installerPackageName: String?,
        callerPackageName: String?,
        userId: Int,
        originatingUid: Int,
        privileged: Boolean,
        installReason: Int = -1,
    ): Int {
        val resolvedUserId = userId.coerceAtLeast(0)
        val caller = callerPackageName?.takeIf { it.isNotBlank() } ?: context.packageName
        log(
            "install() privileged=$privileged userId=$resolvedUserId pkg=$packageName " +
                "caller=$caller installer=$installerPackageName originatingUid=$originatingUid " +
                "installReason=$installReason",
        )

        val installer = resolvePackageInstaller(context, privileged, caller, resolvedUserId)
            ?: run {
                logE("resolvePackageInstaller() returned null")
                return STATUS_FAILURE
            }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        packageName?.takeIf { it.isNotBlank() }?.let { params.setAppPackageName(it) }
        if (fileSize > 0) params.setSize(fileSize)
        if (originatingUid >= 0) {
            try {
                params.setOriginatingUid(originatingUid)
            } catch (e: Exception) {
                logW("setOriginatingUid($originatingUid) failed: ${e.message}")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        if (installReason >= 0) {
            try {
                params.setInstallReason(installReason)
            } catch (e: Exception) {
                logW("setInstallReason($installReason) failed: ${e.message}")
            }
        }
        installerPackageName?.takeIf { it.isNotBlank() }?.let { name ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    params.setInstallerPackageName(name)
                } catch (e: Exception) {
                    logW("setInstallerPackageName($name) failed: ${e.message}")
                }
            }
        }
        addReplaceExistingFlag(params)

        var sessionId = -1
        var session: PackageInstaller.Session? = null
        var receiver: BroadcastReceiver? = null
        var sessionCallback: PackageInstaller.SessionCallback? = null
        var callbackThread: HandlerThread? = null
        var committed = false
        val results = LinkedBlockingQueue<Intent>()
        return try {
            sessionId = installer.createSession(params)
            log("createSession() sessionId=$sessionId")
            session = installer.openSession(sessionId)
            val writeLength = if (fileSize > 0) fileSize else -1L
            session.openWrite("base.apk", 0, writeLength).use { out ->
                val copied = input.copyTo(out)
                out.flush()
                session.fsync(out)
                log("APK written to session: $copied bytes (expected: $fileSize)")
            }

            val token = UUID.randomUUID().toString()
            val action = "$ACTION_INSTALL_RESULT.$token"
            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    results.offer(intent)
                }
            }
            registerInternalReceiver(context, receiver, action)
            val finished = registerFinishedCallback(installer, sessionId, results)
            sessionCallback = finished?.first
            callbackThread = finished?.second
            session.commit(broadcastSender(context, action, token.hashCode()))
            committed = true

            awaitInstallStatus(
                context = context,
                results = results,
                launchConfirmOnPending = !privileged,
                packageName = packageName,
            )
        } catch (e: Exception) {
            logE("install() exception", e)
            STATUS_FAILURE
        } finally {
            if (!committed && sessionId >= 0) {
                try {
                    installer.abandonSession(sessionId)
                } catch (_: Exception) {
                }
            }
            try {
                session?.close()
            } catch (_: Exception) {
            }
            if (sessionCallback != null) {
                try {
                    installer.unregisterSessionCallback(sessionCallback)
                } catch (_: Exception) {
                }
            }
            callbackThread?.quitSafely()
            if (receiver != null) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun uninstall(
        context: Context,
        packageName: String,
        userId: Int,
        privileged: Boolean,
        callerPackageName: String?,
    ): Int {
        val resolvedUserId = userId.coerceAtLeast(0)
        val caller = callerPackageName?.takeIf { it.isNotBlank() } ?: context.packageName
        log("uninstall() pkg=$packageName userId=$resolvedUserId privileged=$privileged")

        val installer = resolvePackageInstaller(context, privileged, caller, resolvedUserId)
            ?: run {
                logE("resolvePackageInstaller() returned null")
                return STATUS_FAILURE
            }

        val results = LinkedBlockingQueue<Intent>()
        var receiver: BroadcastReceiver? = null
        return try {
            val token = UUID.randomUUID().toString()
            val action = "$ACTION_UNINSTALL_RESULT.$token"
            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    results.offer(intent)
                }
            }
            registerInternalReceiver(context, receiver, action)
            installer.uninstall(packageName, broadcastSender(context, action, token.hashCode()))

            val intent = results.poll(UNINSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (intent == null) {
                logE("uninstall timed out after ${UNINSTALL_TIMEOUT_SECONDS}s")
                return if (!isPackageInstalled(context, packageName)) STATUS_SUCCESS else STATUS_FAILURE
            }
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )
            log("uninstall result status=$status message=${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
            if (status == PackageInstaller.STATUS_SUCCESS) STATUS_SUCCESS else STATUS_FAILURE
        } catch (e: Exception) {
            logE("uninstall() exception", e)
            STATUS_FAILURE
        } finally {
            if (receiver != null) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun awaitInstallStatus(
        context: Context,
        results: LinkedBlockingQueue<Intent>,
        launchConfirmOnPending: Boolean,
        packageName: String?,
    ): Int {
        val baselineVersion = installedVersionOrNull(context, packageName)
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(INSTALL_TIMEOUT_SECONDS)
        while (true) {
            if (installVisibleOnDevice(context, packageName, baselineVersion)) {
                log("package state changed on device, treating install as success")
                return STATUS_SUCCESS
            }
            val remainingNs = deadlineNs - System.nanoTime()
            if (remainingNs <= 0L) {
                logE("install timed out after ${INSTALL_TIMEOUT_SECONDS}s")
                if (installVisibleOnDevice(context, packageName, baselineVersion)) {
                    return STATUS_SUCCESS
                }
                return STATUS_FAILURE
            }
            val waitNs = minOf(remainingNs, TimeUnit.MILLISECONDS.toNanos(INSTALL_POLL_MS))
            val intent = results.poll(waitNs, TimeUnit.NANOSECONDS) ?: continue
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            log("install result status=$status message='$message'")
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> return STATUS_SUCCESS
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    if (!launchConfirmOnPending) {
                        return STATUS_PENDING_USER_ACTION_REQUIRED
                    }
                    val confirm = extraIntent(intent) ?: run {
                        logW("PENDING_USER_ACTION without EXTRA_INTENT")
                        return STATUS_PENDING_USER_ACTION_REQUIRED
                    }
                    try {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirm)
                        log("launched system confirm activity")
                    } catch (e: Exception) {
                        logE("failed to launch confirm activity", e)
                        return STATUS_PENDING_USER_ACTION_REQUIRED
                    }
                }
                PackageInstaller.STATUS_FAILURE_ABORTED -> return STATUS_ABORTED
                else -> return STATUS_FAILURE
            }
        }
    }

    private fun registerFinishedCallback(
        installer: PackageInstaller,
        sessionId: Int,
        results: LinkedBlockingQueue<Intent>,
    ): Pair<PackageInstaller.SessionCallback, HandlerThread>? {
        val thread = HandlerThread("PkgSessionCb").apply { start() }
        val callback = object : PackageInstaller.SessionCallback() {
            override fun onCreated(id: Int) = Unit
            override fun onBadgingChanged(id: Int) = Unit
            override fun onActiveChanged(id: Int, active: Boolean) = Unit
            override fun onProgressChanged(id: Int, progress: Float) = Unit
            override fun onFinished(id: Int, success: Boolean) {
                if (id != sessionId || !success) return
                log("SessionCallback.onFinished sessionId=$id success=$success")
                results.offer(
                    Intent().putExtra(
                        PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_SUCCESS,
                    ),
                )
            }
        }
        return try {
            installer.registerSessionCallback(callback, Handler(thread.looper))
            callback to thread
        } catch (e: Exception) {
            logW("registerSessionCallback failed: ${e.message}")
            thread.quitSafely()
            null
        }
    }

    private fun resolvePackageInstaller(
        context: Context,
        privileged: Boolean,
        callerPackageName: String,
        userId: Int,
    ): PackageInstaller? {
        if (!privileged) {
            return context.packageManager.packageInstaller
        }
        val iPackageInstaller = resolveIPackageInstaller(context) ?: return null
        return createPrivilegedPackageInstaller(iPackageInstaller, callerPackageName, userId)
    }

    private fun resolveIPackageInstaller(context: Context): Any? {
        try {
            val publicInstaller = context.packageManager.packageInstaller
            val field = PackageInstaller::class.java.getDeclaredField("mInstaller")
            field.isAccessible = true
            return field.get(publicInstaller)
        } catch (e: Exception) {
            logW("mInstaller field lookup failed: ${e.message}")
        }
        return try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val binder = serviceManager
                .getMethod("getService", String::class.java)
                .invoke(null, "package") as IBinder
            val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
            val ipm = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ipm.javaClass.getMethod("getPackageInstaller").invoke(ipm)
        } catch (e: Exception) {
            logW("ServiceManager IPackageInstaller lookup failed: ${e.message}")
            null
        }
    }

    private fun createPrivilegedPackageInstaller(
        iPackageInstaller: Any,
        callerPackageName: String,
        userId: Int,
    ): PackageInstaller? {
        val ipiClass = try {
            Class.forName("android.content.pm.IPackageInstaller")
        } catch (e: Exception) {
            logW("IPackageInstaller class not found: ${e.message}")
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val ctor = PackageInstaller::class.java.getDeclaredConstructor(
                    ipiClass,
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                )
                ctor.isAccessible = true
                return ctor.newInstance(iPackageInstaller, callerPackageName, null, userId) as PackageInstaller
            } catch (e: Exception) {
                logW("PackageInstaller(S+) constructor failed: ${e.message}")
            }
        }
        return try {
            val ctor = PackageInstaller::class.java.getDeclaredConstructor(
                ipiClass,
                String::class.java,
                Int::class.javaPrimitiveType,
            )
            ctor.isAccessible = true
            ctor.newInstance(iPackageInstaller, callerPackageName, userId) as PackageInstaller
        } catch (e: Exception) {
            logE("PackageInstaller constructor failed", e)
            null
        }
    }

    private fun addReplaceExistingFlag(params: PackageInstaller.SessionParams) {
        for (name in arrayOf("installFlags", "mInstallFlags")) {
            try {
                val field = params.javaClass.getDeclaredField(name)
                field.isAccessible = true
                field.setInt(params, field.getInt(params) or INSTALL_REPLACE_EXISTING)
                return
            } catch (_: Exception) {
            }
        }
        logW("could not set INSTALL_REPLACE_EXISTING")
    }

    private fun broadcastSender(context: Context, action: String, requestCode: Int): android.content.IntentSender {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(action).setPackage(context.packageName),
            flags,
        ).intentSender
    }

    private fun extraIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private fun installVisibleOnDevice(
        ctx: Context,
        packageName: String?,
        baselineVersion: Long?,
    ): Boolean {
        val installed = installedVersionOrNull(ctx, packageName) ?: return false
        return baselineVersion == null || installed > baselineVersion
    }

    private fun installedVersionOrNull(ctx: Context, packageName: String?): Long? {
        val target = packageName?.takeIf { it.isNotBlank() } ?: return null
        val info = packageInfoOrNull(ctx, target) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun isPackageInstalled(ctx: Context, packageName: String): Boolean =
        packageInfoOrNull(ctx, packageName) != null

    private fun packageInfoOrNull(ctx: Context, packageName: String): android.content.pm.PackageInfo? = try {
        ctx.packageManager.getPackageInfo(packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        logE("getPackageInfo($packageName) failed", e)
        null
    }

    private fun registerInternalReceiver(ctx: Context, receiver: BroadcastReceiver, action: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(receiver, IntentFilter(action))
        }
    }

    private fun log(msg: String) = android.util.Log.d(TAG, msg)
    private fun logW(msg: String) = android.util.Log.w(TAG, msg)
    private fun logE(msg: String, e: Throwable? = null) = android.util.Log.e(TAG, msg, e)
}
