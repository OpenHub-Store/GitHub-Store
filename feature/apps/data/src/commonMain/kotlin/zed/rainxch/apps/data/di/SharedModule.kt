package zed.rainxch.apps.data.di

import org.koin.dsl.module
import zed.rainxch.apps.data.repository.AppsRepositoryImpl
import zed.rainxch.apps.domain.repository.AppsRepository

val appsModule =
    module {
        single<AppsRepository> {
            AppsRepositoryImpl(
                appLauncher = get(),
                appsRepository = get(),
                logger = get(),
                httpClient = get(),
            )
        }
    }
