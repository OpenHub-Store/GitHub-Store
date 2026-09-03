package zed.rainxch.githubstore

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.getViewModel
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.data.utils.AndroidShareManager
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.githubstore.app.deeplink.DeepLinkParser
import zed.rainxch.githubstore.utils.updateSystemBars
import kotlin.time.Duration.Companion.milliseconds

private const val LANGUAGE_PREF_READ_TIMEOUT_MS = 2000L

class MainActivity : ComponentActivity() {
    private var deepLinkUri by mutableStateOf<String?>(null)

    // Mirror of MainViewModel.appearanceLoaded, collected here so the splash
    // hold reads the SAME single source of truth the Compose gate uses.
    // While true, the keep-on-screen condition cancels every draw request —
    // the placeholder frame is never rendered, so the user goes
    // splash -> their real theme with zero intermediate frames.
    private var appearanceLoaded = false
    private val shareManager: ShareManager by inject()
    private val tweaksRepository: TweaksRepository by inject()
    private val localizationManager: LocalizationManager by inject()
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase by inject()
    private val appScope: CoroutineScope by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Hold while the persisted appearance preferences are still loading.
        // While held, draw requests are cancelled so no placeholder frame is
        // ever rendered; the first frame released is the real themed UI.
        splash.setKeepOnScreenCondition { !appearanceLoaded }
        enableEdgeToEdge()

        (shareManager as? AndroidShareManager)?.registerActivityResultLauncher(this)

        runBlocking {
            val tag =
                try {
                    withTimeoutOrNull(LANGUAGE_PREF_READ_TIMEOUT_MS.milliseconds) {
                        tweaksRepository.getAppLanguage().first()
                    }
                } catch (_: Exception) {
                    null
                }
            localizationManager.setActiveLanguageTag(tag)
        }

        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                tweaksRepository
                    .getAppLanguage()
                    .drop(1)
                    .collect { newTag ->
                        localizationManager.setActiveLanguageTag(newTag)
                        recreate()
                    }
            }
        }

        // Mirror the MainViewModel appearance gate into this activity so the
        // splash hold reads the same value the Compose gate does. MainViewModel
        // is a Koin single-activity-scoped view model resolved with the same
        // factory inside setContent's koinViewModel().
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val viewModel = getViewModel<MainViewModel>()
                viewModel.state.collect { state ->
                    appearanceLoaded = state.appearanceLoaded
                }
            }
        }

        setContent {
            DisposableEffect(Unit) {
                val listener =
                    Consumer<Intent> { newIntent ->
                        handleIncomingIntent(newIntent)
                    }

                addOnNewIntentListener(listener)

                onDispose {
                    removeOnNewIntentListener(listener)
                }
            }

            App(
                deepLinkUri = deepLinkUri,
                onResolvedDarkTheme = { isDarkTheme ->
                    this@MainActivity.updateSystemBars(isDarkTheme)
                },
            )
        }
    }

    override fun onRestart() {
        super.onRestart()
        appScope.launch {
            runCatching { syncInstalledAppsUseCase() }
                .onFailure {
                    Logger.w(it) { "onRestart sync failed" }
                }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val uriString =
            when (intent.action) {
                Intent.ACTION_VIEW -> {
                    intent.data?.toString()
                }

                Intent.ACTION_SEND -> {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    sharedText?.let { DeepLinkParser.extractSupportedUrl(it) }
                }

                else -> {
                    null
                }
            }

        uriString?.let { deepLinkUri = it }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
