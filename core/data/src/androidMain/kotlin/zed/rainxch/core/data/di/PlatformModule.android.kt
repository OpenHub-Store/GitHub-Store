package zed.rainxch.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import zed.rainxch.core.data.local.data_store.createDataStore
import zed.rainxch.core.data.local.db.AppDatabase
import zed.rainxch.core.data.local.db.initDatabase
import zed.rainxch.core.data.services.AndroidDownloader
import zed.rainxch.core.data.services.AndroidFileLocationsProvider
import zed.rainxch.core.data.services.AndroidInstaller
import zed.rainxch.core.data.services.AndroidInstallerInfoExtractor
import zed.rainxch.core.data.services.AndroidLocalizationManager
import zed.rainxch.core.data.services.AndroidPackageMonitor
import zed.rainxch.core.data.services.FileLocationsProvider
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.data.utils.AndroidAppLauncher
import zed.rainxch.core.data.utils.AndroidBrowserHelper
import zed.rainxch.core.data.utils.AndroidClipboardHelper
import zed.rainxch.core.data.utils.AndroidShareManager
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.utils.AppLauncher
import zed.rainxch.core.domain.utils.BrowserHelper
import zed.rainxch.core.domain.utils.ClipboardHelper
import zed.rainxch.core.domain.utils.ShareManager

actual val corePlatformModule =
    module {
        // Core

        single<Downloader> {
            AndroidDownloader(
                files = get(),
            )
        }

        single<Installer> {
            AndroidInstaller(
                context = get(),
                installerInfoExtractor = AndroidInstallerInfoExtractor(androidContext()),
            )
        }

        single<FileLocationsProvider> {
            AndroidFileLocationsProvider(context = get())
        }

        single<PackageMonitor> {
            AndroidPackageMonitor(androidContext())
        }

        single<LocalizationManager> {
            AndroidLocalizationManager()
        }

        // Locals

        single<AppDatabase> {
            initDatabase(androidContext())
        }

        single<DataStore<Preferences>> {
            createDataStore(androidContext())
        }

        // Utils

        single<BrowserHelper> {
            AndroidBrowserHelper(androidContext())
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
    }
