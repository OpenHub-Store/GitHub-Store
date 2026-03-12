package zed.rainxch.core.domain.model

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val percent: Int?,
)
