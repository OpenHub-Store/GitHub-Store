package zed.rainxch.profile.data.di

import org.koin.dsl.module
import zed.rainxch.profile.data.repository.ProfileRepositoryImpl
import zed.rainxch.profile.domain.repository.ProfileRepository

val settingsModule =
    module {
        single<ProfileRepository> {
            ProfileRepositoryImpl(
                authenticationState = get(),
                tokenStore = get(),
                httpClient = get(),
                cacheManager = get(),
                logger = get(),
                fileLocationsProvider = get(),
            )
        }
    }
