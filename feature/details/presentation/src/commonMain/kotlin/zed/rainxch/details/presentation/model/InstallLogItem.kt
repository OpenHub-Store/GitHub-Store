package zed.rainxch.details.presentation.model

data class InstallLogItem(
    val timeIso: String,
    val assetName: String,
    val assetSizeBytes: Long,
    val releaseTag: String,
    val result: LogResult,
)
