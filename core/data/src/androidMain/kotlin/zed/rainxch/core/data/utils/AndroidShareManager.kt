package zed.rainxch.core.data.utils

import android.content.Context
import android.content.Intent
import zed.rainxch.core.domain.utils.ShareManager

class AndroidShareManager(
    private val context: Context,
) : ShareManager {
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
}
