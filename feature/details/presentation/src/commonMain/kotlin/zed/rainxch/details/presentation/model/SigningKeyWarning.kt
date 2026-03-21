package zed.rainxch.details.presentation.model

import zed.rainxch.core.domain.model.ApkPackageInfo

data class SigningKeyWarning(
    val packageName: String,
    val expectedFingerprint: String,
    val actualFingerprint: String,
    val pendingDownloadUrl: String,
    val pendingAssetName: String,
    val pendingSizeBytes: Long,
    val pendingReleaseTag: String,
    val pendingIsUpdate: Boolean,
    val pendingFilePath: String,
    val pendingApkInfo: ApkPackageInfo,
)
