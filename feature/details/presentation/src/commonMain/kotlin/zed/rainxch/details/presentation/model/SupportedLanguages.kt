package zed.rainxch.details.presentation.model

import zed.rainxch.details.domain.model.SupportedLanguage

object SupportedLanguages {
    val all: List<SupportedLanguage> =
        listOf(
            SupportedLanguage("ar", "Arabic"),
            SupportedLanguage("bn", "Bengali"),
            SupportedLanguage("zh-CN", "Chinese (Simplified)"),
            SupportedLanguage("zh-TW", "Chinese (Traditional)"),
            SupportedLanguage("cs", "Czech"),
            SupportedLanguage("da", "Danish"),
            SupportedLanguage("nl", "Dutch"),
            SupportedLanguage("en", "English"),
            SupportedLanguage("fi", "Finnish"),
            SupportedLanguage("fr", "French"),
            SupportedLanguage("de", "German"),
            SupportedLanguage("el", "Greek"),
            SupportedLanguage("he", "Hebrew"),
            SupportedLanguage("hi", "Hindi"),
            SupportedLanguage("hu", "Hungarian"),
            SupportedLanguage("id", "Indonesian"),
            SupportedLanguage("it", "Italian"),
            SupportedLanguage("ja", "Japanese"),
            SupportedLanguage("ko", "Korean"),
            SupportedLanguage("ms", "Malay"),
            SupportedLanguage("no", "Norwegian"),
            SupportedLanguage("pl", "Polish"),
            SupportedLanguage("pt", "Portuguese"),
            SupportedLanguage("pt-BR", "Portuguese (Brazil)"),
            SupportedLanguage("ro", "Romanian"),
            SupportedLanguage("ru", "Russian"),
            SupportedLanguage("es", "Spanish"),
            SupportedLanguage("sv", "Swedish"),
            SupportedLanguage("th", "Thai"),
            SupportedLanguage("tr", "Turkish"),
            SupportedLanguage("uk", "Ukrainian"),
            SupportedLanguage("uz", "Uzbek"),
            SupportedLanguage("vi", "Vietnamese"),
        )
}
