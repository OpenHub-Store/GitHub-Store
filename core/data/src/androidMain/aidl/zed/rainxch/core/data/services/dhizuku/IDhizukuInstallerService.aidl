package zed.rainxch.core.data.services.dhizuku;

interface IDhizukuInstallerService {
    int installPackage(
        in ParcelFileDescriptor pfd,
        long fileSize,
        String expectedPackageName,
        long expectedVersionCode,
        String installerPackageName,
        int userId,
        int originatingUid
    );
    int uninstallPackage(String packageName, int userId);
    void destroy();
}
