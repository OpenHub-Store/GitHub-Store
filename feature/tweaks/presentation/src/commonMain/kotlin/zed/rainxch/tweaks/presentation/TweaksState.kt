package zed.rainxch.tweaks.presentation

import zed.rainxch.core.domain.model.AppTheme
import zed.rainxch.core.domain.model.FontTheme
import zed.rainxch.core.domain.model.InstallerType
import zed.rainxch.core.domain.model.ShizukuAvailability
import zed.rainxch.tweaks.presentation.model.ProxyType

data class TweaksState(
    val selectedThemeColor: AppTheme = AppTheme.OCEAN,
    val selectedFontTheme: FontTheme = FontTheme.CUSTOM,
    val isAmoledThemeEnabled: Boolean = false,
    val isDarkTheme: Boolean? = null,
    val versionName: String = "",
    val proxyType: ProxyType = ProxyType.NONE,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val isProxyPasswordVisible: Boolean = false,
    val autoDetectClipboardLinks: Boolean = true,
    val cacheSize: String = "",
    val isClearDownloadsDialogVisible: Boolean = false,
    val installerType: InstallerType = InstallerType.DEFAULT,
    val shizukuAvailability: ShizukuAvailability = ShizukuAvailability.UNAVAILABLE,
    val autoUpdateEnabled: Boolean = false,
    val updateCheckIntervalHours: Long = 6L,
    val includePreReleases: Boolean = false,
    val isLiquidGlassEnabled: Boolean = true,
    val isHideSeenEnabled: Boolean = false,
    val isScrollbarEnabled: Boolean = false,
)
