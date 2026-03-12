package zed.rainxch.devprofile.data.di

import org.koin.dsl.module
import zed.rainxch.devprofile.data.repository.DeveloperProfileRepositoryImpl
import zed.rainxch.devprofile.domain.repository.DeveloperProfileRepository

val devProfileModule =
    module {
        single<DeveloperProfileRepository> {
            DeveloperProfileRepositoryImpl(
                logger = get(),
                httpClient = get(),
                platform = get(),
                installedAppsDao = get(),
                favouritesRepository = get(),
            )
        }
    }
