package zed.rainxch.core.data.utils

import zed.rainxch.core.domain.utils.ShareManager
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

class DesktopShareManager : ShareManager {
    override fun shareText(text: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(text)
        clipboard.setContents(selection, null)
    }

    override fun shareFile(fileName: String, content: String, mimeType: String) {
        SwingUtilities.invokeLater {
            val chooser = JFileChooser().apply {
                dialogTitle = "Save exported apps"
                selectedFile = File(fileName)
                fileFilter = FileNameExtensionFilter("JSON files", "json")
            }

            val result = chooser.showSaveDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                var file = chooser.selectedFile
                if (!file.name.endsWith(".json")) {
                    file = File(file.absolutePath + ".json")
                }
                file.writeText(content)
            }
        }
    }

    override fun pickFile(mimeType: String, onResult: (String?) -> Unit) {
        SwingUtilities.invokeLater {
            val chooser = JFileChooser().apply {
                dialogTitle = "Select file to import"
                fileFilter = FileNameExtensionFilter("JSON files", "json")
            }

            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                try {
                    val content = chooser.selectedFile.readText()
                    onResult(content)
                } catch (e: Exception) {
                    onResult(null)
                }
            } else {
                onResult(null)
            }
        }
    }
}
