package zed.rainxch.core.domain.model

data class DeviceApp(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
)
