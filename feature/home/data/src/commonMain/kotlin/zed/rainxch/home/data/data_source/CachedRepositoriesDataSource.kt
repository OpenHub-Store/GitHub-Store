package zed.rainxch.home.data.data_source

import zed.rainxch.core.domain.model.DiscoveryPlatform
import zed.rainxch.home.data.dto.CachedRepoResponse
import zed.rainxch.home.domain.model.TopicCategory

interface CachedRepositoriesDataSource {
    suspend fun getCachedTrendingRepos(platform: DiscoveryPlatform): CachedRepoResponse?

    suspend fun getCachedHotReleaseRepos(platform: DiscoveryPlatform): CachedRepoResponse?

    suspend fun getCachedMostPopularRepos(platform: DiscoveryPlatform): CachedRepoResponse?

    suspend fun getCachedTopicRepos(topic: TopicCategory, platform: DiscoveryPlatform): CachedRepoResponse?
}
