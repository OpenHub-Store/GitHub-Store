package zed.rainxch.home.data.di

import org.koin.dsl.module
import zed.rainxch.home.data.data_source.CachedRepositoriesDataSource
import zed.rainxch.home.data.data_source.impl.CachedRepositoriesDataSourceImpl
import zed.rainxch.home.data.repository.HomeRepositoryImpl
import zed.rainxch.home.domain.repository.HomeRepository

val homeModule =
    module {
        single<HomeRepository> {
            HomeRepositoryImpl(
                cachedDataSource = get(),
                httpClient = get(),
                platform = get(),
                logger = get(),
                cacheManager = get(),
            )
        }

        single<CachedRepositoriesDataSource> {
            CachedRepositoriesDataSourceImpl(
                platform = get(),
                logger = get(),
            )
        }
    }
