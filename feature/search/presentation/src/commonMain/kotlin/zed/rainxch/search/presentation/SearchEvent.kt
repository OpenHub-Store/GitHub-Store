package zed.rainxch.search.presentation

sealed interface SearchEvent {
    data class OnMessage(
        val message: String,
    ) : SearchEvent

    data class NavigateToRepo(
        val owner: String,
        val repo: String,
    ) : SearchEvent
}
