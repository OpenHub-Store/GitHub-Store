package zed.rainxch.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.PaginatedDiscoveryRepositories
import zed.rainxch.domain.model.ProgrammingLanguage
import zed.rainxch.domain.model.SearchPlatform
import zed.rainxch.domain.model.SortBy
import zed.rainxch.domain.model.SortOrder

interface SearchRepository {
    fun searchRepositories(
        query: String,
        searchPlatform: SearchPlatform,
        language: ProgrammingLanguage,
        sortBy: SortBy,
        sortOrder: SortOrder,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories>
}
