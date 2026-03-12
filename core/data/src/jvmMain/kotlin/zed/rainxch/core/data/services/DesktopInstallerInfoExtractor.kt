package zed.rainxch.core.data.services

import zed.rainxch.core.domain.model.ApkPackageInfo
import zed.rainxch.core.domain.system.InstallerInfoExtractor

class DesktopInstallerInfoExtractor : InstallerInfoExtractor {
    override suspend fun extractPackageInfo(filePath: String): ApkPackageInfo? = null
}
