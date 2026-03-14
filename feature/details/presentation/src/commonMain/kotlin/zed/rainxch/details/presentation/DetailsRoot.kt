package zed.rainxch.details.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.rememberLiquidState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.isLiquidFrostAvailable
import zed.rainxch.details.presentation.components.LanguagePicker
import zed.rainxch.details.presentation.components.sections.about
import zed.rainxch.details.presentation.components.sections.author
import zed.rainxch.details.presentation.components.sections.header
import zed.rainxch.details.presentation.components.sections.logs
import zed.rainxch.details.presentation.components.sections.reportIssue
import zed.rainxch.details.presentation.components.sections.stats
import zed.rainxch.details.presentation.components.sections.whatsNew
import zed.rainxch.details.presentation.components.states.ErrorState
import zed.rainxch.details.presentation.model.TranslationTarget
import zed.rainxch.details.presentation.utils.LocalTopbarLiquidState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.add_to_favourites
import zed.rainxch.githubstore.core.presentation.res.cancel
import zed.rainxch.githubstore.core.presentation.res.dismiss
import zed.rainxch.githubstore.core.presentation.res.downgrade_requires_uninstall
import zed.rainxch.githubstore.core.presentation.res.downgrade_warning_message
import zed.rainxch.githubstore.core.presentation.res.install_permission_blocked_message
import zed.rainxch.githubstore.core.presentation.res.install_permission_unavailable
import zed.rainxch.githubstore.core.presentation.res.navigate_back
import zed.rainxch.githubstore.core.presentation.res.open_repository
import zed.rainxch.githubstore.core.presentation.res.open_with_external_installer
import zed.rainxch.githubstore.core.presentation.res.remove_from_favourites
import zed.rainxch.githubstore.core.presentation.res.repository_not_starred
import zed.rainxch.githubstore.core.presentation.res.repository_starred
import zed.rainxch.githubstore.core.presentation.res.share_repository
import zed.rainxch.githubstore.core.presentation.res.star_from_github
import zed.rainxch.githubstore.core.presentation.res.uninstall_first
import zed.rainxch.githubstore.core.presentation.res.unstar_from_github

@Composable
fun DetailsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    onOpenRepositoryInApp: (repoId: Long) -> Unit,
    viewModel: DetailsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DetailsEvent.OnOpenRepositoryInApp -> {
                onOpenRepositoryInApp(event.repositoryId)
            }

            is DetailsEvent.OnMessage -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    DetailsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                DetailsAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                is DetailsAction.OpenDeveloperProfile -> {
                    onNavigateToDeveloperProfile(action.username)
                }

                is DetailsAction.OnMessage -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(getString(action.messageText))
                    }
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
    )

    state.downgradeWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(DetailsAction.OnDismissDowngradeWarning)
            },
            title = {
                Text(
                    text = stringResource(Res.string.downgrade_requires_uninstall),
                )
            },
            text = {
                Text(
                    text =
                        stringResource(
                            Res.string.downgrade_warning_message,
                            warning.targetVersion,
                            warning.currentVersion,
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OnDismissDowngradeWarning)
                        viewModel.onAction(DetailsAction.UninstallApp)
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.uninstall_first),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OnDismissDowngradeWarning)
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.cancel),
                    )
                }
            },
        )
    }

    // Uninstall confirmation dialog
    if (state.showUninstallConfirmation) {
        val appName = state.installedApp?.appName ?: ""
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(DetailsAction.OnDismissUninstallConfirmation)
            },
            title = {
                Text(
                    text = stringResource(Res.string.confirm_uninstall_title),
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.confirm_uninstall_message, appName),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OnConfirmUninstall)
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.uninstall),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OnDismissUninstallConfirmation)
                    },
                ) {
                    Text(text = stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (state.showExternalInstallerPrompt) {
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(DetailsAction.DismissExternalInstallerPrompt)
            },
            title = {
                Text(text = stringResource(Res.string.install_permission_unavailable))
            },
            text = {
                Text(text = stringResource(Res.string.install_permission_blocked_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OpenWithExternalInstaller)
                    },
                ) {
                    Text(text = stringResource(Res.string.open_with_external_installer))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.DismissExternalInstallerPrompt)
                    },
                ) {
                    Text(text = stringResource(Res.string.dismiss))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailsScreen(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val liquidTopbarState = rememberLiquidState()

    CompositionLocalProvider(
        value = LocalTopbarLiquidState provides liquidTopbarState,
    ) {
        Scaffold(
            topBar = {
                DetailsTopbar(
                    state = state,
                    onAction = onAction,
                    liquidTopbarState = liquidTopbarState,
                )
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.liquefiable(liquidTopbarState),
        ) { innerPadding ->

            LanguagePicker(
                isVisible = state.isLanguagePickerVisible,
                selectedLanguageCode =
                    when (state.languagePickerTarget) {
                        TranslationTarget.About -> state.aboutTranslation.targetLanguageCode
                        TranslationTarget.WhatsNew -> state.whatsNewTranslation.targetLanguageCode
                        null -> null
                    },
                onLanguageSelected = { language ->
                    when (state.languagePickerTarget) {
                        TranslationTarget.About -> {
                            onAction(DetailsAction.TranslateAbout(language.code))
                        }

                        TranslationTarget.WhatsNew -> {
                            onAction(
                                DetailsAction.TranslateWhatsNew(
                                    language.code,
                                ),
                            )
                        }

                        null -> {}
                    }
                    onAction(DetailsAction.DismissLanguagePicker)
                },
                onDismiss = { onAction(DetailsAction.DismissLanguagePicker) },
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator()
                }

                return@Scaffold
            }

            if (state.errorMessage != null) {
                ErrorState(state.errorMessage, onAction)

                return@Scaffold
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val collapsedSectionHeight = maxHeight * 0.7f

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .widthIn(max = 680.dp)
                            .fillMaxWidth()
                            .liquefiable(liquidTopbarState)
                            .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    header(
                        state = state,
                        onAction = onAction,
                    )

                    state.stats?.let { stats ->
                        stats(repoStats = stats)
                    }

                    if (state.isComingFromUpdate) {
                        state.selectedRelease?.let { release ->
                            whatsNew(
                                release = release,
                                isExpanded = state.isWhatsNewExpanded,
                                onToggleExpanded = { onAction(DetailsAction.ToggleWhatsNewExpanded) },
                                collapsedHeight = collapsedSectionHeight,
                                translationState = state.whatsNewTranslation,
                                onTranslateClick = {
                                    onAction(DetailsAction.TranslateWhatsNew(state.deviceLanguageCode))
                                },
                                onLanguagePickerClick = {
                                    onAction(DetailsAction.ShowLanguagePicker(TranslationTarget.WhatsNew))
                                },
                                onToggleTranslation = {
                                    onAction(DetailsAction.ToggleWhatsNewTranslation)
                                },
                            )
                        }

                        state.readmeMarkdown?.let {
                            about(
                                readmeMarkdown = state.readmeMarkdown,
                                readmeLanguage = state.readmeLanguage,
                                isExpanded = state.isAboutExpanded,
                                onToggleExpanded = { onAction(DetailsAction.ToggleAboutExpanded) },
                                collapsedHeight = collapsedSectionHeight,
                                translationState = state.aboutTranslation,
                                onTranslateClick = {
                                    onAction(DetailsAction.TranslateAbout(state.deviceLanguageCode))
                                },
                                onLanguagePickerClick = {
                                    onAction(DetailsAction.ShowLanguagePicker(TranslationTarget.About))
                                },
                                onToggleTranslation = {
                                    onAction(DetailsAction.ToggleAboutTranslation)
                                },
                            )
                        }
                    } else {
                        state.readmeMarkdown?.let {
                            about(
                                readmeMarkdown = state.readmeMarkdown,
                                readmeLanguage = state.readmeLanguage,
                                isExpanded = state.isAboutExpanded,
                                onToggleExpanded = { onAction(DetailsAction.ToggleAboutExpanded) },
                                collapsedHeight = collapsedSectionHeight,
                                translationState = state.aboutTranslation,
                                onTranslateClick = {
                                    onAction(DetailsAction.TranslateAbout(state.deviceLanguageCode))
                                },
                                onLanguagePickerClick = {
                                    onAction(DetailsAction.ShowLanguagePicker(TranslationTarget.About))
                                },
                                onToggleTranslation = {
                                    onAction(DetailsAction.ToggleAboutTranslation)
                                },
                            )
                        }

                        state.selectedRelease?.let { release ->
                            whatsNew(
                                release = release,
                                isExpanded = state.isWhatsNewExpanded,
                                onToggleExpanded = { onAction(DetailsAction.ToggleWhatsNewExpanded) },
                                collapsedHeight = collapsedSectionHeight,
                                translationState = state.whatsNewTranslation,
                                onTranslateClick = {
                                    onAction(DetailsAction.TranslateWhatsNew(state.deviceLanguageCode))
                                },
                                onLanguagePickerClick = {
                                    onAction(DetailsAction.ShowLanguagePicker(TranslationTarget.WhatsNew))
                                },
                                onToggleTranslation = {
                                    onAction(DetailsAction.ToggleWhatsNewTranslation)
                                },
                            )
                        }
                    }

                    state.repository?.let { repository ->
                        reportIssue(
                            repoUrl = repository.htmlUrl,
                        )
                    }

                    state.userProfile?.let { userProfile ->
                        author(
                            author = userProfile,
                            onAction = onAction,
                        )
                    }

                    if (state.installLogs.isNotEmpty()) {
                        logs(state)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailsTopbar(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
    liquidTopbarState: LiquidState,
) {
    TopAppBar(
        title = { },
        navigationIcon = {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = {
                    onAction(DetailsAction.OnNavigateBackClick)
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.navigate_back),
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.repository != null) {
                    IconButton(
                        onClick = {
                            onAction(
                                DetailsAction.OnMessage(
                                    messageText =
                                        if (state.isStarred) {
                                            Res.string.unstar_from_github
                                        } else {
                                            Res.string.star_from_github
                                        },
                                ),
                            )
                        },
                        shapes = IconButtonDefaults.shapes(),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Icon(
                            imageVector =
                                if (state.isStarred) {
                                    Icons.Default.Star
                                } else {
                                    Icons.Default.StarBorder
                                },
                            contentDescription =
                                stringResource(
                                    resource =
                                        if (state.isStarred) {
                                            Res.string.repository_starred
                                        } else {
                                            Res.string.repository_not_starred
                                        },
                                ),
                        )
                    }

                    IconButton(
                        onClick = {
                            onAction(DetailsAction.OnToggleFavorite)
                        },
                        shapes = IconButtonDefaults.shapes(),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Icon(
                            imageVector =
                                if (state.isFavourite) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                            contentDescription =
                                stringResource(
                                    resource =
                                        if (state.isFavourite) {
                                            Res.string.remove_from_favourites
                                        } else {
                                            Res.string.add_to_favourites
                                        },
                                ),
                        )
                    }

                    IconButton(
                        onClick = {
                            onAction(DetailsAction.OnShareClick)
                        },
                        shapes = IconButtonDefaults.shapes(),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(Res.string.share_repository),
                        )
                    }
                }

                state.repository?.htmlUrl?.let {
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = {
                            onAction(DetailsAction.OpenRepoInBrowser)
                        },
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = stringResource(Res.string.open_repository),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
        modifier =
            Modifier
                .shadow(
                    elevation = 6.dp,
                    ambientColor = MaterialTheme.colorScheme.surfaceTint,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                ).background(
                    Brush.linearGradient(
                        0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        0.5f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    ),
                ).then(
                    if (isLiquidFrostAvailable()) {
                        Modifier.liquid(liquidTopbarState) {
                            this.shape = CutCornerShape(0.dp)
                            if (isLiquidFrostAvailable()) {
                                this.frost = 5.dp
                            }
                            this.curve = .25f
                            this.refraction = .05f
                            this.dispersion = .1f
                        }
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    },
                ),
    )
}

@Preview
@Composable
private fun Preview() {
    GithubStoreTheme {
        DetailsScreen(
            state =
                DetailsState(
                    isLoading = false,
                ),
            onAction = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
