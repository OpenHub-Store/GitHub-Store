package zed.rainxch.core.domain.utils

interface ShareManager {
    fun shareText(text: String)
    fun shareFile(fileName: String, content: String, mimeType: String = "application/json")
    fun pickFile(mimeType: String = "application/json", onResult: (String?) -> Unit)
}
