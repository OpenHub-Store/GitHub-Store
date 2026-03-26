package zed.rainxch.tweaks.presentation

import zed.rainxch.core.domain.model.AppTheme
import zed.rainxch.core.domain.model.FontTheme
import zed.rainxch.core.domain.model.InstallerType
import zed.rainxch.tweaks.presentation.model.ProxyType

sealed interface TweaksAction {
    data object OnNavigateBackClick : TweaksAction

    data class OnThemeColorSelected(
        val themeColor: AppTheme,
    ) : TweaksAction

    data class OnAmoledThemeToggled(
        val enabled: Boolean,
    ) : TweaksAction

    data class OnDarkThemeChange(
        val isDarkTheme: Boolean?,
    ) : TweaksAction

    data class OnFontThemeSelected(
        val fontTheme: FontTheme,
    ) : TweaksAction

    data class OnLiquidGlassEnabledChange(
        val enabled: Boolean,
    ) : TweaksAction

    data class OnScrollbarToggled(
        val enabled: Boolean,
    ) : TweaksAction

    data class OnProxyTypeSelected(
        val type: ProxyType,
    ) : TweaksAction

    data class OnProxyHostChanged(
        val host: String,
    ) : TweaksAction

    data class OnProxyPortChanged(
        val port: String,
    ) : TweaksAction

    data class OnProxyUsernameChanged(
        val username: String,
    ) : TweaksAction

    data class OnProxyPasswordChanged(
        val password: String,
    ) : TweaksAction

    data object OnProxyPasswordVisibilityToggle : TweaksAction

    data object OnProxySave : TweaksAction

    data class OnInstallerTypeSelected(
        val type: InstallerType,
    ) : TweaksAction

    data object OnRequestShizukuPermission : TweaksAction

    data class OnAutoUpdateToggled(
        val enabled: Boolean,
    ) : TweaksAction

    data class OnUpdateCheckIntervalChanged(
        val hours: Long,
    ) : TweaksAction

    data class OnIncludePreReleasesToggled(
        val enabled: Boolean,
    ) : TweaksAction

    data class OnAutoDetectClipboardToggled(
        val enabled: Boolean,
    ) : TweaksAction

    data class OnHideSeenToggled(
        val enabled: Boolean,
    ) : TweaksAction

    data object OnClearSeenRepos : TweaksAction

    data object OnRefreshCacheSize : TweaksAction

    data object OnClearCacheClick : TweaksAction

    data object OnClearDownloadsConfirm : TweaksAction

    data object OnClearDownloadsDismiss : TweaksAction

    data object OnHelpClick : TweaksAction
}
