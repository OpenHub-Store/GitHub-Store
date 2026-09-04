package zed.rainxch.core.data.services.root

import android.content.Context
import co.touchlab.kermit.Logger
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zed.rainxch.core.data.services.root.model.RootStatus
import java.io.File

class RootServiceManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val prefs by lazy {
        context.getSharedPreferences(ROOT_PREFS, Context.MODE_PRIVATE)
    }

    private fun wasGrantedBefore(): Boolean = prefs.getBoolean(KEY_GRANTED_BEFORE, false)

    private fun rememberGranted(granted: Boolean) {
        if (prefs.getBoolean(KEY_GRANTED_BEFORE, false) != granted) {
            prefs.edit().putBoolean(KEY_GRANTED_BEFORE, granted).apply()
        }
    }

    private val _status = MutableStateFlow(RootStatus.NOT_AVAILABLE)
    val status: StateFlow<RootStatus> = _status.asStateFlow()

    @Volatile
    private var configured = false

    fun initialize() {
        configureDefaultShell()
        scope.launch(Dispatchers.IO) { refreshStatusBlocking() }
    }

    fun refreshStatus() {
        configureDefaultShell()
        scope.launch(Dispatchers.IO) { refreshStatusBlocking() }
    }

    fun requestPermission() {
        configureDefaultShell()
        scope.launch(Dispatchers.IO) {
            Logger.d(TAG) { "requestPermission() — forcing main shell creation" }
            try {
                val shell = Shell.getShell()
                Logger.d(TAG) { "requestPermission() — shell rootStatus=${shell.status}" }
            } catch (e: Exception) {
                Logger.w(TAG) { "requestPermission() — getShell failed: ${e.javaClass.simpleName}: ${e.message}" }
            }
            refreshStatusBlocking()
        }
    }

    suspend fun installPackage(
        apkFile: File,
        installerPackageName: String?,
        userId: Int,
    ): Int? = withContext(Dispatchers.IO) {
        configureDefaultShell()
        if (!isRootGranted()) {
            Logger.w(TAG) { "installPackage() — root not granted, aborting" }
            return@withContext null
        }
        val safeInstaller = installerPackageName?.takeIf { it.isNotBlank() }
        if (safeInstaller != null && !PACKAGE_NAME_PATTERN.matches(safeInstaller)) {
            Logger.w(TAG) {
                "installPackage() — rejecting non-conformant installerPackageName='$safeInstaller'"
            }
            return@withContext STATUS_FAILURE
        }

        val tmpPath = "/data/local/tmp/ghs_${System.currentTimeMillis()}_${(0..Int.MAX_VALUE).random()}.apk"
        val srcPath = shellQuote(apkFile.absolutePath)
        val tmpPathQuoted = shellQuote(tmpPath)
        val safeUserId = userId.coerceAtLeast(0)

        try {
            val copyRes = Shell.cmd("cp $srcPath $tmpPathQuoted && chmod 644 $tmpPathQuoted").exec()
            if (!copyRes.isSuccess) {
                Logger.e(TAG) {
                    "installPackage() — staging copy failed: exit=${copyRes.code} out='${copyRes.out.joinToString("\n")}' err='${copyRes.err.joinToString("\n")}'"
                }
                return@withContext STATUS_FAILURE
            }

            val command = buildString {
                append("pm install --user ").append(safeUserId).append(' ')
                if (safeInstaller != null) append("-i ").append(safeInstaller).append(' ')
                append("-r ")
                append(tmpPathQuoted)
            }
            Logger.d(TAG) { "installPackage() — executing: $command" }
            val result = Shell.cmd(command).exec()
            val stdout = result.out.joinToString("\n").trim()
            val stderr = result.err.joinToString("\n").trim()
            Logger.d(TAG) { "installPackage() — exit=${result.code} stdout='$stdout' stderr='$stderr'" }
            if (result.isSuccess && stdout.contains("Success")) {
                STATUS_SUCCESS
            } else {
                Logger.w(TAG) { "installPackage() — pm reported failure: stdout='$stdout' stderr='$stderr'" }
                STATUS_FAILURE
            }
        } finally {
            Shell.cmd("rm -f $tmpPathQuoted").submit()
        }
    }

    suspend fun uninstallPackage(
        packageName: String,
        userId: Int,
    ): Int? = withContext(Dispatchers.IO) {
        configureDefaultShell()
        if (!isRootGranted()) {
            Logger.w(TAG) { "uninstallPackage() — root not granted, aborting" }
            return@withContext null
        }
        if (!PACKAGE_NAME_PATTERN.matches(packageName)) {
            Logger.w(TAG) {
                "uninstallPackage() — rejecting non-conformant packageName='$packageName'"
            }
            return@withContext STATUS_FAILURE
        }
        val safeUserId = userId.coerceAtLeast(0)
        val result = Shell.cmd("pm uninstall --user $safeUserId $packageName").exec()
        val stdout = result.out.joinToString("\n").trim()
        Logger.d(TAG) { "uninstallPackage($packageName, $safeUserId) — exit=${result.code} stdout='$stdout'" }
        if (result.isSuccess && stdout.contains("Success")) STATUS_SUCCESS else STATUS_FAILURE
    }

    private fun refreshStatusBlocking() {
        val computed = computeStatus()
        if (_status.value != computed) {
            Logger.d(TAG) { "refreshStatus() — $computed (was ${_status.value})" }
        }
        _status.value = computed
    }

    private fun computeStatus(): RootStatus {
        Shell.getCachedShell()?.let { shell ->
            rememberGranted(shell.isRoot)
            return if (shell.isRoot) RootStatus.READY else RootStatus.NOT_AVAILABLE
        }
        return when (Shell.isAppGrantedRoot()) {
            true -> {
                rememberGranted(true)
                RootStatus.READY
            }
            // false/null on a fresh process (no shell created yet) is unreliable. If root
            // was granted before, trust the su manager's persistent grant instead of
            // re-prompting on every launch (GH#705). A real revoke is caught lazily when
            // an install actually spawns the shell (isRootGranted below).
            else -> if (wasGrantedBefore()) RootStatus.READY else RootStatus.PERMISSION_NEEDED
        }
    }

    // Establishes the root shell and reports whether root is actually granted. With a
    // persistent su grant this is silent (no prompt); if the grant was revoked the shell
    // comes back non-root and we forget the remembered grant so the status corrects.
    private fun isRootGranted(): Boolean =
        try {
            val granted = Shell.getShell().isRoot
            rememberGranted(granted)
            granted
        } catch (e: Exception) {
            Logger.w(TAG) { "isRootGranted() — getShell failed: ${e.message}" }
            false
        }

    private fun configureDefaultShell() {
        if (configured) return
        synchronized(this) {
            if (configured) return
            try {
                Shell.setDefaultBuilder(
                    Shell.Builder.create()
                        .setTimeout(SHELL_TIMEOUT_SECONDS),
                )
            } catch (e: IllegalStateException) {
                Logger.d(TAG) { "configureDefaultShell() — main shell already constructed, skipping setDefaultBuilder" }
            }
            configured = true
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    companion object {
        private const val TAG = "RootServiceManager"
        private const val ROOT_PREFS = "ghs_root_prefs"
        private const val KEY_GRANTED_BEFORE = "root_granted_before"
        private const val STATUS_SUCCESS = 0
        private const val STATUS_FAILURE = -1
        private const val SHELL_TIMEOUT_SECONDS = 10L

        private val PACKAGE_NAME_PATTERN =
            Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")
    }
}
