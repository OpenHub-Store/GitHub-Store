package zed.rainxch.profile.presentation

sealed interface ProfileEvent {
    data object OnLogoutSuccessful : ProfileEvent

    data class OnLogoutError(
        val message: String,
    ) : ProfileEvent

    data object OnProxySaved : ProfileEvent

    data class OnProxySaveError(
        val message: String,
    ) : ProfileEvent

    data object OnCacheCleared : ProfileEvent

    data class OnCacheClearError(
        val message: String,
    ) : ProfileEvent
}
