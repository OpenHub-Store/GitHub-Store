package zed.rainxch.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import zed.rainxch.core.data.local.data_store.createAnnouncementsDataStore
import zed.rainxch.core.data.local.data_store.createDataStore
import zed.rainxch.core.data.local.db.AppDatabase
import zed.rainxch.core.data.local.db.initDatabase
import zed.rainxch.core.data.services.AndroidApkInspector
import zed.rainxch.core.data.services.AndroidDownloader
import zed.rainxch.core.data.services.AndroidDownloadProgressNotifier
import zed.rainxch.core.data.services.AndroidFileLocationsProvider
import zed.rainxch.core.data.services.AndroidInstaller
import zed.rainxch.core.data.services.AndroidInstallerInfoExtractor
import zed.rainxch.core.data.services.CurrentActivityHolder
import zed.rainxch.core.data.services.AndroidLocalizationManager
import zed.rainxch.core.data.services.AndroidPackageMonitor
import zed.rainxch.core.data.services.AndroidPendingInstallNotifier
import zed.rainxch.core.data.services.AndroidUpdateScheduleManager
import zed.rainxch.core.data.services.DownloadNotificationObserver
import zed.rainxch.core.data.services.FileLocationsProvider
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.data.services.external.AndroidExternalAppScanner
import zed.rainxch.core.data.services.external.InstallerSourceClassifier
import zed.rainxch.core.data.services.external.ManifestHintExtractor
import zed.rainxch.core.data.services.dhizuku.DhizukuServiceManager
import zed.rainxch.core.data.services.installer.AndroidInstallerStatusProvider
import zed.rainxch.core.data.services.installer.SilentInstallerDispatcher
import zed.rainxch.core.data.services.root.RootServiceManager
import zed.rainxch.core.data.services.shizuku.ShizukuServiceManager
import zed.rainxch.core.data.utils.AndroidAppLauncher
import zed.rainxch.core.data.utils.AndroidBrowserHelper
import zed.rainxch.core.data.utils.AndroidClipboardHelper
import zed.rainxch.core.data.utils.AndroidShareManager
import zed.rainxch.core.data.network.AndroidDigestVerifier
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.system.ApkInspector
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadProgressNotifier
import zed.rainxch.core.domain.system.ExternalAppScanner
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerStatusProvider
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.system.PendingInstallNotifier
import zed.rainxch.core.domain.system.UpdateScheduleManager
import zed.rainxch.core.domain.helpers.AppLauncher
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.helpers.ClipboardHelper
import zed.rainxch.core.domain.helpers.ShareManager

actual val corePlatformModule =
    module {

        single<Downloader> {
            AndroidDownloader(
                files = get(),
                tokenStore = get(),
            )
        }

        single {
            CurrentActivityHolder.install(androidContext() as android.app.Application)
            AndroidInstaller(
                context = androidContext(),
                installerInfoExtractor = AndroidInstallerInfoExtractor(androidContext()),
            )
        }

        single {
            ShizukuServiceManager(
                context = androidContext(),
            ).also { it.initialize() }
        }

        single {
            DhizukuServiceManager(
                context = androidContext(),
            ).also { it.initialize() }
        }

        single {
            RootServiceManager(
                context = androidContext(),
                scope = get<CoroutineScope>(),
            ).also { it.initialize() }
        }

        single<Installer> {
            SilentInstallerDispatcher(
                androidContext = androidContext(),
                androidInstaller = get<AndroidInstaller>(),
                shizukuServiceManager = get(),
                dhizukuServiceManager = get(),
                rootServiceManager = get(),
                tweaksRepository = get(),
                scope = get<CoroutineScope>(),
            ).also { dispatcher ->
                dispatcher.observeInstallerPreference()
            }
        }

        single<InstallerStatusProvider> {
            AndroidInstallerStatusProvider(
                shizukuServiceManager = get(),
                dhizukuServiceManager = get(),
                rootServiceManager = get(),
                scope = get(),
            )
        }

        single<FileLocationsProvider> {
            AndroidFileLocationsProvider(context = get())
        }

        single<zed.rainxch.core.domain.system.AggressiveOemDetector> {
            zed.rainxch.core.data.services.AndroidAggressiveOemDetector(context = androidContext())
        }

        single<PendingInstallNotifier> {
            AndroidPendingInstallNotifier(context = androidContext())
        }

        single<DownloadProgressNotifier> {
            AndroidDownloadProgressNotifier(context = androidContext())
        }

        single {
            DownloadNotificationObserver(
                orchestrator = get<DownloadOrchestrator>(),
                notifier = get<DownloadProgressNotifier>(),
            )
        }

        single<PackageMonitor> {
            AndroidPackageMonitor(androidContext())
        }

        single<ApkInspector> {
            AndroidApkInspector(androidContext())
        }

        single { ManifestHintExtractor() }

        single {
            InstallerSourceClassifier(
                packageManager = androidContext().packageManager,
                selfPackageName = androidContext().packageName,
            )
        }

        single<ExternalAppScanner> {
            AndroidExternalAppScanner(
                context = androidContext(),
                manifestHintExtractor = get(),
                installerSourceClassifier = get(),
            )
        }

        single<LocalizationManager> {
            AndroidLocalizationManager()
        }

        single<AppDatabase> {
            initDatabase(androidContext())
        }

        single<DataStore<Preferences>> {
            createDataStore(androidContext())
        }

        single<DataStore<Preferences>>(qualifier = org.koin.core.qualifier.named("announcements")) {
            createAnnouncementsDataStore(androidContext())
        }

        single<eu.anifantakis.lib.ksafe.KSafe>(qualifier = org.koin.core.qualifier.named("tokens")) {
            eu.anifantakis.lib.ksafe.KSafe(
                context = androidContext(),
                fileName = "ghs_tokens",
            )
        }

        single<eu.anifantakis.lib.ksafe.KSafe>(qualifier = org.koin.core.qualifier.named("prefs")) {
            eu.anifantakis.lib.ksafe.KSafe(
                context = androidContext(),
                fileName = "ghs_prefs",
            )
        }

        single<eu.anifantakis.lib.ksafe.KSafe>(qualifier = org.koin.core.qualifier.named("announcements_cache")) {
            eu.anifantakis.lib.ksafe.KSafe(
                context = androidContext(),
                fileName = "ghs_announcements",
            )
        }

        single<BrowserHelper> {
            AndroidBrowserHelper(androidContext())
        }

        single<DigestVerifier> {
            AndroidDigestVerifier()
        }

        single<ClipboardHelper> {
            AndroidClipboardHelper(androidContext())
        }

        single<AppLauncher> {
            AndroidAppLauncher(
                context = androidContext(),
                logger = get(),
            )
        }

        single<ShareManager> {
            AndroidShareManager(
                context = androidContext(),
            )
        }

        single<UpdateScheduleManager> {
            AndroidUpdateScheduleManager(
                context = androidContext(),
            )
        }
    }
