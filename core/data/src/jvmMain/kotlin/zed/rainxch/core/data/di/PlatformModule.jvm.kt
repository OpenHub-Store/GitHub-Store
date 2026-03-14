package zed.rainxch.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.koin.dsl.module
import zed.rainxch.core.data.local.data_store.createDataStore
import zed.rainxch.core.data.local.db.AppDatabase
import zed.rainxch.core.data.local.db.initDatabase
import zed.rainxch.core.data.services.DesktopInstallerInfoExtractor
import zed.rainxch.core.data.utils.DesktopAppLauncher
import zed.rainxch.core.data.utils.DesktopBrowserHelper
import zed.rainxch.core.data.utils.DesktopClipboardHelper
import zed.rainxch.core.data.services.DesktopDownloader
import zed.rainxch.core.data.services.DesktopFileLocationsProvider
import zed.rainxch.core.data.services.DesktopInstaller
import zed.rainxch.core.data.services.DesktopLocalizationManager
import zed.rainxch.core.data.services.DesktopPackageMonitor
import zed.rainxch.core.data.services.FileLocationsProvider
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerStatusProvider
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.data.services.DesktopInstallerStatusProvider
import zed.rainxch.core.data.utils.DesktopShareManager
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.utils.AppLauncher
import zed.rainxch.core.domain.utils.BrowserHelper
import zed.rainxch.core.domain.utils.ClipboardHelper
import zed.rainxch.core.domain.utils.ShareManager

actual val corePlatformModule = module {
    // Core
    single<Downloader> {
        DesktopDownloader(
            files = get(),
        )
    }

    single<Installer> {
        DesktopInstaller(
            platform = get(),
            installerInfoExtractor = DesktopInstallerInfoExtractor(),
        )
    }

    single<FileLocationsProvider> {
        DesktopFileLocationsProvider(
            platform = get()
        )
    }

    single<PackageMonitor> {
        DesktopPackageMonitor()
    }

    single<LocalizationManager> {
        DesktopLocalizationManager()
    }

    // Locals

    single<AppDatabase> {
        initDatabase()
    }

    single<DataStore<Preferences>> {
        createDataStore()
    }


    // Utils

    single<BrowserHelper> {
        DesktopBrowserHelper()
    }

    single<ClipboardHelper> {
        DesktopClipboardHelper()
    }

    single<AppLauncher> {
        DesktopAppLauncher(
            logger = get(),
            platform = get()
        )
    }

    single<ShareManager> {
        DesktopShareManager()
    }

    single<InstallerStatusProvider> {
        DesktopInstallerStatusProvider()
    }
}