package zed.rainxch.core.data.services.dhizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import com.rosan.dhizuku.api.Dhizuku
import zed.rainxch.core.data.services.installer.PackageSessionInstaller

class DhizukuInstallerServiceImpl() : IDhizukuInstallerService.Stub() {

    companion object {
        private const val TAG = "DhizukuService"
        private fun log(msg: String) = android.util.Log.d(TAG, msg)
        private fun logE(msg: String, e: Throwable? = null) = android.util.Log.e(TAG, msg, e)

        private fun ownerPackageName(ctx: Context): String {
            val owner = runCatching {
                if (Dhizuku.init(ctx)) Dhizuku.getOwnerPackageName() else null
            }.getOrNull()?.takeIf { it.isNotBlank() }
            return owner ?: ctx.packageName
        }
    }

    override fun installPackage(
        pfd: ParcelFileDescriptor,
        fileSize: Long,
        expectedPackageName: String?,
        expectedVersionCode: Long,
        installerPackageName: String?,
        userId: Int,
        originatingUid: Int,
    ): Int {
        log(
            "installPackage() fileSize=$fileSize expected=$expectedPackageName@$expectedVersionCode " +
                "userId=$userId installer=$installerPackageName",
        )
        val ctx = PackageSessionInstaller.currentApplicationOrNull() ?: run {
            logE("currentApplication() returned null")
            return PackageSessionInstaller.STATUS_FAILURE
        }
        val owner = ownerPackageName(ctx)
        return try {
            PackageSessionInstaller.installFromPfd(
                context = ctx,
                pfd = pfd,
                fileSize = fileSize,
                packageName = expectedPackageName,
                expectedVersionCode = expectedVersionCode,
                installerPackageName = installerPackageName,
                callerPackageName = owner,
                userId = userId,
                originatingUid = originatingUid,
                privileged = true,
                installReason = PackageManager.INSTALL_REASON_POLICY,
            )
        } catch (e: Exception) {
            logE("installPackage() exception", e)
            PackageSessionInstaller.STATUS_FAILURE
        }
    }

    override fun uninstallPackage(packageName: String, userId: Int): Int {
        log("uninstallPackage() pkg=$packageName userId=$userId")
        val ctx = PackageSessionInstaller.currentApplicationOrNull() ?: run {
            logE("currentApplication() returned null")
            return PackageSessionInstaller.STATUS_FAILURE
        }
        return try {
            PackageSessionInstaller.uninstall(
                context = ctx,
                packageName = packageName,
                userId = userId,
                privileged = true,
                callerPackageName = ownerPackageName(ctx),
            )
        } catch (e: Exception) {
            logE("uninstallPackage() exception", e)
            PackageSessionInstaller.STATUS_FAILURE
        }
    }

    override fun destroy() {
        log("destroy() — service being unbound")
        System.exit(0)
    }
}
