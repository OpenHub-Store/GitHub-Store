package zed.rainxch.devprofile.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.devprofile.domain.model.RepoFilterType
import zed.rainxch.devprofile.domain.model.RepoSortType
import zed.rainxch.devprofile.presentation.DeveloperProfileAction
import zed.rainxch.githubstore.core.presentation.res.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilterSortControls(
    currentFilter: RepoFilterType,
    currentSort: RepoSortType,
    searchQuery: String,
    repoCount: Int,
    totalCount: Int,
    onAction: (DeveloperProfileAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { query ->
                onAction(DeveloperProfileAction.OnSearchQueryChange(query))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(Res.string.search_repositories),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(Res.string.search_repositories),
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = { onAction(DeveloperProfileAction.OnSearchQueryChange("")) },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.clear_search),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryScrollableTabRow(
                selectedTabIndex = currentFilter.ordinal,
                modifier = Modifier.weight(1f),
                edgePadding = 0.dp,
                divider = {},
            ) {
                RepoFilterType.entries.forEach { filter ->
                    FilterChipTab(
                        selected = currentFilter == filter,
                        onClick = { onAction(DeveloperProfileAction.OnFilterChange(filter)) },
                        label = filter.displayName(),
                    )
                }
            }

            SortMenu(
                currentSort = currentSort,
                onSortChange = { sort ->
                    onAction(DeveloperProfileAction.OnSortChange(sort))
                },
            )
        }

        Text(
            text =
                if (repoCount == totalCount) {
                    "$repoCount ${
                        stringResource(
                            if (repoCount == 1) {
                                Res.string.repository_singular
                            } else {
                                Res.string.repositories
                            },
                        )
                    }"
                } else {
                    stringResource(
                        resource = Res.string.showing_x_of_y_repositories,
                        repoCount,
                        totalCount,
                    )
                },
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterChipTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.height(40.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SortMenu(
    currentSort: RepoSortType,
    onSortChange: (RepoSortType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilledIconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(40.dp),
            shape = MaterialShapes.Cookie9Sided.toShape(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(Res.string.sort),
                modifier = Modifier.size(20.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(32.dp),
        ) {
            RepoSortType.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (currentSort == sort) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Spacer(modifier = Modifier.size(18.dp))
                            }

                            Text(
                                text = sort.displayName(),
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    if (currentSort == sort) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    },
                    onClick = {
                        onSortChange(sort)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RepoFilterType.displayName(): String =
    when (this) {
        RepoFilterType.WITH_RELEASES -> stringResource(Res.string.filter_with_releases)
        RepoFilterType.INSTALLED -> stringResource(Res.string.filter_installed)
        RepoFilterType.FAVORITES -> stringResource(Res.string.filter_favorites)
    }

@Composable
private fun RepoSortType.displayName(): String =
    when (this) {
        RepoSortType.UPDATED -> stringResource(Res.string.sort_recently_updated)
        RepoSortType.STARS -> stringResource(Res.string.sort_most_stars)
        RepoSortType.NAME -> stringResource(Res.string.sort_name)
    }
