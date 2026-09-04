package zed.rainxch.details.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatCount
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun StatItem(
    label: String,
    stat: Int,
    modifier: Modifier = Modifier,
) {
    StatItem(label = label, stat = stat.toLong(), modifier = modifier)
}

@Composable
fun StatItem(
    label: String,
    stat: Long,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    Column(
        modifier = modifier
            .clip(rowShape)
            .border(width = 1.dp, color = colors.outline, shape = rowShape)
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        KomiText(
            text = label,
            role = KomiTextRole.Label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            uppercase = false,
        )
        KomiText(
            text = formatCount(stat),
            role = KomiTextRole.Title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            uppercase = false,
            
        )
    }
}

@Composable
fun TextStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    Column(
        modifier = modifier
            .clip(rowShape)
            .border(width = 1.dp, color = colors.outline, shape = rowShape)
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        KomiText(
            text = label,
            role = KomiTextRole.Label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            uppercase = false,
        )
        KomiText(
            text = value,
            role = KomiTextRole.Title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            uppercase = false,
        )
    }
}
