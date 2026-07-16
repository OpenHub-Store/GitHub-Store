package zed.rainxch.tweaks.presentation.model

import kotlinx.serialization.Serializable

@Serializable
data class SettingsBackup(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val themeColor: String? = null,
    val isDarkTheme: Boolean? = null,
    val amoledTheme: Boolean? = null,
    val mangaPaper: String? = null,
    val fontTheme: String? = null,
    val personality: String? = null,
    val accentId: String? = null,
    val scrollbarEnabled: Boolean? = null,
    val contentWidth: String? = null,
    val autoDetectClipboardLinks: Boolean? = null,
    val hideSeenEnabled: Boolean? = null,
    val discoveryPlatforms: Set<String>? = null,
    val showAllPlatforms: Boolean? = null,
    val installerType: String? = null,
    val installerAttribution: String? = null,
    val autoUpdateEnabled: Boolean? = null,
    val updateCheckEnabled: Boolean? = null,
    val updateCheckIntervalHours: Long? = null,
    val includePreReleases: Boolean? = null,
    val appLanguage: String? = null,
    val translationProvider: String? = null,
    val youdaoAppKey: String? = null,
    val youdaoAppSecret: String? = null,
    val libreTranslateBaseUrl: String? = null,
    val libreTranslateApiKey: String? = null,
    val deeplAuthKey: String? = null,
    val microsoftTranslatorKey: String? = null,
    val microsoftTranslatorRegion: String? = null,
    val autoTranslateEnabled: Boolean? = null,
    val autoTranslateTargetLang: String? = null,
    val externalImportEnabled: Boolean? = null,
    val externalMatchSearchEnabled: Boolean? = null,
    val appsSortRule: String? = null,
    val starredSortRule: String? = null,
    val favouritesSortRule: String? = null,
    val customForgeHosts: Set<String>? = null,
    val proxyConfigs: Map<String, ProxyEntryBackup>? = null,
    val masterProxyConfig: ProxyEntryBackup? = null,
    val useMasterByScope: Map<String, Boolean>? = null,
)

@Serializable
data class ProxyEntryBackup(
    val type: String,
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val password: String? = null,
)
