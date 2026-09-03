package zed.rainxch.details.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.model.account.github.preReleaseLabel
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatIsoDate
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.latest_badge
import zed.rainxch.githubstore.core.presentation.res.no_version_selected
import zed.rainxch.githubstore.core.presentation.res.not_available
import zed.rainxch.githubstore.core.presentation.res.pre_release_badge
import zed.rainxch.githubstore.core.presentation.res.select_version
import zed.rainxch.githubstore.core.presentation.res.versions_title

@Composable
fun VersionPicker(
    selectedRelease: GithubRelease?,
    filteredReleases: ImmutableList<GithubRelease>,
    isPickerVisible: Boolean,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val isPickerEnabled = filteredReleases.isNotEmpty()

    val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    Column(
        modifier = modifier.wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.versions_title),
            role = KomiTextRole.Label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = colors.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .border(
                    width = 1.dp,
                    color = colors.outline,
                    shape = rowShape,
                )
                .background(colors.surface)
                .clickable(enabled = isPickerEnabled) {
                    onAction(DetailsAction.ToggleVersionPicker)
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .heightIn(min = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                KomiText(
                    text = selectedRelease?.tagName
                        ?: stringResource(Res.string.no_version_selected),
                    role = KomiTextRole.Stamp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    overflow = TextOverflow.Clip,
                    maxLines = 1,
                    uppercase = false,
                )
                selectedRelease?.name?.let { name ->
                    if (name != selectedRelease.tagName) {
                        KomiText(
                            text = name,
                            role = KomiTextRole.Body,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            KomiIcon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = stringResource(Res.string.select_version),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (isPickerVisible) {
        KomiSheet(
            onDismiss = { onAction(DetailsAction.ToggleVersionPicker) },
            placement = KomiSheetPlacement.Bottom,
        ) {
            KomiText(
                text = stringResource(Res.string.versions_title),
                role = KomiTextRole.Title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 6.dp),
                uppercase = false,
            )
            Spacer(Modifier.size(8.dp))
            if (filteredReleases.isEmpty()) {
                KomiText(
                    text = stringResource(Res.string.not_available),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                val latestReleaseId = filteredReleases.firstOrNull()?.id
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(items = filteredReleases, key = { it.id }) { release ->
                        VersionListItem(
                            release = release,
                            isSelected = release.id == selectedRelease?.id,
                            isLatest = release.id == latestReleaseId,
                            onClick = { onAction(DetailsAction.SelectRelease(release)) },
                        )
                        KomiHorizontalDivider(
                            color = colors.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionListItem(
    release: GithubRelease,
    isSelected: Boolean,
    isLatest: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(Res.string.select_version),
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors = LocalPersonality.current.colors
        val shape = LocalPersonality.current.shape
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KomiText(
                    text = release.tagName,
                    role = KomiTextRole.Stamp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) colors.primary else colors.onSurface,
                    uppercase = false,
                )
                if (isLatest) {
                    KomiText(
                        text = stringResource(Res.string.latest_badge),
                        role = KomiTextRole.Label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(shape.cornerSmall))
                            .border(
                                width = 1.dp,
                                color = colors.primary,
                                shape = RoundedCornerShape(shape.cornerSmall),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (release.isEffectivelyPreRelease()) {
                    val specificLabel = release.preReleaseLabel()
                    KomiText(
                        text = specificLabel ?: stringResource(Res.string.pre_release_badge),
                        role = KomiTextRole.Label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(shape.cornerSmall))
                            .border(
                                width = 1.dp,
                                color = colors.primary,
                                shape = RoundedCornerShape(shape.cornerSmall),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            release.name?.let { name ->
                if (name != release.tagName) {
                    KomiText(
                        text = name,
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            KomiText(
                text = formatIsoDate(release.publishedAt)
                    ?: release.publishedAt.take(10),
                role = KomiTextRole.Label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
                uppercase = false,
            )
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            KomiIcon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
