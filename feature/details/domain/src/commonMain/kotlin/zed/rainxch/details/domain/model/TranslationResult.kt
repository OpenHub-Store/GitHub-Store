package zed.rainxch.details.domain.model

data class TranslationResult(
    val translatedText: String,
    val detectedSourceLanguage: String?,
)
