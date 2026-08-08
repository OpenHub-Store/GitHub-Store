package zed.rainxch.core.presentation.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import zed.rainxch.core.presentation.components.GitHubStoreImage
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.utils.formatReleasedAgo
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.core.presentation.components.surfaces.KomiScreentone
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.StarburstShape
import zed.rainxch.core.presentation.personality.manga.decoration.halftoneOverlay
import zed.rainxch.core.presentation.personality.manga.decoration.screentoneFill
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.model.PersonalityColors
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.core.presentation.utils.toIcon
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KomiRepoCard(
    name: String,
    owner: String,
    language: String,
    description: String,
    platforms: ImmutableList<DiscoveryPlatform>,
    stars: Int,
    downloads: Int,
    releasedAgoDays: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    monogram: String? = null,
    imageUrl: String? = null,
    feed: KomiRepoCardFeed = KomiRepoCardFeed.Plain,
    rank: Int = 1,
    version: String? = null,
    dailyStars: Int = 0,
    compact: Boolean = false,
    index: Int = 0,
    tilt: Boolean = false,
    releasedAt: String? = null,
) {
    val colors = LocalPersonality.current.colors
    val tiltDeg =
        if (tilt) {
            if (index % 2 != 0) 0.4f else -0.4f
        } else {
            0f
        }
    val gap = if (compact) 9.dp else 11.dp

    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        elevation = KomiSurfaceElevation.Card,
        screentone = KomiScreentone.Corner,
        onClick = onOpen,
        onLongClick = onLongPress,
        tilt = tiltDeg,
        contentPadding = PaddingValues(if (compact) 13.dp else 15.dp),
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CardIconTile(
                    monogram = monogram ?: name.take(2),
                    imageUrl = imageUrl,
                    size = if (compact) 52.dp else 60.dp,
                    colors = colors
                )
                Column(modifier = Modifier.weight(1f)) {
                    KomiText(
                        text = name,
                        role = KomiTextRole.Title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.padding(top = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KomiText(
                            text = "@$owner",
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W600,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Diamond(color = colors.onSurfaceVariant)
                        KomiText(
                            text = language,
                            role = KomiTextRole.Body,
                            color = colors.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W800,
                            maxLines = 1,
                        )
                    }
                }
                CardBadge(
                    feed = feed,
                    rank = rank,
                    dailyStars = dailyStars,
                    version = version,
                    releasedAgoDays = releasedAgoDays,
                    colors = colors,
                )
            }

            KomiText(
                text = description,
                role = KomiTextRole.Body,
                color = colors.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.W500,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = gap),
            )

            if (!compact && platforms.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = gap),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    platforms.forEach { platform ->
                        KomiChip(
                            label = platform.toLabel(),
                            kind = KomiChipKind.Info,
                            size = if (compact) KomiChipSize.Sm else KomiChipSize.Md,
                            leadingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    platform.toIcon()?.let { icon ->
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = colors.onSurface,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = if (compact) 9.dp else 12.dp),
                thickness = 2.dp,
                color = colors.outline.copy(alpha = 0.3f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = if (compact) 9.dp else 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Stat(
                        icon = Icons.Filled.Star,
                        value = fmtCompact(stars),
                        colors = colors,
                        hero = true
                    )
                    Stat(
                        icon = Icons.Outlined.Download,
                        value = fmtCompact(downloads),
                        colors = colors
                    )
                    if (!compact) {
                        formatReleasedAgo(releasedAt)?.let { ago ->
                            Stat(
                                icon = Icons.Outlined.Schedule,
                                value = ago,
                                colors = colors
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!compact) {
                        KomiText(
                            text = "Read",
                            role = KomiTextRole.Label,
                            color = colors.onSurface,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.onSurface,
                    )
                }
            }
        }
    }
}

private fun inkAvatarFilter(dark: Boolean): ColorFilter {
    val saturation = 0.15f
    val contrast = if (dark) 1.05f else 1.15f
    val brightness = if (dark) 0.95f else 1.02f
    val keep = 1f - saturation
    val cb = contrast * brightness
    val t = (-.5f * contrast + .5f) * 255f
    val lr = 0.299f
    val lg = 0.587f
    val lb = 0.114f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                cb * (lr * keep + saturation), cb * (lg * keep), cb * (lb * keep), 0f, t,
                cb * (lr * keep), cb * (lg * keep + saturation), cb * (lb * keep), 0f, t,
                cb * (lr * keep), cb * (lg * keep), cb * (lb * keep + saturation), 0f, t,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

private val InkAvatarFilterDay = inkAvatarFilter(dark = false)
private val InkAvatarFilterNight = inkAvatarFilter(dark = true)

@Composable
private fun CardIconTile(
    monogram: String,
    imageUrl: String?,
    size: Dp,
    colors: PersonalityColors,
) {
    val hasImage = !imageUrl.isNullOrBlank()
    when (LocalPersonality.current) {
        is MangaPersonality ->
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clipToBounds()
                        .background(color = colors.surface)
                        .then(
                            if (hasImage) {
                                Modifier
                            } else {
                                Modifier.screentoneFill(
                                    color = colors.onSurface,
                                    opacity = colors.screentoneOpacity + 0.06f,
                                )
                            },
                        ).border(width = 2.5.dp, color = colors.outline),
                contentAlignment = Alignment.Center,
            ) {
                if (hasImage) {
                    GitHubStoreImage(
                        imageModel = { imageUrl },
                        modifier =
                            Modifier
                                .matchParentSize()
                                .halftoneOverlay(
                                    color = colors.onSurface,
                                    opacity = colors.screentoneOpacity + 0.06f,
                                ),
                        colorFilter = if (colors.isDark) InkAvatarFilterNight else InkAvatarFilterDay,
                    )
                } else {
                    KomiText(
                        text = monogram.uppercase(),
                        role = KomiTextRole.Title,
                        color = colors.onSurface,
                        fontSize = (size.value * 0.46f).sp,
                        maxLines = 1,
                    )
                }
            }

        is ClassicPersonality ->
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clip(RoundedCornerShape(14.dp))
                        .background(color = colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (hasImage) {
                    GitHubStoreImage(
                        imageModel = { imageUrl },
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    KomiText(
                        text = monogram.uppercase(),
                        role = KomiTextRole.Title,
                        color = colors.onSurface,
                        fontSize = (size.value * 0.42f).sp,
                        maxLines = 1,
                    )
                }
            }
    }
}

@Composable
private fun CardBadge(
    feed: KomiRepoCardFeed,
    rank: Int,
    dailyStars: Int,
    version: String?,
    releasedAgoDays: Int,
    colors: PersonalityColors,
) {
    if (feed == KomiRepoCardFeed.Plain) return
    when (LocalPersonality.current) {
        is MangaPersonality ->
            when (feed) {
                KomiRepoCardFeed.Popular -> RankStamp(rank = rank, colors = colors)
                KomiRepoCardFeed.Trending -> if (dailyStars > 0) {
                    BurstBadge(text = "+${fmtCompact(dailyStars)}", colors = colors)
                }

                KomiRepoCardFeed.Release -> NewReleaseStamp(
                    version = version,
                    showStamp = releasedAgoDays <= 7,
                    colors = colors
                )

                KomiRepoCardFeed.Plain -> Unit
            }

        is ClassicPersonality ->
            ClassicBadge(
                feed = feed,
                rank = rank,
                dailyStars = dailyStars,
                releasedAgoDays = releasedAgoDays,
                colors = colors
            )
    }
}

@Composable
private fun RankStamp(
    rank: Int,
    colors: PersonalityColors,
) {
    Box(modifier = Modifier.size(46.dp).rotate(-8f), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(colors.primary)
                    .border(width = 2.5.dp, color = colors.outline, shape = CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(
                        3.dp,
                    ).border(
                        width = 1.5.dp,
                        color = colors.onPrimary.copy(alpha = 0.55f),
                        shape = CircleShape
                    ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KomiText(
                text = "NO.",
                role = KomiTextRole.Label,
                color = colors.onPrimary,
                fontSize = 8.sp
            )
            KomiText(
                text = rank.toString().padStart(2, '0'),
                role = KomiTextRole.Title,
                color = colors.onPrimary,
                fontSize = 21.sp
            )
        }
    }
}

@Composable
private fun BurstBadge(
    text: String,
    colors: PersonalityColors,
) {
    Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(color = colors.primary, shape = StarburstShape())
                    .border(width = 2.dp, color = colors.outline, shape = StarburstShape()),
        )
        KomiText(
            text = text,
            role = KomiTextRole.Title,
            color = colors.onPrimary,
            fontSize = 13.sp,
            modifier = Modifier.rotate(-6f)
        )
    }
}

@Composable
private fun NewReleaseStamp(
    version: String?,
    showStamp: Boolean,
    colors: PersonalityColors,
) {
    if (!showStamp) return
    Column(horizontalAlignment = Alignment.End) {
        Box(
            modifier =
                Modifier
                    .rotate(2f)
                    .background(color = colors.primary)
                    .border(width = 2.dp, color = colors.outline)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            KomiText(
                text = "New Release",
                role = KomiTextRole.Label,
                color = colors.onPrimary,
                fontSize = 10.5.sp
            )
        }
        if (version != null) {
            KomiText(
                text = "v$version",
                role = KomiTextRole.Title,
                color = colors.onSurface,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ClassicBadge(
    feed: KomiRepoCardFeed,
    rank: Int,
    dailyStars: Int,
    releasedAgoDays: Int,
    colors: PersonalityColors,
) {
    val text =
        when (feed) {
            KomiRepoCardFeed.Popular -> "No. $rank"
            KomiRepoCardFeed.Trending -> "+${fmtCompact(dailyStars)}"
            KomiRepoCardFeed.Release -> "New"
            KomiRepoCardFeed.Plain -> ""
        }
    val show = (feed != KomiRepoCardFeed.Release || releasedAgoDays <= 7) &&
        (feed != KomiRepoCardFeed.Trending || dailyStars > 0)
    if (text.isNotEmpty() && show) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color = colors.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            KomiText(
                text = text,
                role = KomiTextRole.Label,
                color = colors.onPrimaryContainer,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun Stat(
    icon: ImageVector,
    value: String,
    colors: PersonalityColors,
    hero: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (hero) colors.primary else colors.onSurface,
        )
        KomiText(
            text = value,
            role = KomiTextRole.Body,
            color = colors.onSurface,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.W800,
            maxLines = 1,
        )
    }
}

@Composable
private fun Diamond(color: Color) {
    Box(modifier = Modifier.size(4.dp).rotate(45f).background(color))
}

private fun fmtCompact(n: Int): String =
    when {
        n >= 1_000_000 -> shorten(n / 1_000_000.0, n >= 10_000_000) + "M"
        n >= 1_000 -> shorten(n / 1_000.0, n >= 10_000) + "k"
        else -> n.toString()
    }

private fun shorten(
    value: Double,
    zeroDecimals: Boolean,
): String {
    if (zeroDecimals) return value.roundToInt().toString()
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong()
        .toString() else rounded.toString()
}

@Composable
private fun PreviewCards() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        KomiRepoCard(
            name = "immich",
            owner = "immich-app",
            language = "TypeScript",
            description = "High-performance self-hosted photo and video management. Back up and browse your library from any device.",
            platforms = persistentListOf(DiscoveryPlatform.Android),
            stars = 102900,
            downloads = 4946278,
            releasedAgoDays = 4,
            feed = KomiRepoCardFeed.Popular,
            rank = 1,
            dailyStars = 1820,
            onOpen = {},
        )
        KomiRepoCard(
            name = "localsend",
            owner = "localsend",
            language = "Dart",
            description = "Open-source AirDrop alternative — share files to nearby devices over your local network.",
            platforms = persistentListOf(
                DiscoveryPlatform.Android,
                DiscoveryPlatform.Windows,
                DiscoveryPlatform.Macos,
                DiscoveryPlatform.Linux
            ),
            stars = 62100,
            downloads = 8330000,
            releasedAgoDays = 5,
            feed = KomiRepoCardFeed.Trending,
            dailyStars = 980,
            version = "1.17.0",
            onOpen = {},
        )
        KomiRepoCard(
            name = "jellyfin",
            owner = "jellyfin",
            language = "C#",
            description = "The free software media system that puts you in control of streaming your media.",
            platforms = persistentListOf(DiscoveryPlatform.Android, DiscoveryPlatform.Linux),
            stars = 38400,
            downloads = 12040000,
            releasedAgoDays = 3,
            feed = KomiRepoCardFeed.Release,
            version = "10.9.11",
            onOpen = {},
        )
    }
}

@Preview
@Composable
private fun KomiRepoCardMangaPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewCards() }
}

@Preview
@Composable
private fun KomiRepoCardMangaNightPreview() {
    PersonalityPreview(
        mangaPersonality(
            paper = MangaPaper.NIGHT,
            accent = MangaAccent.SUN
        )
    ) { PreviewCards() }
}

@Preview
@Composable
private fun KomiRepoCardClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewCards() }
}
