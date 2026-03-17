package zed.rainxch.apps.presentation.model

data class DeviceAppUi(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
    val signingFingerprint: String?,
)
