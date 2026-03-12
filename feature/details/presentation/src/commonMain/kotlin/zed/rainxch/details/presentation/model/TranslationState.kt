package zed.rainxch.details.presentation.model

data class TranslationState(
    val isTranslating: Boolean = false,
    val translatedText: String? = null,
    val isShowingTranslation: Boolean = false,
    val targetLanguageCode: String? = null,
    val targetLanguageDisplayName: String? = null,
    val detectedSourceLanguage: String? = null,
    val error: String? = null,
)
