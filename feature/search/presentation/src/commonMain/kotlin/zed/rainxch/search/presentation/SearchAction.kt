package zed.rainxch.search.presentation

import zed.rainxch.core.domain.model.GithubRepoSummary
import zed.rainxch.domain.model.ProgrammingLanguage
import zed.rainxch.domain.model.SearchPlatform
import zed.rainxch.domain.model.SortBy
import zed.rainxch.domain.model.SortOrder

sealed interface SearchAction {
    data class OnSearchChange(
        val query: String,
    ) : SearchAction

    data class OnPlatformTypeSelected(
        val searchPlatform: SearchPlatform,
    ) : SearchAction

    data class OnLanguageSelected(
        val language: ProgrammingLanguage,
    ) : SearchAction

    data class OnSortBySelected(
        val sortBy: SortBy,
    ) : SearchAction

    data class OnSortOrderSelected(
        val sortOrder: SortOrder,
    ) : SearchAction

    data class OnRepositoryClick(
        val repository: GithubRepoSummary,
    ) : SearchAction

    data class OnRepositoryDeveloperClick(
        val username: String,
    ) : SearchAction

    data class OnShareClick(
        val repo: GithubRepoSummary,
    ) : SearchAction

    data class OpenGithubLink(
        val owner: String,
        val repo: String,
    ) : SearchAction

    data object OnSearchImeClick : SearchAction

    data object OnNavigateBackClick : SearchAction

    data object LoadMore : SearchAction

    data object OnClearClick : SearchAction

    data object Retry : SearchAction

    data object OnToggleLanguageSheetVisibility : SearchAction

    data object OnToggleSortByDialogVisibility : SearchAction

    data object OnFabClick : SearchAction

    data object DismissClipboardBanner : SearchAction
}
