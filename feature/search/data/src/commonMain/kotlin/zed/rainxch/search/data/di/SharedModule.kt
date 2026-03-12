package zed.rainxch.search.data.di

import org.koin.dsl.module
import zed.rainxch.domain.repository.SearchRepository
import zed.rainxch.search.data.repository.SearchRepositoryImpl

val searchModule =
    module {
        single<SearchRepository> {
            SearchRepositoryImpl(
                httpClient = get(),
                cacheManager = get(),
            )
        }
    }
