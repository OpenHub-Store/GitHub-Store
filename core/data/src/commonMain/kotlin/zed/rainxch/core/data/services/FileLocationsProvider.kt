package zed.rainxch.core.data.services

interface FileLocationsProvider {
    fun appDownloadsDir(): String

    fun userDownloadsDir(): String

    fun setExecutableIfNeeded(path: String)

    fun getCacheSizeBytes(): Long

    fun clearCacheFiles(): Boolean
}
