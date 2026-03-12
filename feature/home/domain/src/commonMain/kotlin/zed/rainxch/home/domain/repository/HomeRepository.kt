package zed.rainxch.home.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.PaginatedDiscoveryRepositories

interface HomeRepository {
    fun getTrendingRepositories(page: Int): Flow<PaginatedDiscoveryRepositories>

    fun getHotReleaseRepositories(page: Int): Flow<PaginatedDiscoveryRepositories>

    fun getMostPopular(page: Int): Flow<PaginatedDiscoveryRepositories>
}
