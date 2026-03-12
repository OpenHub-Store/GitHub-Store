package zed.rainxch.core.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

val primaryLight = Color(0xFF2A638A)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFCBE6FF)
val onPrimaryContainerLight = Color(0xFF034B71)
val secondaryLight = Color(0xFF50606F)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFD4E4F6)
val onSecondaryContainerLight = Color(0xFF394856)
val tertiaryLight = Color(0xFF66587B)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFECDCFF)
val onTertiaryContainerLight = Color(0xFF4E4162)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF93000A)
val backgroundLight = Color(0xFFF7F9FF)
val onBackgroundLight = Color(0xFF181C20)
val surfaceLight = Color(0xFFF7F9FF)
val onSurfaceLight = Color(0xFF181C20)
val surfaceVariantLight = Color(0xFFDEE3EA)
val onSurfaceVariantLight = Color(0xFF42474D)
val outlineLight = Color(0xFF72787E)
val outlineVariantLight = Color(0xFFC1C7CE)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2D3135)
val inverseOnSurfaceLight = Color(0xFFEEF1F6)
val inversePrimaryLight = Color(0xFF98CCF9)
val surfaceDimLight = Color(0xFFD7DADF)
val surfaceBrightLight = Color(0xFFF7F9FF)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF1F4F9)
val surfaceContainerLight = Color(0xFFEBEEF3)
val surfaceContainerHighLight = Color(0xFFE6E8EE)
val surfaceContainerHighestLight = Color(0xFFE0E3E8)

val primaryDark = Color(0xFF98CCF9)
val onPrimaryDark = Color(0xFF003350)
val primaryContainerDark = Color(0xFF034B71)
val onPrimaryContainerDark = Color(0xFFCBE6FF)
val secondaryDark = Color(0xFFB8C8D9)
val onSecondaryDark = Color(0xFF22323F)
val secondaryContainerDark = Color(0xFF394856)
val onSecondaryContainerDark = Color(0xFFD4E4F6)
val tertiaryDark = Color(0xFFD1BFE7)
val onTertiaryDark = Color(0xFF372B4A)
val tertiaryContainerDark = Color(0xFF4E4162)
val onTertiaryContainerDark = Color(0xFFECDCFF)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF101417)
val onBackgroundDark = Color(0xFFE0E3E8)
val surfaceDark = Color(0xFF101417)
val onSurfaceDark = Color(0xFFE0E3E8)
val surfaceVariantDark = Color(0xFF42474D)
val onSurfaceVariantDark = Color(0xFFC1C7CE)
val outlineDark = Color(0xFF8C9198)
val outlineVariantDark = Color(0xFF42474D)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFE0E3E8)
val inverseOnSurfaceDark = Color(0xFF2D3135)
val inversePrimaryDark = Color(0xFF2A638A)
val surfaceDimDark = Color(0xFF101417)
val surfaceBrightDark = Color(0xFF363A3E)
val surfaceContainerLowestDark = Color(0xFF0B0F12)
val surfaceContainerLowDark = Color(0xFF181C20)
val surfaceContainerDark = Color(0xFF1C2024)
val surfaceContainerHighDark = Color(0xFF272A2E)
val surfaceContainerHighestDark = Color(0xFF313539)

fun ColorScheme.toAmoled(): ColorScheme =
    this.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainer = Color(0xFF0A0A0A),
        surfaceContainerLow = Color(0xFF050505),
        surfaceContainerLowest = Color.Black,
        surfaceContainerHigh = Color(0xFF121212),
        surfaceContainerHighest = Color(0xFF1A1A1A),
        surfaceDim = Color(0xFF0D0D0D),
        surfaceBright = Color(0xFF1F1F1F),
        surfaceVariant = Color(0xFF121212),
    )
