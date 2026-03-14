package zed.rainxch.core.data.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import zed.rainxch.core.domain.utils.ShareManager
import java.io.File

class AndroidShareManager(
    private val context: Context,
) : ShareManager {
    private var filePickerCallback: ((String?) -> Unit)? = null
    private var filePickerLauncher: ActivityResultLauncher<Intent>? = null

    fun registerActivityResultLauncher(activity: ComponentActivity) {
        filePickerLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callback = filePickerCallback
            filePickerCallback = null

            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    try {
                        val content = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                        callback?.invoke(content)
                    } catch (e: Exception) {
                        callback?.invoke(null)
                    }
                } else {
                    callback?.invoke(null)
                }
            } else {
                callback?.invoke(null)
            }
        }
    }

    override fun shareText(text: String) {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }

        val chooser =
            Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        context.startActivity(chooser)
    }

    override fun shareFile(fileName: String, content: String, mimeType: String) {
        val cacheDir = File(context.cacheDir, "exports")
        cacheDir.mkdirs()

        val file = File(cacheDir, fileName)
        file.writeText(content)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooser)
    }

    override fun pickFile(mimeType: String, onResult: (String?) -> Unit) {
        filePickerCallback = onResult

        val launcher = filePickerLauncher
        if (launcher != null) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
            }
            launcher.launch(intent)
        } else {
            // Fallback: try with ACTION_GET_CONTENT
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(Intent.createChooser(intent, null).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                // Note: fallback won't deliver result without launcher
                onResult(null)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }
}
