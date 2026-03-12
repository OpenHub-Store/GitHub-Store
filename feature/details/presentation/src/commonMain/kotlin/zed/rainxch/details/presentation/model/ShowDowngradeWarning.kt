package zed.rainxch.details.presentation.model

data class DowngradeWarning(
    val packageName: String,
    val currentVersion: String,
    val targetVersion: String,
)
