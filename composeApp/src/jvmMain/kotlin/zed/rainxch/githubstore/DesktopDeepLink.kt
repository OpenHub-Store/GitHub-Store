package zed.rainxch.githubstore

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

object DesktopDeepLink {
    private const val SINGLE_INSTANCE_PORT = 47632
    private const val SCHEME = "githubstore"
    private const val DESKTOP_FILE_NAME = "github-store-deeplink"

    /**
     * On Windows and Linux, ensure the `githubstore://` protocol is registered.
     * - Windows: Writes to HKCU registry.
     * - Linux: Creates a `.desktop` file and registers via `xdg-mime`.
     * No-op on macOS (handled via Info.plist in the packaged .app).
     */
    fun registerUriSchemeIfNeeded() {
        when {
            isWindows() -> registerWindows()
            isLinux() -> registerLinux()
        }
    }

    private fun registerWindows() {
        val checkResult =
            runCommand(
                "reg",
                "query",
                "HKCU\\SOFTWARE\\Classes\\$SCHEME",
                "/ve",
            )
        if (checkResult != null && checkResult.contains("URL:")) return

        val exePath = resolveExePath() ?: return

        runCommand(
            "reg",
            "add",
            "HKCU\\SOFTWARE\\Classes\\$SCHEME",
            "/ve",
            "/d",
            "URL:GitHub Store Protocol",
            "/f",
        )
        runCommand(
            "reg",
            "add",
            "HKCU\\SOFTWARE\\Classes\\$SCHEME",
            "/v",
            "URL Protocol",
            "/d",
            "",
            "/f",
        )
        runCommand(
            "reg",
            "add",
            "HKCU\\SOFTWARE\\Classes\\$SCHEME\\DefaultIcon",
            "/ve",
            "/d",
            "\"$exePath\",1",
            "/f",
        )
        runCommand(
            "reg",
            "add",
            "HKCU\\SOFTWARE\\Classes\\$SCHEME\\shell\\open\\command",
            "/ve",
            "/d",
            "\"$exePath\" \"%1\"",
            "/f",
        )
    }

    private fun registerLinux() {
        val appsDir = File(System.getProperty("user.home"), ".local/share/applications")
        val desktopFile = File(appsDir, "$DESKTOP_FILE_NAME.desktop")

        if (desktopFile.exists()) return

        val exePath = resolveExePath() ?: return

        appsDir.mkdirs()

        desktopFile.writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=GitHub Store
            Exec="$exePath" %u
            Terminal=false
            MimeType=x-scheme-handler/$SCHEME;
            NoDisplay=true
            """.trimIndent(),
        )

        runCommand("xdg-mime", "default", "$DESKTOP_FILE_NAME.desktop", "x-scheme-handler/$SCHEME")
    }

    /**
     * Try to forward a deep link URI to an already-running instance.
     * @return `true` if the URI was forwarded (this instance should exit),
     *         `false` if no existing instance is running.
     */
    fun tryForwardToRunningInstance(uri: String): Boolean =
        try {
            Socket("127.0.0.1", SINGLE_INSTANCE_PORT).use { socket ->
                PrintWriter(socket.getOutputStream(), true).println(uri)
            }
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Start listening for URIs forwarded from new instances.
     * Calls [onUri] on the main thread when a URI is received.
     */
    fun startInstanceListener(onUri: (String) -> Unit) {
        val thread =
            Thread({
                try {
                    val server = ServerSocket(SINGLE_INSTANCE_PORT, 50, InetAddress.getLoopbackAddress())
                    while (true) {
                        val client = server.accept()
                        try {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            val uri = reader.readLine()
                            if (!uri.isNullOrBlank()) {
                                onUri(uri.trim())
                            }
                        } catch (_: Exception) {
                        } finally {
                            client.close()
                        }
                    }
                } catch (_: Exception) {
                }
            }, "DeepLinkListener")
        thread.isDaemon = true
        thread.start()
    }

    private fun isWindows(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    private fun isLinux(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("linux") == true

    private fun resolveExePath(): String? =
        try {
            ProcessHandle
                .current()
                .info()
                .command()
                .orElse(null)
        } catch (_: Exception) {
            null
        }

    private fun runCommand(vararg cmd: String): String? =
        try {
            val process =
                ProcessBuilder(*cmd)
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (_: Exception) {
            null
        }
}
