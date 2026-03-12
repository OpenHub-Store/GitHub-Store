package zed.rainxch.core.data.utils

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.core.domain.model.InstalledApp
import zed.rainxch.core.domain.utils.AppLauncher

class AndroidAppLauncher(
    private val context: Context,
    private val logger: GitHubStoreLogger,
) : AppLauncher {
    override suspend fun launchApp(installedApp: InstalledApp): Result<Unit> =
        withContext(Dispatchers.Main) {
            runCatching {
                val packageManager = context.packageManager

                val launchIntent = packageManager.getLaunchIntentForPackage(installedApp.packageName)

                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    logger.debug("Launched app: ${installedApp.packageName}")
                } else {
                    throw Exception("No launch intent found for ${installedApp.packageName}")
                }
            }.onFailure { error ->
                logger.error("Failed to launch app ${installedApp.packageName}: ${error.message}")
            }
        }

    override suspend fun canLaunchApp(installedApp: InstalledApp): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val packageManager = context.packageManager
                packageManager.getLaunchIntentForPackage(installedApp.packageName) != null
            } catch (e: Exception) {
                false
            }
        }
}
