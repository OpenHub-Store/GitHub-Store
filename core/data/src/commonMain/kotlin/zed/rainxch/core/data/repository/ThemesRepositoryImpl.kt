package zed.rainxch.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.domain.model.AppTheme
import zed.rainxch.core.domain.model.FontTheme
import zed.rainxch.core.domain.model.InstallerType
import zed.rainxch.core.domain.repository.ThemesRepository

class ThemesRepositoryImpl(
    private val preferences: DataStore<Preferences>,
) : ThemesRepository {
    private val THEME_KEY = stringPreferencesKey("app_theme")
    private val AMOLED_KEY = booleanPreferencesKey("amoled_theme")
    private val IS_DARK_THEME_KEY = booleanPreferencesKey("is_dark_theme")
    private val FONT_KEY = stringPreferencesKey("font_theme")
    private val AUTO_DETECT_CLIPBOARD_KEY = booleanPreferencesKey("auto_detect_clipboard_links")
    private val INSTALLER_TYPE_KEY = stringPreferencesKey("installer_type")
    private val AUTO_UPDATE_KEY = booleanPreferencesKey("auto_update_enabled")

    override fun getThemeColor(): Flow<AppTheme> =
        preferences.data.map { prefs ->
            val themeName = prefs[THEME_KEY]
            AppTheme.fromName(themeName)
        }

    override suspend fun setThemeColor(theme: AppTheme) {
        preferences.edit { prefs ->
            prefs[THEME_KEY] = theme.name
        }
    }

    override fun getIsDarkTheme(): Flow<Boolean?> =
        preferences.data.map { prefs ->
            prefs[IS_DARK_THEME_KEY]
        }

    override suspend fun setDarkTheme(isDarkTheme: Boolean?) {
        preferences.edit { prefs ->
            if (isDarkTheme == null) {
                prefs.remove(IS_DARK_THEME_KEY)
            } else {
                prefs[IS_DARK_THEME_KEY] = isDarkTheme
            }
        }
    }

    override fun getAmoledTheme(): Flow<Boolean> =
        preferences.data.map { prefs ->
            prefs[AMOLED_KEY] ?: false
        }

    override suspend fun setAmoledTheme(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[AMOLED_KEY] = enabled
        }
    }

    override fun getFontTheme(): Flow<FontTheme> =
        preferences.data.map { prefs ->
            val fontName = prefs[FONT_KEY]
            FontTheme.fromName(fontName)
        }

    override suspend fun setFontTheme(fontTheme: FontTheme) {
        preferences.edit { prefs ->
            prefs[FONT_KEY] = fontTheme.name
        }
    }

    override fun getAutoDetectClipboardLinks(): Flow<Boolean> =
        preferences.data.map { prefs ->
            prefs[AUTO_DETECT_CLIPBOARD_KEY] ?: false
        }

    override suspend fun setAutoDetectClipboardLinks(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[AUTO_DETECT_CLIPBOARD_KEY] = enabled
        }
    }

    override fun getInstallerType(): Flow<InstallerType> {
        return preferences.data.map { prefs ->
            val name = prefs[INSTALLER_TYPE_KEY]
            InstallerType.fromName(name)
        }
    }

    override suspend fun setInstallerType(type: InstallerType) {
        preferences.edit { prefs ->
            prefs[INSTALLER_TYPE_KEY] = type.name
        }
    }

    override fun getAutoUpdateEnabled(): Flow<Boolean> =
        preferences.data.map { prefs ->
            prefs[AUTO_UPDATE_KEY] ?: false
        }

    override suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[AUTO_UPDATE_KEY] = enabled
        }
    }
}
