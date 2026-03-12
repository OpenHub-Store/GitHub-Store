package zed.rainxch.core.data.services

import java.util.Locale

class DesktopLocalizationManager : LocalizationManager {
    override fun getCurrentLanguageCode(): String {
        val locale = Locale.getDefault()
        val language = locale.language
        val country = locale.country
        return if (country.isNotEmpty()) {
            "$language-$country"
        } else {
            language
        }
    }

    override fun getPrimaryLanguageCode(): String = Locale.getDefault().language
}
