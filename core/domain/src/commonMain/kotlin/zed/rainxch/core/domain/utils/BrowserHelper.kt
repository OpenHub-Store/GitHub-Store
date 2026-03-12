package zed.rainxch.core.domain.utils

interface BrowserHelper {
    fun openUrl(
        url: String,
        onFailure: (error: String) -> Unit = { },
    )
}
