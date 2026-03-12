package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import zed.rainxch.core.domain.model.Platform
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class DesktopFileLocationsProvider(
    private val platform: Platform,
) : FileLocationsProvider {
    override fun appDownloadsDir(): String {
        val baseDir =
            when (platform) {
                Platform.WINDOWS -> {
                    val appData =
                        System.getenv("LOCALAPPDATA")
                            ?: (System.getProperty("user.home") + "\\AppData\\Local")
                    File(appData, "GithubStore\\Downloads")
                }

                Platform.MACOS -> {
                    val home = System.getProperty("user.home")
                    File(home, "Library/Caches/GithubStore/Downloads")
                }

                Platform.LINUX -> {
                    val cacheHome =
                        System.getenv("XDG_CACHE_HOME")
                            ?: (System.getProperty("user.home") + "/.cache")
                    File(cacheHome, "githubstore/downloads")
                }

                else -> {
                    File(System.getProperty("user.home"), ".githubstore/downloads")
                }
            }

        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        return baseDir.absolutePath
    }

    override fun setExecutableIfNeeded(path: String) {
        if (platform == Platform.LINUX || platform == Platform.MACOS) {
            try {
                val file = File(path)
                val filePath = file.toPath()

                val perms = Files.getPosixFilePermissions(filePath).toMutableSet()

                perms.add(PosixFilePermission.OWNER_EXECUTE)
                perms.add(PosixFilePermission.GROUP_EXECUTE)
                perms.add(PosixFilePermission.OTHERS_EXECUTE)

                Files.setPosixFilePermissions(filePath, perms)
            } catch (e: Exception) {
                try {
                    Runtime.getRuntime().exec(arrayOf("chmod", "+x", path)).waitFor()
                } catch (e2: Exception) {
                    println("Warning: Could not set executable permission on $path")
                }
            }
        }
    }

    override fun userDownloadsDir(): String {
        val appSubdirName = "GitHub Store Downloads"
        val downloadsDir =
            when (platform) {
                Platform.WINDOWS -> {
                    val userProfile =
                        System.getenv("USERPROFILE")
                            ?: System.getProperty("user.home")
                    File(userProfile, "Downloads").resolve(appSubdirName)
                }

                Platform.MACOS -> {
                    val home = System.getProperty("user.home")
                    File(home, "Downloads").resolve(appSubdirName)
                }

                Platform.LINUX -> {
                    val xdgDownloads = getXdgDownloadsDir()
                    val baseDir =
                        if (xdgDownloads != null) {
                            File(xdgDownloads)
                        } else {
                            val home = System.getProperty("user.home")
                            File(home, "Downloads")
                        }
                    baseDir.resolve(appSubdirName)
                }

                else -> {
                    File(System.getProperty("user.home"), "Downloads").resolve(appSubdirName)
                }
            }

        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        return downloadsDir.absolutePath
    }

    override fun getCacheSizeBytes(): Long {
        val appDir = File(appDownloadsDir())
        val userDir = File(userDownloadsDir())
        return calculateDirSize(appDir) + calculateDirSize(userDir)
    }

    override fun clearCacheFiles(): Boolean {
        val appDir = File(appDownloadsDir())
        val userDir = File(userDownloadsDir())
        val appCleared = deleteDirectoryContents(appDir)
        val userCleared = deleteDirectoryContents(userDir)
        return appCleared && userCleared
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    private fun deleteDirectoryContents(dir: File): Boolean {
        if (!dir.exists()) return true
        var allDeleted = true
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (!deleteDirectoryContents(file)) allDeleted = false
                if (!file.delete()) allDeleted = false
            } else {
                if (!file.delete()) allDeleted = false
            }
        }
        return allDeleted
    }

    private fun getXdgDownloadsDir(): String? {
        return try {
            val userDirsFile =
                File(
                    System.getProperty("user.home"),
                    ".config/user-dirs.dirs",
                )

            if (userDirsFile.exists()) {
                userDirsFile.readLines().forEach { line ->
                    if (line.trim().startsWith("XDG_DOWNLOAD_DIR=")) {
                        val path =
                            line
                                .substringAfter("=")
                                .trim()
                                .removeSurrounding("\"")
                                .replace("\$HOME", System.getProperty("user.home"))
                        return path
                    }
                }
            }
            null
        } catch (e: Exception) {
            Logger.w { "Failed to read XDG user dirs: ${e.message}" }
            null
        }
    }
}
