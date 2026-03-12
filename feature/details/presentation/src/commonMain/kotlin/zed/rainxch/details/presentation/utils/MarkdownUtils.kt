package zed.rainxch.details.presentation.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography

@Composable
fun rememberMarkdownColors(): MarkdownColors {
    val colorScheme = MaterialTheme.colorScheme

    return DefaultMarkdownColors(
        text = colorScheme.onBackground,
        codeBackground = colorScheme.surfaceVariant.copy(alpha = 0.5f),
        inlineCodeBackground = colorScheme.surfaceVariant.copy(alpha = 0.5f),
        dividerColor = colorScheme.outlineVariant,
        tableBackground = colorScheme.surface,
    )
}

@Composable
fun rememberMarkdownTypography(): MarkdownTypography {
    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme

    return DefaultMarkdownTypography(
        h1 = typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        h2 = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        h3 = typography.titleMedium.copy(fontWeight = FontWeight.Medium),
        h4 = typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        h5 = typography.titleSmall.copy(fontWeight = FontWeight.Normal),
        h6 = typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        text = typography.bodyLarge,
        code =
            typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurfaceVariant,
            ),
        inlineCode =
            typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurfaceVariant,
            ),
        quote =
            typography.bodyLarge.copy(
                fontStyle = FontStyle.Italic,
                color = colorScheme.onSurfaceVariant,
            ),
        paragraph = typography.bodyLarge,
        ordered = typography.bodyLarge,
        bullet = typography.bodyLarge,
        list = typography.bodyLarge,
        textLink =
            TextLinkStyles(
                style =
                    SpanStyle(
                        color = colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
            ),
        table = typography.bodyMedium,
    )
}
