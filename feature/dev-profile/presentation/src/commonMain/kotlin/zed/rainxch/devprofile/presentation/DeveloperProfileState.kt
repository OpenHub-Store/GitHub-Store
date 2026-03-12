package zed.rainxch.devprofile.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.devprofile.domain.model.DeveloperProfile
import zed.rainxch.devprofile.domain.model.DeveloperRepository
import zed.rainxch.devprofile.domain.model.RepoFilterType
import zed.rainxch.devprofile.domain.model.RepoSortType

data class DeveloperProfileState(
    val username: String = "",
    val profile: DeveloperProfile? = null,
    val repositories: ImmutableList<DeveloperRepository> = persistentListOf(),
    val filteredRepositories: ImmutableList<DeveloperRepository> = persistentListOf(),
    val isLoading: Boolean = false,
    val isLoadingRepos: Boolean = false,
    val errorMessage: String? = null,
    val currentFilter: RepoFilterType = RepoFilterType.WITH_RELEASES,
    val currentSort: RepoSortType = RepoSortType.UPDATED,
    val searchQuery: String = "",
)
