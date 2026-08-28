package zed.rainxch.core.data.local.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import zed.rainxch.core.domain.model.installation.InstallSource

@Entity(tableName = "installed_apps")
data class InstalledAppEntity(
    @PrimaryKey val packageName: String,
    val repoId: Long,
    val repoName: String,
    val repoOwner: String,
    val repoOwnerAvatarUrl: String,
    val repoDescription: String?,
    val primaryLanguage: String?,
    val repoUrl: String,
    val installedVersion: String,
    val installedAssetName: String?,
    val installedAssetUrl: String?,
    val latestVersion: String?,
    val latestAssetName: String?,
    val latestAssetUrl: String?,
    val latestAssetSize: Long?,
    val appName: String,
    val installSource: InstallSource,
    val signingFingerprint: String?,
    val installedAt: Long,
    val lastCheckedAt: Long,
    val lastUpdatedAt: Long,
    val isUpdateAvailable: Boolean,
    val updateCheckEnabled: Boolean = true,
    val releaseNotes: String? = "",
    val systemArchitecture: String,
    val fileExtension: String,
    val isPendingInstall: Boolean = false,
    val installedVersionName: String? = null,
    val installedVersionCode: Long = 0L,
    val latestVersionName: String? = null,
    val latestVersionCode: Long? = null,
    val latestReleasePublishedAt: String? = null,
    val includePreReleases: Boolean = false,

    val assetFilterRegex: String? = null,

    @ColumnInfo(defaultValue = "0")
    val fallbackToOlderReleases: Boolean = false,

    val preferredAssetVariant: String? = null,

    @ColumnInfo(defaultValue = "0")
    val preferredVariantStale: Boolean = false,

    val preferredAssetTokens: String? = null,

    val assetGlobPattern: String? = null,

    val pickedAssetIndex: Int? = null,

    val pickedAssetSiblingCount: Int? = null,

    val pendingInstallFilePath: String? = null,

    val pendingInstallVersion: String? = null,

    val pendingInstallAssetName: String? = null,

    @ColumnInfo(defaultValue = "NULL")
    val skippedReleaseTag: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val sourceHost: String? = null,
)
