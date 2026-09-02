package zed.rainxch.core.data.services.shizuku

import android.os.ParcelFileDescriptor
import zed.rainxch.core.data.services.installer.PackageSessionInstaller

class ShizukuInstallerServiceImpl() : IShizukuInstallerService.Stub() {

    companion object {
        private const val TAG = "ShizukuService"
        private fun log(msg: String) = android.util.Log.d(TAG, msg)
        private fun logE(msg: String, e: Throwable? = null) = android.util.Log.e(TAG, msg, e)
    }

    override fun installPackage(
        pfd: ParcelFileDescriptor,
        fileSize: Long,
        packageName: String?,
        installerPackageName: String?,
        userId: Int,
        originatingUid: Int,
    ): Int {
        log("installPackage() fileSize=$fileSize pkg=$packageName userId=$userId installer=$installerPackageName")
        val ctx = PackageSessionInstaller.currentApplicationOrNull() ?: run {
            logE("currentApplication() returned null")
            return PackageSessionInstaller.STATUS_FAILURE
        }
        return try {
            PackageSessionInstaller.installFromPfd(
                context = ctx,
                pfd = pfd,
                fileSize = fileSize,
                packageName = packageName,
                expectedVersionCode = -1L,
                installerPackageName = installerPackageName,
                callerPackageName = ctx.packageName,
                userId = userId,
                originatingUid = originatingUid,
                privileged = true,
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
                callerPackageName = ctx.packageName,
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
