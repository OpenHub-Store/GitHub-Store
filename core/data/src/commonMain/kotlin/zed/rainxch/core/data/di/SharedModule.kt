package zed.rainxch.core.data.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module
import zed.rainxch.core.data.network.createPlatformHttpClient
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.download.MultiSourceDownloaderImpl
import zed.rainxch.core.data.download.SlowDownloadDetectorImpl
import zed.rainxch.core.data.services.BuildKonfigAppVersionInfo
import zed.rainxch.core.data.services.DefaultDownloadOrchestrator
import zed.rainxch.core.data.services.DefaultSystemInstallSerializer
import zed.rainxch.core.data.data_source.impl.DefaultTokenStore
import zed.rainxch.core.data.local.db.AppDatabase
import zed.rainxch.core.data.local.db.dao.CacheDao
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.data.local.db.dao.FavoriteRepoDao
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.SearchHistoryDao
import zed.rainxch.core.data.local.db.dao.HiddenRepoDao
import zed.rainxch.core.data.local.db.dao.SeenRepoDao
import zed.rainxch.core.data.local.db.dao.SigningFingerprintDao
import zed.rainxch.core.data.local.db.dao.StarredRepoDao
import zed.rainxch.core.data.local.db.dao.UpdateHistoryDao
import zed.rainxch.core.data.logging.KermitLogger
import zed.rainxch.core.data.mirror.MirrorRepositoryImpl
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.BackendExternalMatchApi
import zed.rainxch.core.data.network.ExternalMatchApi
import zed.rainxch.core.data.network.ExternalMatchApiSelector
import zed.rainxch.core.data.network.ForgejoClientRegistry
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.MirrorApiClient
import zed.rainxch.core.data.network.MockExternalMatchApi
import zed.rainxch.core.data.network.ProxyManager
import zed.rainxch.core.data.network.ProxyTesterImpl
import zed.rainxch.core.data.network.TranslationClientProvider
import zed.rainxch.core.data.repository.UserSessionRepositoryImpl
import zed.rainxch.core.data.repository.ExternalImportRepositoryImpl
import zed.rainxch.core.data.repository.FavouritesRepositoryImpl
import zed.rainxch.core.data.repository.InstalledAppsRepositoryImpl
import zed.rainxch.core.data.repository.ProxyRepositoryImpl
import zed.rainxch.core.data.repository.RateLimitRepositoryImpl
import zed.rainxch.core.data.repository.HiddenReposRepositoryImpl
import zed.rainxch.core.data.repository.BrowseFilterStoreImpl
import zed.rainxch.core.data.repository.SeenReposRepositoryImpl
import zed.rainxch.core.data.repository.StarredRepositoryImpl
import zed.rainxch.core.data.repository.AnnouncementsCacheStoreImpl
import zed.rainxch.core.data.repository.AnnouncementsRepositoryImpl
import zed.rainxch.core.data.repository.CacheRepositoryImpl
import zed.rainxch.core.data.repository.HostTokenRepositoryImpl
import zed.rainxch.core.data.repository.TweaksRepositoryImpl
import zed.rainxch.core.domain.getPlatform
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.model.settings.ProxyScope
import zed.rainxch.core.domain.network.ProxyTester
import zed.rainxch.core.domain.network.SlowDownloadDetector
import zed.rainxch.core.domain.system.AppVersionInfo
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.SystemInstallSerializer
import zed.rainxch.core.domain.system.ExternalAppScanner
import zed.rainxch.core.domain.system.MultiSourceDownloader
import zed.rainxch.core.domain.repository.AnnouncementsCacheStore
import zed.rainxch.core.domain.repository.AnnouncementsRepository
import zed.rainxch.core.domain.repository.CacheRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.MirrorRepository
import zed.rainxch.core.domain.repository.ProxyRepository
import zed.rainxch.core.domain.repository.RateLimitRepository
import zed.rainxch.core.domain.repository.HiddenReposRepository
import zed.rainxch.core.domain.repository.HostTokenRepository
import zed.rainxch.core.domain.repository.BrowseFilterStore
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase

val coreModule =
    module {
        single {
            CoroutineScope(Dispatchers.IO + SupervisorJob())
        }

        single<KomiStoreLogger> {
            KermitLogger
        }

        single<Platform> {
            getPlatform()
        }

        single<UserSessionRepository> {
            UserSessionRepositoryImpl(
                tokenStore = get(),
                cacheManager = get(),
                httpClientProvider = { get<GitHubClientProvider>().client },
                logger = get(),
            )
        }

        single<FavouritesRepository> {
            FavouritesRepositoryImpl(
                favoriteRepoDao = get(),
                installedAppsDao = get(),
            )
        }

        single<ForgejoClientRegistry> {
            ForgejoClientRegistry(
                proxyConfigFlow = ProxyManager.configFlow(ProxyScope.DISCOVERY),
            )
        }

        single<CacheRepository> {
            CacheRepositoryImpl(
                logger = get(),
                fileLocationsProvider = get(),
                cacheManager = get()
            )
        }

        single<InstalledAppsRepository> {
            InstalledAppsRepositoryImpl(
                database = get(),
                installedAppsDao = get(),
                historyDao = get(),
                installer = get(),
                clientProvider = get(),
                backendApiClient = get(),
                forgejoClientRegistry = get(),
            )
        }

        single<StarredRepository> {
            StarredRepositoryImpl(
                installedAppsDao = get(),
                starredRepoDao = get(),
                platform = get(),
                clientProvider = get(),
                backendApiClient = get(),
            )
        }

        single<TweaksRepository> {
            TweaksRepositoryImpl(
                ksafe = get(qualifier = named("prefs")),
                legacyDataStore = get(),
            )
        }

        single<AppVersionInfo> {
            BuildKonfigAppVersionInfo()
        }

        single<AnnouncementsCacheStore> {
            AnnouncementsCacheStoreImpl(
                ksafe = get(qualifier = named("announcements_cache")),
                legacyDataStore = get(qualifier = named("announcements")),
            )
        }

        single<AnnouncementsRepository> {
            AnnouncementsRepositoryImpl(
                backendApiClient = get(),
                tweaksRepository = get(),
                cacheStore = get(),
                localizationManager = get(),
                appVersionInfo = get(),
            )
        }

        single<MirrorApiClient> {
            MirrorApiClient(
                backendApiClient = get(),
            )
        }

        single<MirrorRepository> {
            val repo =
                MirrorRepositoryImpl(
                    ksafe = get(qualifier = named("prefs")),
                    legacyDataStore = get(),
                    apiClient = get(),
                    appScope = get(),
                )
            ProxyManager.startMirrorCollector(
                repository = repo,
                scope = get()
            )
            repo
        }

        single<SeenReposRepository> {
            SeenReposRepositoryImpl(
                seenRepoDao = get(),
            )
        }

        single<BrowseFilterStore> {
            BrowseFilterStoreImpl()
        }

        single<HiddenReposRepository> {
            HiddenReposRepositoryImpl(
                hiddenRepoDao = get(),
            )
        }

        single<ProxyRepository> {
            ProxyRepositoryImpl(
                ksafe = get(qualifier = named("prefs")),
                legacyDataStore = get(),
                logger = get(),
            )
        }

        single<ProxyTester> {
            ProxyTesterImpl()
        }

        single<SyncInstalledAppsUseCase> {
            SyncInstalledAppsUseCase(
                packageMonitor = get(),
                installedAppsRepository = get(),
                platform = get(),
                logger = get(),
            )
        }

        single<CacheManager> {
            CacheManager(
                cacheDao = get(),
                appScope = get()
            )
        }

        single<BackendApiClient> {
            BackendApiClient(
                proxyConfigFlow = ProxyManager.configFlow(ProxyScope.DISCOVERY),
                tokenStore = get(),
            )
        }

        single { BackendExternalMatchApi(get()) }

        single { MockExternalMatchApi() }

        single<ExternalMatchApi> {
            ExternalMatchApiSelector(
                real = get(),
                mock = get(),
                tweaks = get(),
                scope = get(),
            )
        }

        single<ExternalImportRepository> {
            ExternalImportRepositoryImpl(
                scanner = get<ExternalAppScanner>(),
                externalLinkDao = get(),
                installedAppDao = get(),
                signingFingerprintDao = get(),
                ksafe = get(qualifier = named("prefs")),
                legacyDataStore = get(),
                externalMatchApi = get(),
                backendClient = get(),
                forgejoClientRegistry = get(),
                tweaksRepository = get(),
                starredRepository = get(),
            )
        }

        single<MultiSourceDownloader> {
            MultiSourceDownloaderImpl(
                downloader = get(),
            )
        }

        single<SlowDownloadDetector> {
            SlowDownloadDetectorImpl(
                ksafe = get(qualifier = named("prefs")),
                appScope = get(),
            )
        }

        single<DownloadOrchestrator> {
            DefaultDownloadOrchestrator(
                downloader = get(),
                multiSourceDownloader = get(),
                digestVerifier = get(),
                installer = get(),
                installedAppsRepository = get(),
                pendingInstallNotifier = get(),
                slowDownloadDetector = get(),
                appScope = get(),
                systemInstallSerializer = get(),
                tokenStore = get(),
            )
        }

        single<SystemInstallSerializer> {
            DefaultSystemInstallSerializer()
        }
    }

val networkModule =
    module {
        single<GitHubClientProvider> {
            GitHubClientProvider(
                tokenStore = get(),
                rateLimitRepository = get(),
                userSessionRepository = get(),
                proxyConfigFlow = ProxyManager.configFlow(ProxyScope.DISCOVERY),
            )
        }

        single<TranslationClientProvider> {
            TranslationClientProvider(
                proxyConfigFlow = ProxyManager.configFlow(ProxyScope.TRANSLATION),
            )
        }

        single<TokenStore> {
            DefaultTokenStore(
                ksafe = get(qualifier = named("tokens")),
                legacyDataStore = get(),
            )
        }

        single<RateLimitRepository> {
            RateLimitRepositoryImpl()
        }

        single<HostTokenRepository> {
            HostTokenRepositoryImpl(
                ksafe = get(qualifier = named("tokens")),
                httpClient = get(qualifier = named("test")),
            )
        }

        single<HttpClient>(qualifier = named("test")) {
            createPlatformHttpClient(ProxyConfig.System).config {
                install(HttpTimeout) {
                    requestTimeoutMillis = 5_000
                    connectTimeoutMillis = 5_000
                    socketTimeoutMillis = 5_000
                }
                defaultRequest {
                    header(HttpHeaders.UserAgent, "GithubStore/1.0 (KMP)")
                }
            }
        }
    }

val databaseModule =
    module {
        single<FavoriteRepoDao> {
            get<AppDatabase>().favoriteRepoDao
        }

        single<InstalledAppDao> {
            get<AppDatabase>().installedAppDao
        }

        single<StarredRepoDao> {
            get<AppDatabase>().starredReposDao
        }

        single<UpdateHistoryDao> {
            get<AppDatabase>().updateHistoryDao
        }

        single<CacheDao> {
            get<AppDatabase>().cacheDao
        }

        single<SeenRepoDao> {
            get<AppDatabase>().seenRepoDao
        }

        single<HiddenRepoDao> {
            get<AppDatabase>().hiddenRepoDao
        }

        single<SearchHistoryDao> {
            get<AppDatabase>().searchHistoryDao
        }

        single<ExternalLinkDao> {
            get<AppDatabase>().externalLinkDao
        }

        single<SigningFingerprintDao> {
            get<AppDatabase>().signingFingerprintDao
        }
    }
