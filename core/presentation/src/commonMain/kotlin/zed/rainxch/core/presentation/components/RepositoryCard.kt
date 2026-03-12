package zed.rainxch.core.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.GithubRepoSummary
import zed.rainxch.core.domain.model.GithubUser
import zed.rainxch.core.presentation.model.DiscoveryRepository
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.core.presentation.utils.formatReleasedAt
import zed.rainxch.core.presentation.utils.hasWeekNotPassed
import zed.rainxch.githubstore.core.presentation.res.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun RepositoryCard(
    discoveryRepository: DiscoveryRepository,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeveloperClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    ExpressiveCard(
        onClick = onClick,
        modifier = modifier,
    ) {
        Box {
            if (discoveryRepository.isFavourite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier =
                        Modifier
                            .size(120.dp)
                            .align(Alignment.BottomStart)
                            .offset(x = (-32).dp, y = 32.dp),
                )
            }

            if (discoveryRepository.isStarred) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                    modifier =
                        Modifier
                            .size(120.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 32.dp, y = (-32).dp),
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier =
                            Modifier.clickable(onClick = {
                                onDeveloperClick(discoveryRepository.repository.owner.login)
                            }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GitHubStoreImage(
                            imageModel = { discoveryRepository.repository.owner.avatarUrl },
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                        )

                        Text(
                            text = discoveryRepository.repository.owner.login,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text = "/ ${discoveryRepository.repository.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = discoveryRepository.repository.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (discoveryRepository.repository.isFork) {
                        ForkBadge()
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                discoveryRepository.repository.description?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        softWrap = true,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "⭐ ${discoveryRepository.repository.stargazersCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = "• 🌴 ${discoveryRepository.repository.forksCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )

                    discoveryRepository.repository.language?.let {
                        Text(
                            text = "• $it",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (discoveryRepository.isInstalled) {
                    Spacer(Modifier.height(12.dp))

                    InstallStatusBadge(
                        isUpdateAvailable = discoveryRepository.isUpdateAvailable,
                    )
                }

                if (discoveryRepository.repository.availablePlatforms.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        discoveryRepository.repository.availablePlatforms.forEach { platform ->
                            PlatformChip(platform = platform)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val releasedAtText =
                    buildAnnotatedString {
                        if (hasWeekNotPassed(discoveryRepository.repository.updatedAt)) {
                            append("🔥 ")
                        }

                        append(formatReleasedAt(discoveryRepository.repository.updatedAt))
                    }

                Text(
                    text = releasedAtText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GithubStoreButton(
                        text = stringResource(Res.string.home_view_details),
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = onShareClick,
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(Res.string.share_repository),
                        )
                    }

                    IconButton(
                        onClick = {
                            uriHandler.openUri(discoveryRepository.repository.htmlUrl)
                        },
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = stringResource(Res.string.open_in_browser),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlatformChip(
    platform: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = platform,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun ForkBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.CallSplit,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(Res.string.forked_repository),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun InstallStatusBadge(
    isUpdateAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (isUpdateAvailable) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }

    val textColor =
        if (isUpdateAvailable) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

    val icon =
        if (isUpdateAvailable) {
            Icons.Default.Update
        } else {
            Icons.Default.CheckCircle
        }

    val text =
        if (isUpdateAvailable) {
            stringResource(Res.string.update_available)
        } else {
            stringResource(Res.string.installed)
        }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = textColor,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Preview
@Composable
fun RepositoryCardPreview() {
    GithubStoreTheme {
        RepositoryCard(
            discoveryRepository =
                DiscoveryRepository(
                    repository =
                        GithubRepoSummary(
                            id = 0L,
                            name = "Hello",
                            fullName = "JIFEOJEF",
                            owner =
                                GithubUser(
                                    id = 0L,
                                    login = "Skydoves",
                                    avatarUrl = "ewfew",
                                    htmlUrl = "grgrre",
                                ),
                            description = "Hello wolrd Hello wolrd Hello wolrd Hello wolrd Hello wolrd",
                            htmlUrl = "",
                            stargazersCount = 20,
                            forksCount = 4,
                            language = "Kotlin",
                            topics = null,
                            releasesUrl = "",
                            updatedAt = "2025-12-01T12:00:00Z",
                            defaultBranch = "",
                        ),
                    isUpdateAvailable = true,
                    isFavourite = true,
                    isInstalled = true,
                    isStarred = false,
                ),
            onClick = { },
            onShareClick = { },
            onDeveloperClick = { },
        )
    }
}
