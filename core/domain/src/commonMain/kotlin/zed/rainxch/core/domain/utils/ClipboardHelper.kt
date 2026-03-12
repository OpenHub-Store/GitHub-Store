package zed.rainxch.core.domain.utils

interface ClipboardHelper {
    fun copy(
        label: String,
        text: String,
    )

    fun getText(): String?
}
