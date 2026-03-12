package zed.rainxch.details.presentation.model

sealed interface TranslationTarget {
    data object About : TranslationTarget

    data object WhatsNew : TranslationTarget
}
