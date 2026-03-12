package zed.rainxch.core.data.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.InstalledApp
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.core.domain.utils.AppLauncher
import java.io.File

class DesktopAppLauncher(
    private val logger: GitHubStoreLogger,
    private val platform: Platform,
) : AppLauncher {
    override suspend fun launchApp(installedApp: InstalledApp): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (platform) {
                    Platform.WINDOWS -> launchWindowsApp(installedApp)
                    Platform.MACOS -> launchMacOSApp(installedApp)
                    Platform.LINUX -> launchLinuxApp(installedApp)
                    else -> throw Exception("Unsupported platform: $platform")
                }
            }.onFailure { error ->
                logger.error("Failed to launch app ${installedApp.appName}: ${error.message}")
            }
        }

    override suspend fun canLaunchApp(installedApp: InstalledApp): Boolean =
        withContext(Dispatchers.IO) {
            when (platform) {
                Platform.WINDOWS -> canLaunchWindowsApp(installedApp)
                Platform.MACOS -> canLaunchMacOSApp(installedApp)
                Platform.LINUX -> canLaunchLinuxApp(installedApp)
                else -> false
            }
        }

    private fun launchWindowsApp(installedApp: InstalledApp) {
        val programFiles =
            listOfNotNull(
                System.getenv("ProgramFiles"),
                System.getenv("ProgramFiles(x86)"),
                System.getenv("LOCALAPPDATA"),
            )

        var launched = false

        for (basePath in programFiles) {
            val possiblePaths =
                listOf(
                    File(basePath, installedApp.appName),
                    File(basePath, installedApp.repoName),
                    File(basePath, installedApp.packageName.substringAfterLast(".")),
                )

            for (appDir in possiblePaths) {
                if (appDir.exists() && appDir.isDirectory) {
                    val exeFiles =
                        appDir
                            .walkTopDown()
                            .maxDepth(3)
                            .filter { it.extension.equals("exe", ignoreCase = true) }
                            .filter { !it.name.contains("uninstall", ignoreCase = true) }
                            .toList()

                    val mainExe =
                        exeFiles.find {
                            it.nameWithoutExtension.equals(installedApp.appName, ignoreCase = true) ||
                                it.nameWithoutExtension.equals(
                                    installedApp.repoName,
                                    ignoreCase = true,
                                )
                        } ?: exeFiles.firstOrNull()

                    if (mainExe != null) {
                        ProcessBuilder("cmd", "/c", "start", "", mainExe.absolutePath)
                            .start()
                        launched = true
                        logger.debug("Launched Windows app from: ${mainExe.absolutePath}")
                        break
                    }
                }
            }
            if (launched) break
        }

        if (!launched) {
            val appNameVariations =
                listOf(
                    installedApp.appName,
                    installedApp.repoName,
                    installedApp.appName.replace(" ", ""),
                )

            for (name in appNameVariations) {
                try {
                    ProcessBuilder("cmd", "/c", "start", "", name)
                        .start()
                    launched = true
                    logger.debug("Launched Windows app using shell: $name")
                    break
                } catch (e: Exception) {
                }
            }
        }

        if (!launched) {
            val displayName = findWindowsDisplayName(installedApp)
            if (displayName != null) {
                val installLocation = getWindowsInstallLocation(displayName)
                if (installLocation != null) {
                    val installDir = File(installLocation)
                    val exeFiles =
                        installDir.listFiles { file ->
                            file.extension.equals("exe", ignoreCase = true) &&
                                !file.name.contains("uninstall", ignoreCase = true)
                        }

                    val mainExe = exeFiles?.firstOrNull()
                    if (mainExe != null) {
                        ProcessBuilder("cmd", "/c", "start", "", mainExe.absolutePath)
                            .start()
                        launched = true
                        logger.debug("Launched Windows app from registry location: ${mainExe.absolutePath}")
                    }
                }
            }
        }

        if (!launched) {
            throw Exception("Could not find executable for ${installedApp.appName}")
        }
    }

    private fun launchMacOSApp(installedApp: InstalledApp) {
        val appName =
            if (installedApp.appName.endsWith(".app")) {
                installedApp.appName
            } else {
                "${installedApp.appName}.app"
            }

        val appPath = File("/Applications", appName)

        if (appPath.exists()) {
            ProcessBuilder("open", "-a", appPath.absolutePath).start()
            logger.debug("Launched macOS app: ${appPath.absolutePath}")
            return
        }

        val appsDir = File("/Applications")
        val matchingApp =
            appsDir.listFiles()?.find { file ->
                file.isDirectory &&
                    file.name.endsWith(".app") &&
                    (
                        file.name.contains(installedApp.appName, ignoreCase = true) ||
                            file.name.contains(installedApp.repoName, ignoreCase = true)
                    )
            }

        if (matchingApp != null) {
            ProcessBuilder("open", "-a", matchingApp.absolutePath).start()
            logger.debug("Launched macOS app: ${matchingApp.absolutePath}")
            return
        }

        try {
            ProcessBuilder("open", "-b", installedApp.packageName).start()
            logger.debug("Launched macOS app by bundle ID: ${installedApp.packageName}")
            return
        } catch (e: Exception) {
        }

        val userAppsDir = File(System.getProperty("user.home"), "Applications")
        val userMatchingApp =
            userAppsDir.listFiles()?.find { file ->
                file.isDirectory &&
                    file.name.endsWith(".app") &&
                    (
                        file.name.contains(installedApp.appName, ignoreCase = true) ||
                            file.name.contains(installedApp.repoName, ignoreCase = true)
                    )
            }

        if (userMatchingApp != null) {
            ProcessBuilder("open", "-a", userMatchingApp.absolutePath).start()
            logger.debug("Launched macOS app from user folder: ${userMatchingApp.absolutePath}")
            return
        }

        throw Exception("Could not find app for ${installedApp.appName}")
    }

    private fun launchLinuxApp(installedApp: InstalledApp) {
        val commandVariations =
            listOf(
                installedApp.appName.lowercase().replace(" ", "-"),
                installedApp.appName.lowercase().replace(" ", ""),
                installedApp.repoName.lowercase(),
                installedApp.packageName.substringAfterLast("."),
            )

        for (command in commandVariations) {
            try {
                ProcessBuilder(command).start()
                logger.debug("Launched Linux app with command: $command")
                return
            } catch (e: Exception) {
            }
        }

        val desktopFileDirs =
            listOf(
                "/usr/share/applications",
                "/usr/local/share/applications",
                "${System.getProperty("user.home")}/.local/share/applications",
            )

        for (dir in desktopFileDirs) {
            val desktopDir = File(dir)
            if (!desktopDir.exists()) continue

            val desktopFile =
                desktopDir.listFiles()?.find { file ->
                    file.extension.equals("desktop", ignoreCase = true) &&
                        (
                            file.nameWithoutExtension.contains(
                                installedApp.appName,
                                ignoreCase = true,
                            ) ||
                                file.nameWithoutExtension.contains(
                                    installedApp.repoName,
                                    ignoreCase = true,
                                )
                        )
                }

            if (desktopFile != null) {
                val execLine =
                    desktopFile
                        .readLines()
                        .find {
                            it.trim().startsWith("Exec=")
                        }?.substringAfter("Exec=")

                if (execLine != null) {
                    val command = execLine.replace(Regex("%[fFuU]"), "").trim()
                    ProcessBuilder("sh", "-c", command).start()
                    logger.debug("Launched Linux app from .desktop file: $command")
                    return
                }
            }
        }

        try {
            val flatpakList = executeCommand("flatpak", "list", "--app")
            if (flatpakList.contains(installedApp.appName, ignoreCase = true) ||
                flatpakList.contains(installedApp.repoName, ignoreCase = true)
            ) {
                val appId =
                    flatpakList
                        .lines()
                        .find {
                            it.contains(installedApp.appName, ignoreCase = true) ||
                                it.contains(installedApp.repoName, ignoreCase = true)
                        }?.split(Regex("\\s+"))
                        ?.firstOrNull()

                if (appId != null) {
                    ProcessBuilder("flatpak", "run", appId).start()
                    logger.debug("Launched Linux app via flatpak: $appId")
                    return
                }
            }
        } catch (e: Exception) {
        }

        if (installedApp.fileExtension.equals("appimage", ignoreCase = true)) {
            val appImageDirs =
                listOf(
                    "${System.getProperty("user.home")}/Applications",
                    "${System.getProperty("user.home")}/.local/bin",
                    "/opt",
                )

            for (dir in appImageDirs) {
                val dirFile = File(dir)
                val appImage =
                    dirFile.listFiles()?.find { file ->
                        file.extension.equals("appimage", ignoreCase = true) &&
                            file.nameWithoutExtension.contains(
                                installedApp.appName,
                                ignoreCase = true,
                            )
                    }

                if (appImage != null && appImage.canExecute()) {
                    ProcessBuilder(appImage.absolutePath).start()
                    logger.debug("Launched AppImage: ${appImage.absolutePath}")
                    return
                }
            }
        }

        throw Exception("Could not launch ${installedApp.appName}")
    }

    private fun canLaunchWindowsApp(installedApp: InstalledApp): Boolean {
        val programFiles =
            listOfNotNull(
                System.getenv("ProgramFiles"),
                System.getenv("ProgramFiles(x86)"),
            )

        for (basePath in programFiles) {
            val appDir = File(basePath, installedApp.appName)
            if (appDir.exists() && appDir.isDirectory) {
                val hasExe =
                    appDir
                        .walkTopDown()
                        .maxDepth(3)
                        .any { it.extension.equals("exe", ignoreCase = true) }
                if (hasExe) return true
            }
        }

        return false
    }

    private fun canLaunchMacOSApp(installedApp: InstalledApp): Boolean {
        val appPath = File("/Applications", "${installedApp.appName}.app")
        if (appPath.exists()) return true

        val appsDir = File("/Applications")
        return appsDir.listFiles()?.any { file ->
            file.name.endsWith(".app") &&
                file.name.contains(installedApp.appName, ignoreCase = true)
        } ?: false
    }

    private fun canLaunchLinuxApp(installedApp: InstalledApp): Boolean {
        val command = installedApp.appName.lowercase().replace(" ", "-")
        val which = executeCommand("which", command)
        if (which.isNotBlank()) return true

        val desktopFileDirs =
            listOf(
                "/usr/share/applications",
                "/usr/local/share/applications",
                "${System.getProperty("user.home")}/.local/share/applications",
            )

        return desktopFileDirs.any { dir ->
            File(dir).listFiles()?.any { file ->
                file.extension.equals("desktop", ignoreCase = true) &&
                    file.nameWithoutExtension.contains(installedApp.appName, ignoreCase = true)
            } ?: false
        }
    }

    private fun findWindowsDisplayName(installedApp: InstalledApp): String? {
        val searchName = installedApp.appName
        val result =
            executeCommand(
                "powershell",
                "-Command",
                "Get-ItemProperty HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\* | Where-Object { \$_.DisplayName -like '*$searchName*' } | Select-Object -First 1 -ExpandProperty DisplayName",
            )
        return result.trim().takeIf { it.isNotBlank() }
    }

    private fun getWindowsInstallLocation(displayName: String): String? {
        val result =
            executeCommand(
                "powershell",
                "-Command",
                "Get-ItemProperty HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\* | Where-Object { \$_.DisplayName -eq '$displayName' } | Select-Object -First 1 -ExpandProperty InstallLocation",
            )
        return result.trim().takeIf { it.isNotBlank() }
    }

    private fun executeCommand(vararg command: String): String =
        try {
            val process =
                ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
}
