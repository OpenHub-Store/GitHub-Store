package zed.rainxch.starred.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.ExpressiveCard
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.core.presentation.utils.formatCount
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.starred.presentation.model.StarredRepositoryUi

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StarredRepositoryItem(
    repository: StarredRepositoryUi,
    onToggleFavoriteClick: () -> Unit,
    onItemClick: () -> Unit,
    onDevProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = onItemClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoilImage(
                    imageModel = { repository.repoOwnerAvatarUrl },
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = {
                                onDevProfileClick()
                            }),
                    imageOptions =
                        ImageOptions(
                            contentScale = ContentScale.Crop,
                        ),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(onClick = {
                                onDevProfileClick()
                            }),
                ) {
                    Text(
                        text = repository.repoName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = repository.repoOwner,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FilledIconToggleButton(
                    checked = repository.isFavorite,
                    onCheckedChange = { onToggleFavoriteClick() },
                    modifier = Modifier.size(40.dp),
                    shape = MaterialShapes.Cookie6Sided.toShape(),
                ) {
                    Icon(
                        imageVector =
                            if (repository.isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                        contentDescription =
                            if (repository.isFavorite) {
                                stringResource(Res.string.remove_from_favourites)
                            } else {
                                stringResource(Res.string.add_to_favourites)
                            },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            repository.repoDescription?.let { description ->
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatChip(
                    icon = Icons.Default.Star,
                    label = formatCount(repository.stargazersCount),
                    contentDescription = "${repository.stargazersCount} ${stringResource(Res.string.stars)}",
                )

                StatChip(
                    icon = Icons.AutoMirrored.Filled.CallSplit,
                    label = formatCount(repository.forksCount),
                    contentDescription = "${repository.forksCount} ${stringResource(Res.string.forks)}",
                )

                if (repository.openIssuesCount > 0) {
                    StatChip(
                        icon = Icons.Outlined.Warning,
                        label = formatCount(repository.openIssuesCount),
                        contentDescription = "${repository.openIssuesCount} ${stringResource(Res.string.issues)}",
                    )
                }

                repository.primaryLanguage?.let { language ->
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = language,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.height(32.dp),
                    )
                }
            }

            if (repository.isInstalled || repository.latestRelease != null) {
                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (repository.isInstalled) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = stringResource(Res.string.installed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    repository.latestRelease?.let { version ->
                        Badge(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = version,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun PreviewStarredRepoItem() {
    GithubStoreTheme {
        StarredRepositoryItem(
            repository =
                StarredRepositoryUi(
                    repoId = 1,
                    repoName = "awesome-app",
                    repoOwner = "developer",
                    repoOwnerAvatarUrl = "",
                    repoDescription = "An awesome application that does amazing things",
                    primaryLanguage = "Kotlin",
                    repoUrl = "",
                    stargazersCount = 1234,
                    forksCount = 567,
                    openIssuesCount = 12,
                    isInstalled = true,
                    isFavorite = false,
                    latestRelease = "v1.2.3",
                    latestReleaseUrl = null,
                    starredAt = null,
                ),
            onToggleFavoriteClick = {},
            onItemClick = {},
            onDevProfileClick = {},
        )
    }
}
