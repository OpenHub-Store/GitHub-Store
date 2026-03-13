package zed.rainxch.core.data.services.shizuku

import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import rikka.shizuku.SystemServiceHelper
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shizuku UserService implementation that runs in a privileged process (shell/root).
 * Provides silent package install/uninstall via the system PackageInstaller API.
 *
 * This class runs in Shizuku's process, NOT in the app's process.
 * It has shell-level (UID 2000) or root-level (UID 0) privileges.
 *
 * Uses reflection to access hidden Android framework APIs, since their method
 * signatures vary across Android versions and hidden-api-stub versions.
 *
 * MUST have a default no-arg constructor for Shizuku's UserService framework.
 */
class ShizukuInstallerServiceImpl() : IShizukuInstallerService.Stub() {

    companion object {
        private const val INSTALL_TIMEOUT_SECONDS = 120L
        private const val UNINSTALL_TIMEOUT_SECONDS = 60L

        private const val STATUS_SUCCESS = 0
        private const val STATUS_FAILURE = -1
        private const val STATUS_FAILURE_ABORTED = -2
        private const val STATUS_FAILURE_BLOCKED = -3
        private const val STATUS_FAILURE_CONFLICT = -4
        private const val STATUS_FAILURE_INCOMPATIBLE = -5
        private const val STATUS_FAILURE_INVALID = -6
        private const val STATUS_FAILURE_STORAGE = -7
        private const val STATUS_FAILURE_TIMEOUT = -8
    }

    /**
     * Obtains the system IPackageInstaller binder via IPackageManager (reflection).
     */
    private fun getPackageInstallerBinder(): Any {
        val binder: IBinder = SystemServiceHelper.getSystemService("package")

        // IPackageManager.Stub.asInterface(binder)
        val ipmClass = Class.forName("android.content.pm.IPackageManager\$Stub")
        val asInterface = ipmClass.getMethod("asInterface", IBinder::class.java)
        val pm = asInterface.invoke(null, binder)

        // pm.getPackageInstaller()
        val getInstaller = pm.javaClass.getMethod("getPackageInstaller")
        return getInstaller.invoke(pm)!!
    }

    override fun installPackage(apkPath: String): Int {
        val file = File(apkPath)
        if (!file.exists()) return STATUS_FAILURE_INVALID

        return try {
            val installer = getPackageInstallerBinder()
            val installerClass = installer.javaClass

            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            params.setSize(file.length())

            // createSession — try various signatures across Android versions
            val sessionId = createSession(installer, installerClass, params)

            // openSession returns an IBinder for the session
            val openSessionMethod = installerClass.getMethod("openSession", Int::class.javaPrimitiveType)
            val sessionBinder = openSessionMethod.invoke(installer, sessionId)

            // IPackageInstallerSession.Stub.asInterface(binder)
            val sessionStubClass = Class.forName("android.content.pm.IPackageInstallerSession\$Stub")
            val sessionAsInterface = sessionStubClass.getMethod("asInterface", IBinder::class.java)
            val session = sessionAsInterface.invoke(null, sessionBinder as IBinder)
            val sessionClass = session.javaClass

            // Write APK to session
            val openWrite = sessionClass.getMethod(
                "openWrite",
                String::class.java,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            )
            val pfd = openWrite.invoke(session, "base.apk", 0L, file.length()) as ParcelFileDescriptor
            val output = ParcelFileDescriptor.AutoCloseOutputStream(pfd)

            FileInputStream(file).use { input ->
                output.use { out ->
                    input.copyTo(out, bufferSize = 65536)
                    out.flush()
                }
            }

            // Set up LocalSocket for synchronous result callback
            val resultCode = AtomicInteger(STATUS_FAILURE_TIMEOUT)
            val latch = CountDownLatch(1)
            val socketName = "shizuku_install_${SystemClock.elapsedRealtimeNanos()}"
            val serverSocket = LocalServerSocket(socketName)

            val listenerThread = Thread {
                try {
                    val client = serverSocket.accept()
                    val dis = DataInputStream(client.inputStream)
                    val status = dis.readInt()
                    resultCode.set(mapInstallStatus(status))
                    dis.close()
                    client.close()
                } catch (_: Exception) {
                    resultCode.set(STATUS_FAILURE)
                } finally {
                    try { serverSocket.close() } catch (_: Exception) {}
                    latch.countDown()
                }
            }
            listenerThread.isDaemon = true
            listenerThread.start()

            val statusReceiver = createStatusReceiver(socketName)

            // session.commit(intentSender, false)
            val commitMethod = sessionClass.getMethod(
                "commit",
                IntentSender::class.java,
                Boolean::class.javaPrimitiveType
            )
            commitMethod.invoke(session, statusReceiver, false)

            if (!latch.await(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                resultCode.set(STATUS_FAILURE_TIMEOUT)
                try { serverSocket.close() } catch (_: Exception) {}
            }

            resultCode.get()
        } catch (e: Exception) {
            STATUS_FAILURE
        }
    }

    override fun uninstallPackage(packageName: String): Int {
        return try {
            val installer = getPackageInstallerBinder()
            val installerClass = installer.javaClass

            val resultCode = AtomicInteger(STATUS_FAILURE_TIMEOUT)
            val latch = CountDownLatch(1)
            val socketName = "shizuku_uninstall_${SystemClock.elapsedRealtimeNanos()}"
            val serverSocket = LocalServerSocket(socketName)

            val listenerThread = Thread {
                try {
                    val client = serverSocket.accept()
                    val dis = DataInputStream(client.inputStream)
                    val status = dis.readInt()
                    resultCode.set(mapInstallStatus(status))
                    dis.close()
                    client.close()
                } catch (_: Exception) {
                    resultCode.set(STATUS_FAILURE)
                } finally {
                    try { serverSocket.close() } catch (_: Exception) {}
                    latch.countDown()
                }
            }
            listenerThread.isDaemon = true
            listenerThread.start()

            val statusReceiver = createStatusReceiver(socketName)

            // Try uninstall via reflection — signature varies by Android version
            performUninstall(installer, installerClass, packageName, statusReceiver)

            if (!latch.await(UNINSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                resultCode.set(STATUS_FAILURE_TIMEOUT)
                try { serverSocket.close() } catch (_: Exception) {}
            }

            resultCode.get()
        } catch (e: Exception) {
            STATUS_FAILURE
        }
    }

    /**
     * Calls IPackageInstaller.createSession with the correct signature for the
     * current Android version. Tries multiple overloads.
     */
    private fun createSession(
        installer: Any,
        installerClass: Class<*>,
        params: PackageInstaller.SessionParams
    ): Int {
        val callerPackage = "com.android.shell"
        val uid = android.os.Process.myUid()

        // API 33+: createSession(SessionParams, String, String, int)
        try {
            val method = installerClass.getMethod(
                "createSession",
                PackageInstaller.SessionParams::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            return method.invoke(installer, params, callerPackage, null, uid) as Int
        } catch (_: NoSuchMethodException) {}

        // API 26-32: createSession(SessionParams, String, String)
        try {
            val method = installerClass.getMethod(
                "createSession",
                PackageInstaller.SessionParams::class.java,
                String::class.java,
                String::class.java
            )
            return method.invoke(installer, params, callerPackage, null) as Int
        } catch (_: NoSuchMethodException) {}

        throw IllegalStateException("Could not find createSession method")
    }

    /**
     * Calls IPackageInstaller.uninstall with the correct signature for the
     * current Android version. Tries multiple overloads.
     */
    private fun performUninstall(
        installer: Any,
        installerClass: Class<*>,
        packageName: String,
        statusReceiver: IntentSender
    ) {
        val versionedPackageClass = Class.forName("android.content.pm.VersionedPackage")
        val versionedPackage = versionedPackageClass
            .getConstructor(String::class.java, Int::class.javaPrimitiveType)
            .newInstance(packageName, -1) // VERSION_CODE_HIGHEST = -1

        val callerPackage = "com.android.shell"

        // API 33+: uninstall(VersionedPackage, String, int, IntentSender, int)
        try {
            val method = installerClass.getMethod(
                "uninstall",
                versionedPackageClass,
                String::class.java,
                Int::class.javaPrimitiveType,
                IntentSender::class.java,
                Int::class.javaPrimitiveType
            )
            method.invoke(installer, versionedPackage, callerPackage, 0, statusReceiver, 0)
            return
        } catch (_: NoSuchMethodException) {}

        // API 26-32: uninstall(VersionedPackage, String, int, IntentSender)
        try {
            val method = installerClass.getMethod(
                "uninstall",
                versionedPackageClass,
                String::class.java,
                Int::class.javaPrimitiveType,
                IntentSender::class.java
            )
            method.invoke(installer, versionedPackage, callerPackage, 0, statusReceiver)
            return
        } catch (_: NoSuchMethodException) {}

        throw IllegalStateException("Could not find uninstall method")
    }

    /**
     * Creates an IntentSender that reports install/uninstall status back
     * through a LocalSocket. Uses reflection to construct IntentSender
     * from an IIntentSender proxy, since these are hidden APIs.
     */
    private fun createStatusReceiver(socketName: String): IntentSender {
        val iIntentSenderClass = Class.forName("android.content.IIntentSender")

        // Create a dynamic proxy for IIntentSender that writes result to LocalSocket
        val proxy = Proxy.newProxyInstance(
            iIntentSenderClass.classLoader,
            arrayOf(iIntentSenderClass)
        ) { _, method, args ->
            when (method.name) {
                "send" -> {
                    // Extract the Intent argument (second param in all known signatures)
                    val intent = args?.filterIsInstance<Intent>()?.firstOrNull()
                    val status = intent?.getIntExtra(
                        PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE
                    ) ?: PackageInstaller.STATUS_FAILURE

                    try {
                        val socket = LocalSocket()
                        socket.connect(
                            LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT)
                        )
                        val dos = DataOutputStream(socket.outputStream)
                        dos.writeInt(status)
                        dos.flush()
                        dos.close()
                        socket.close()
                    } catch (_: Exception) {}

                    // Return 0 if method returns int, null otherwise
                    if (method.returnType == Int::class.javaPrimitiveType) 0 else null
                }
                "asBinder" -> {
                    // Return the proxy itself as a binder stand-in
                    null
                }
                else -> null
            }
        }

        // IntentSender(IIntentSender) — hidden constructor, use reflection
        val constructor = IntentSender::class.java.getDeclaredConstructor(iIntentSenderClass)
        constructor.isAccessible = true
        return constructor.newInstance(proxy)
    }

    private fun mapInstallStatus(status: Int): Int {
        return when (status) {
            PackageInstaller.STATUS_SUCCESS -> STATUS_SUCCESS
            PackageInstaller.STATUS_FAILURE_ABORTED -> STATUS_FAILURE_ABORTED
            PackageInstaller.STATUS_FAILURE_BLOCKED -> STATUS_FAILURE_BLOCKED
            PackageInstaller.STATUS_FAILURE_CONFLICT -> STATUS_FAILURE_CONFLICT
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> STATUS_FAILURE_INCOMPATIBLE
            PackageInstaller.STATUS_FAILURE_INVALID -> STATUS_FAILURE_INVALID
            PackageInstaller.STATUS_FAILURE_STORAGE -> STATUS_FAILURE_STORAGE
            else -> STATUS_FAILURE
        }
    }

    override fun destroy() {
        // Cleanup — called when Shizuku unbinds the service
    }
}
