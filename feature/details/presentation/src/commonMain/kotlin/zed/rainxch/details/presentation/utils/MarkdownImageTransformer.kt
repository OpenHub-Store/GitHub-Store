package zed.rainxch.details.presentation.utils

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.rememberAsyncImagePainter
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer

object MarkdownImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        if (link.isBlank()) {
            return null
        }

        val normalizedLink =
            if (link.contains("github.com") && link.contains("/blob/")) {
                link
                    .replace("github.com", "raw.githubusercontent.com")
                    .replace("/blob/", "/")
            } else {
                link
            }

        if (normalizedLink.endsWith(".svg", ignoreCase = true) ||
            normalizedLink.contains(".svg?", ignoreCase = true) ||
            normalizedLink.contains(".svg#", ignoreCase = true)
        ) {
            return null
        }

        if (!normalizedLink.startsWith("http://") &&
            !normalizedLink.startsWith("https://") &&
            !normalizedLink.startsWith("data:")
        ) {
            return null
        }

        val painter =
            rememberAsyncImagePainter(
                model = normalizedLink,
            )

        return ImageData(
            painter = painter,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Image",
            contentScale = ContentScale.Fit,
        )
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size = painter.intrinsicSize
}
