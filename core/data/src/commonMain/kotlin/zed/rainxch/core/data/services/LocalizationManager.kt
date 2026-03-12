package zed.rainxch.core.data.services

interface LocalizationManager {
    /**
     * Returns the current device language code in ISO 639-1 format (e.g., "en", "zh", "ja")
     * Can include region code if available (e.g., "zh-CN", "pt-BR")
     */
    fun getCurrentLanguageCode(): String

    /**
     * Returns the primary language code without region (e.g., "zh" from "zh-CN")
     */
    fun getPrimaryLanguageCode(): String
}
