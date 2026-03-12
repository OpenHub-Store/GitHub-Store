package zed.rainxch.profile.presentation

import zed.rainxch.core.domain.model.AppTheme
import zed.rainxch.core.domain.model.FontTheme
import zed.rainxch.profile.presentation.model.ProxyType

sealed interface ProfileAction {
    data object OnNavigateBackClick : ProfileAction

    data class OnThemeColorSelected(
        val themeColor: AppTheme,
    ) : ProfileAction

    data class OnAmoledThemeToggled(
        val enabled: Boolean,
    ) : ProfileAction

    data class OnDarkThemeChange(
        val isDarkTheme: Boolean?,
    ) : ProfileAction

    data object OnLogoutClick : ProfileAction

    data object OnLogoutConfirmClick : ProfileAction

    data object OnStarredReposClick : ProfileAction

    data object OnFavouriteReposClick : ProfileAction

    data object OnLogoutDismiss : ProfileAction

    data object OnHelpClick : ProfileAction

    data object OnLoginClick : ProfileAction

    data object OnClearCacheClick : ProfileAction

    data class OnFontThemeSelected(
        val fontTheme: FontTheme,
    ) : ProfileAction

    data class OnProxyTypeSelected(
        val type: ProxyType,
    ) : ProfileAction

    data class OnProxyHostChanged(
        val host: String,
    ) : ProfileAction

    data class OnProxyPortChanged(
        val port: String,
    ) : ProfileAction

    data class OnRepositoriesClick(
        val username: String,
    ) : ProfileAction

    data class OnProxyUsernameChanged(
        val username: String,
    ) : ProfileAction

    data class OnProxyPasswordChanged(
        val password: String,
    ) : ProfileAction

    data object OnProxyPasswordVisibilityToggle : ProfileAction

    data object OnProxySave : ProfileAction

    data class OnAutoDetectClipboardToggled(
        val enabled: Boolean,
    ) : ProfileAction

    data object OnSponsorClick : ProfileAction
}
