package zed.rainxch.core.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import zed.rainxch.core.domain.model.FontTheme
import zed.rainxch.githubstore.core.presentation.res.*

val jetbrainsMonoFontFamily
    @Composable get() =
        FontFamily(
            Font(Res.font.jetbrains_mono_light, FontWeight.Light),
            Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
            Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
            Font(Res.font.jetbrains_mono_semi_bold, FontWeight.SemiBold),
            Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
        )

val interFontFamily
    @Composable get() =
        FontFamily(
            Font(Res.font.inter_light, FontWeight.Light),
            Font(Res.font.inter_regular, FontWeight.Normal),
            Font(Res.font.inter_medium, FontWeight.Medium),
            Font(Res.font.inter_semi_bold, FontWeight.SemiBold),
            Font(Res.font.inter_bold, FontWeight.Bold),
            Font(Res.font.inter_black, FontWeight.Black),
        )

val baseline = Typography()

@Composable
fun getAppTypography(fontTheme: FontTheme = FontTheme.CUSTOM): Typography =
    when (fontTheme) {
        FontTheme.SYSTEM -> {
            baseline
        }

        FontTheme.CUSTOM -> {
            Typography(
                displayLarge = baseline.displayLarge.copy(fontFamily = jetbrainsMonoFontFamily),
                displayMedium = baseline.displayMedium.copy(fontFamily = jetbrainsMonoFontFamily),
                displaySmall = baseline.displaySmall.copy(fontFamily = jetbrainsMonoFontFamily),
                headlineLarge = baseline.headlineLarge.copy(fontFamily = jetbrainsMonoFontFamily),
                headlineMedium = baseline.headlineMedium.copy(fontFamily = jetbrainsMonoFontFamily),
                headlineSmall = baseline.headlineSmall.copy(fontFamily = jetbrainsMonoFontFamily),
                titleLarge = baseline.titleLarge.copy(fontFamily = jetbrainsMonoFontFamily),
                titleMedium = baseline.titleMedium.copy(fontFamily = jetbrainsMonoFontFamily),
                titleSmall = baseline.titleSmall.copy(fontFamily = jetbrainsMonoFontFamily),
                bodyLarge = baseline.bodyLarge.copy(fontFamily = interFontFamily),
                bodyMedium = baseline.bodyMedium.copy(fontFamily = interFontFamily),
                bodySmall = baseline.bodySmall.copy(fontFamily = interFontFamily),
                labelLarge = baseline.labelLarge.copy(fontFamily = interFontFamily),
                labelMedium = baseline.labelMedium.copy(fontFamily = interFontFamily),
                labelSmall = baseline.labelSmall.copy(fontFamily = interFontFamily),
            )
        }
    }
