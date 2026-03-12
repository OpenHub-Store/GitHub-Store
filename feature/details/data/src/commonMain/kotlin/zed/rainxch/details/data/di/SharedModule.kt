package zed.rainxch.details.data.di

import org.koin.dsl.module
import zed.rainxch.details.data.repository.DetailsRepositoryImpl
import zed.rainxch.details.data.repository.TranslationRepositoryImpl
import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.domain.repository.TranslationRepository

val detailsModule =
    module {
        single<DetailsRepository> {
            DetailsRepositoryImpl(
                logger = get(),
                httpClient = get(),
                localizationManager = get(),
                cacheManager = get(),
            )
        }

        single<TranslationRepository> {
            TranslationRepositoryImpl(
                localizationManager = get(),
            )
        }
    }
