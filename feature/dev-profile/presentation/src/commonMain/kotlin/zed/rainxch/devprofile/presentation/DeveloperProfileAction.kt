package zed.rainxch.devprofile.presentation

import zed.rainxch.devprofile.domain.model.DeveloperRepository
import zed.rainxch.devprofile.domain.model.RepoFilterType
import zed.rainxch.devprofile.domain.model.RepoSortType

sealed interface DeveloperProfileAction {
    data object OnNavigateBackClick : DeveloperProfileAction

    data class OnRepositoryClick(
        val repoId: Long,
    ) : DeveloperProfileAction

    data class OnFilterChange(
        val filter: RepoFilterType,
    ) : DeveloperProfileAction

    data class OnSortChange(
        val sort: RepoSortType,
    ) : DeveloperProfileAction

    data class OnSearchQueryChange(
        val query: String,
    ) : DeveloperProfileAction

    data class OnToggleFavorite(
        val repository: DeveloperRepository,
    ) : DeveloperProfileAction

    data object OnDismissError : DeveloperProfileAction

    data object OnRetry : DeveloperProfileAction

    data class OnOpenLink(
        val url: String,
    ) : DeveloperProfileAction
}
