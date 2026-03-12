package zed.rainxch.details.domain.repository

import zed.rainxch.details.domain.model.TranslationResult

interface TranslationRepository {
    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String = "auto",
    ): TranslationResult

    fun getDeviceLanguageCode(): String
}
