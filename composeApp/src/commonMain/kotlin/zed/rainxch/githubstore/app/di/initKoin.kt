package zed.rainxch.githubstore.app.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import zed.rainxch.apps.data.di.appsModule
import zed.rainxch.auth.data.di.authModule
import zed.rainxch.core.data.di.coreModule
import zed.rainxch.core.data.di.corePlatformModule
import zed.rainxch.core.data.di.databaseModule
import zed.rainxch.core.data.di.networkModule
import zed.rainxch.details.data.di.detailsModule
import zed.rainxch.devprofile.data.di.devProfileModule
import zed.rainxch.home.data.di.homeModule
import zed.rainxch.profile.data.di.settingsModule
import zed.rainxch.search.data.di.searchModule

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            mainModule,
            corePlatformModule,
            coreModule,
            networkModule,
            databaseModule,
            viewModelsModule,
            appsModule,
            authModule,
            detailsModule,
            devProfileModule,
            homeModule,
            searchModule,
            settingsModule,
        )
    }
}
