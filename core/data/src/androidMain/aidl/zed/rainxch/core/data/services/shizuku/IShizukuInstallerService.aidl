package zed.rainxch.core.data.services.shizuku;

interface IShizukuInstallerService {
    int installPackage(
        in ParcelFileDescriptor pfd,
        long fileSize,
        String packageName,
        String installerPackageName,
        int userId,
        int originatingUid
    ) = 1;
    int uninstallPackage(String packageName, int userId) = 2;
    void destroy() = 16777114;
}
