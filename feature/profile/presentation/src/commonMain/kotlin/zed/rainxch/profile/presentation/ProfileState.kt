package zed.rainxch.profile.presentation

import zed.rainxch.core.domain.model.AppTheme
import zed.rainxch.core.domain.model.FontTheme
import zed.rainxch.core.domain.model.InstallerType
import zed.rainxch.core.domain.model.ShizukuAvailability
import zed.rainxch.profile.domain.model.UserProfile
import zed.rainxch.profile.presentation.model.ProxyType

data class ProfileState(
    val userProfile: UserProfile? = null,
    val selectedThemeColor: AppTheme = AppTheme.OCEAN,
    val selectedFontTheme: FontTheme = FontTheme.CUSTOM,
    val isLogoutDialogVisible: Boolean = false,
    val isUserLoggedIn: Boolean = false,
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
    val installerType: InstallerType = InstallerType.DEFAULT,
    val shizukuAvailability: ShizukuAvailability = ShizukuAvailability.UNAVAILABLE,
    val autoUpdateEnabled: Boolean = false,
)
