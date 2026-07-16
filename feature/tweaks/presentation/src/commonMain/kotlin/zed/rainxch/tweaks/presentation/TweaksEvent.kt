package zed.rainxch.tweaks.presentation

sealed interface TweaksEvent {
    data object OnProxySaved : TweaksEvent

    data class OnProxySaveError(
        val message: String,
    ) : TweaksEvent

    data class OnProxyTestSuccess(
        val latencyMs: Long,
    ) : TweaksEvent

    data class OnProxyTestError(
        val message: String,
    ) : TweaksEvent

    data class OnMasterProxyTestResult(
        val searchMs: Long?,
        val downloadMs: Long?,
        val translationMs: Long?,
    ) : TweaksEvent

    data object OnCacheCleared : TweaksEvent

    data class OnCacheClearError(
        val message: String,
    ) : TweaksEvent

    data object OnSeenHistoryCleared : TweaksEvent

    data object OnTranslationProviderSaved : TweaksEvent

    data object OnYoudaoCredentialsSaved : TweaksEvent

    data object OnLibreTranslateCredentialsSaved : TweaksEvent

    data object OnDeeplCredentialsSaved : TweaksEvent

    data object OnMicrosoftTranslatorCredentialsSaved : TweaksEvent

    data object OnAppLanguageChangeRequiresRestart : TweaksEvent

    data object OnSettingsExported : TweaksEvent
    data object OnSettingsImported : TweaksEvent
    data class OnSettingsExportError(
        val message: String,
    ) : TweaksEvent
    data class OnSettingsImportError(
        val message: String,
    ) : TweaksEvent
}
