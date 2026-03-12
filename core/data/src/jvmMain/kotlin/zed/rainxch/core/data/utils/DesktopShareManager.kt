package zed.rainxch.core.data.utils

import zed.rainxch.core.domain.utils.ShareManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class DesktopShareManager : ShareManager {
    override fun shareText(text: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(text)
        clipboard.setContents(selection, null)
    }
}
