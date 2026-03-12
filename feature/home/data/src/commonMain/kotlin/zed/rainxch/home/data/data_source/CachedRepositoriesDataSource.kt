package zed.rainxch.home.data.data_source

import zed.rainxch.home.data.dto.CachedRepoResponse

interface CachedRepositoriesDataSource {
    suspend fun getCachedTrendingRepos(): CachedRepoResponse?

    suspend fun getCachedHotReleaseRepos(): CachedRepoResponse?

    suspend fun getCachedMostPopularRepos(): CachedRepoResponse?
}
