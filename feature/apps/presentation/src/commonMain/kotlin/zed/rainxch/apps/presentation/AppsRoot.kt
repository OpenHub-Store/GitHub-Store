@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package zed.rainxch.apps.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydoves.landscapist.coil3.CoilImage
import io.github.fletchmckee.liquid.liquefiable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.model.UpdateAllProgress
import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.core.presentation.components.ExpressiveCard
import zed.rainxch.core.presentation.locals.LocalBottomNavigationHeight
import zed.rainxch.core.presentation.locals.LocalBottomNavigationLiquid
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun AppsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToRepo: (repoId: Long) -> Unit,
    viewModel: AppsViewModel = koinViewModel(),
    state: AppsState,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AppsEvent.NavigateToRepo -> {
                onNavigateToRepo(event.repoId)
            }

            is AppsEvent.ShowError -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(event.message)
                }
            }

            is AppsEvent.ShowSuccess -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    AppsScreen(
        state = state,
        onAction = { action ->
            when (action) {
                AppsAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun AppsScreen(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val liquidState = LocalBottomNavigationLiquid.current
    val bottomNavHeight = LocalBottomNavigationHeight.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.installed_apps),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    IconButton(
                        onClick = { onAction(AppsAction.OnCheckAllForUpdates) },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.check_for_updates),
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottomNavHeight + 16.dp),
            )
        },
        modifier = Modifier.liquefiable(liquidState),
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(AppsAction.OnRefresh) },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                TextField(
                    value = state.searchQuery,
                    onValueChange = { onAction(AppsAction.OnSearchChange(it)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    placeholder = { Text(stringResource(Res.string.search_your_apps)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = CircleShape,
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                )

                if (state.isCheckingForUpdates) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(Res.string.checking_for_updates),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (state.lastCheckedTimestamp != null) {
                    Text(
                        text =
                            stringResource(
                                Res.string.last_checked,
                                formatLastChecked(state.lastCheckedTimestamp),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                val hasUpdates = state.apps.any { it.installedApp.isUpdateAvailable }
                if (hasUpdates && !state.isUpdatingAll) {
                    Button(
                        onClick = { onAction(AppsAction.OnUpdateAll) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        enabled = state.updateAllButtonEnabled,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = null,
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = stringResource(Res.string.update_all),
                        )
                    }
                }

                if (state.isUpdatingAll && state.updateAllProgress != null) {
                    UpdateAllProgressCard(
                        progress = state.updateAllProgress,
                        onCancel = {
                            onAction(AppsAction.OnCancelUpdateAll)
                        },
                    )
                }

                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.filteredApps.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.no_apps_found),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = state.filteredApps,
                                key = { it.installedApp.packageName },
                            ) { appItem ->
                                AppItemCard(
                                    appItem = appItem,
                                    onOpenClick = { onAction(AppsAction.OnOpenApp(appItem.installedApp)) },
                                    onUpdateClick = { onAction(AppsAction.OnUpdateApp(appItem.installedApp)) },
                                    onCancelClick = { onAction(AppsAction.OnCancelUpdate(appItem.installedApp.packageName)) },
                                    onUninstallClick = { onAction(AppsAction.OnUninstallApp(appItem.installedApp)) },
                                    onRepoClick = { onAction(AppsAction.OnNavigateToRepo(appItem.installedApp.repoId)) },
                                    modifier = Modifier.liquefiable(liquidState),
                                )
                            }

                            item {
                                Spacer(Modifier.height(bottomNavHeight + 32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateAllProgressCard(
    progress: UpdateAllProgress,
    onCancel: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        stringResource(
                            Res.string.updating_x_of_y,
                            progress.current,
                            progress.total,
                        ),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.cancel),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text =
                    stringResource(
                        Res.string.currently_updating,
                        progress.currentAppName,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            LinearWavyProgressIndicator(
                progress = { progress.current.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AppItemCard(
    appItem: AppItem,
    onOpenClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onCancelClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onRepoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = appItem.installedApp

    ExpressiveCard(
        onClick = onRepoClick,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoilImage(
                    imageModel = { app.repoOwnerAvatarUrl },
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularWavyProgressIndicator()
                        }
                    },
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = app.repoOwner,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    when {
                        app.isPendingInstall -> {
                            Text(
                                text = stringResource(Res.string.pending_install),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }

                        app.isUpdateAvailable -> {
                            Text(
                                text = "${app.installedVersion} → ${app.latestVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        else -> {
                            Text(
                                text = app.installedVersion,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (app.repoDescription != null) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = app.repoDescription!!,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))

            when (val state = appItem.updateState) {
                is UpdateState.Downloading -> {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(Res.string.downloading),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (appItem.downloadProgress != null) {
                                Text(
                                    text = "${appItem.downloadProgress}%",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearWavyProgressIndicator(
                            progress = { (appItem.downloadProgress ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is UpdateState.Installing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(Res.string.installing),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                is UpdateState.CheckingUpdate -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(Res.string.checking),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                is UpdateState.Success -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(Res.string.updated_successfully),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                is UpdateState.Error -> {
                    Text(
                        text = stringResource(Res.string.error_with_message, state.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                UpdateState.Idle -> {}
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!app.isPendingInstall &&
                    appItem.updateState !is UpdateState.Downloading &&
                    appItem.updateState !is UpdateState.Installing &&
                    appItem.updateState !is UpdateState.CheckingUpdate
                ) {
                    IconButton(
                        onClick = onUninstallClick,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(Res.string.uninstall),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onOpenClick,
                    modifier = Modifier.weight(1f),
                    enabled =
                        !app.isPendingInstall &&
                            appItem.updateState !is UpdateState.Downloading &&
                            appItem.updateState !is UpdateState.Installing,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.open),
                    )
                }

                when (appItem.updateState) {
                    is UpdateState.Downloading, is UpdateState.Installing, is UpdateState.CheckingUpdate -> {
                        Button(
                            onClick = onCancelClick,
                            modifier = Modifier.weight(1f),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = stringResource(Res.string.cancel),
                                modifier = Modifier.size(18.dp),
                            )

                            Spacer(Modifier.width(4.dp))

                            Text(
                                text = stringResource(Res.string.cancel),
                            )
                        }
                    }

                    else -> {
                        if (app.isUpdateAvailable && !app.isPendingInstall) {
                            Button(
                                onClick = onUpdateClick,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Update,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(Res.string.update),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatLastChecked(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)

    return when {
        minutes < 1 -> stringResource(Res.string.last_checked_just_now)
        minutes < 60 -> stringResource(Res.string.last_checked_minutes_ago, minutes.toInt())
        else -> stringResource(Res.string.last_checked_hours_ago, hours.toInt())
    }
}

@Preview
@Composable
private fun Preview() {
    GithubStoreTheme {
        AppsScreen(
            state = AppsState(),
            onAction = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
