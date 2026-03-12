package zed.rainxch.core.data.utils

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import zed.rainxch.core.domain.utils.BrowserHelper

class AndroidBrowserHelper(
    private val context: Context,
) : BrowserHelper {
    override fun openUrl(
        url: String,
        onFailure: (error: String) -> Unit,
    ) {
        val intent =
            Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }
}
