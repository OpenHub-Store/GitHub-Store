package zed.rainxch.tweaks.presentation

sealed interface TweaksEvent {
    data object OnProxySaved : TweaksEvent

    data class OnProxySaveError(
        val message: String,
    ) : TweaksEvent

    data object OnCacheCleared : TweaksEvent

    data class OnCacheClearError(
        val message: String,
    ) : TweaksEvent

    data object OnSeenHistoryCleared : TweaksEvent
}
