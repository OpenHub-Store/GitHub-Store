package zed.rainxch.githubstore

import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.AppTheme
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.model.appearance.FontTheme
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.domain.model.error.RateLimitInfo

data class MainState(
    val isLoggedIn: Boolean = false,
    val rateLimitInfo: RateLimitInfo? = null,
    val showRateLimitDialog: Boolean = false,
    val showSessionExpiredDialog: Boolean = false,
    val personality: AppPersonality = AppPersonality.MANGA,
    val accent: AccentId = AccentId.CRIMSON,
    val mangaPaper: MangaPaperId = MangaPaperId.DAY,
    val currentColorTheme: AppTheme = AppTheme.NORD,
    val isAmoledTheme: Boolean = false,
    val isDarkTheme: Boolean? = null,
    val currentFontTheme: FontTheme = FontTheme.CUSTOM,
    val isScrollbarEnabled: Boolean = false,
    val contentWidth: ContentWidth = ContentWidth.COMPACT,
    val appLanguageTag: String? = null,
    /**
     * True once every persisted appearance preference has emitted its first
     * value. The first frame is held until then so the UI never renders with
     * the hardcoded defaults (MANGA personality, NORD theme) before the
     * user's actual choices arrive from storage.
     */
    val appearanceLoaded: Boolean = false,
)
