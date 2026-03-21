package zed.rainxch.home.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.DiscoveryPlatform
import zed.rainxch.core.domain.model.PaginatedDiscoveryRepositories
import zed.rainxch.home.domain.model.TopicCategory

interface HomeRepository {
    fun getTrendingRepositories(
        platform: DiscoveryPlatform,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories>

    fun getHotReleaseRepositories(
        platform: DiscoveryPlatform,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories>

    fun getMostPopular(
        platform: DiscoveryPlatform,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories>

    fun searchByTopic(
        searchKeywords: String,
        platform: DiscoveryPlatform,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories>

    fun getTopicRepositories(
        topic: TopicCategory,
        platform: DiscoveryPlatform,
    ): Flow<PaginatedDiscoveryRepositories>
}
