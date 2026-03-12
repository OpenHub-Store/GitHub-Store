package zed.rainxch.core.presentation.utils

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.AppTheme
import zed.rainxch.core.domain.model.AppTheme.AMBER
import zed.rainxch.core.domain.model.AppTheme.DYNAMIC
import zed.rainxch.core.domain.model.AppTheme.FOREST
import zed.rainxch.core.domain.model.AppTheme.OCEAN
import zed.rainxch.core.domain.model.AppTheme.PURPLE
import zed.rainxch.core.domain.model.AppTheme.SLATE
import zed.rainxch.core.presentation.theme.amberOrangeDark
import zed.rainxch.core.presentation.theme.amberOrangeLight
import zed.rainxch.core.presentation.theme.deepPurpleDark
import zed.rainxch.core.presentation.theme.deepPurpleLight
import zed.rainxch.core.presentation.theme.forestGreenDark
import zed.rainxch.core.presentation.theme.forestGreenLight
import zed.rainxch.core.presentation.theme.oceanBlueDark
import zed.rainxch.core.presentation.theme.oceanBlueLight
import zed.rainxch.core.presentation.theme.slateGrayDark
import zed.rainxch.core.presentation.theme.slateGrayLight
import zed.rainxch.githubstore.core.presentation.res.*

val AppTheme.lightScheme: ColorScheme?
    get() =
        when (this) {
            DYNAMIC -> null
            OCEAN -> oceanBlueLight
            PURPLE -> deepPurpleLight
            FOREST -> forestGreenLight
            SLATE -> slateGrayLight
            AMBER -> amberOrangeLight
        }

val AppTheme.darkScheme: ColorScheme?
    get() =
        when (this) {
            DYNAMIC -> null
            OCEAN -> oceanBlueDark
            PURPLE -> deepPurpleDark
            FOREST -> forestGreenDark
            SLATE -> slateGrayDark
            AMBER -> amberOrangeDark
        }

val AppTheme.primaryColor: Color?
    get() =
        when (this) {
            DYNAMIC -> null
            OCEAN -> Color(0xFF2A638A)
            PURPLE -> Color(0xFF6750A4)
            FOREST -> Color(0xFF356859)
            SLATE -> Color(0xFF535E6C)
            AMBER -> Color(0xFF8B5000)
        }

val AppTheme.displayName: String
    @Composable
    get() =
        stringResource(
            when (this) {
                DYNAMIC -> Res.string.theme_dynamic
                OCEAN -> Res.string.theme_ocean
                PURPLE -> Res.string.theme_purple
                FOREST -> Res.string.theme_forest
                SLATE -> Res.string.theme_slate
                AMBER -> Res.string.theme_amber
            },
        )
