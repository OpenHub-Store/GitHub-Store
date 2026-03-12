package zed.rainxch.auth.data.di

import org.koin.dsl.module
import zed.rainxch.auth.data.repository.AuthenticationRepositoryImpl
import zed.rainxch.auth.domain.repository.AuthenticationRepository

val authModule =
    module {
        single<AuthenticationRepository> {
            AuthenticationRepositoryImpl(
                tokenStore = get(),
                logger = get(),
            )
        }
    }
