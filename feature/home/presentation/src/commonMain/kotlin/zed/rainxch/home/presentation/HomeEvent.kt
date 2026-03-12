package zed.rainxch.home.presentation

sealed interface HomeEvent {
    data object OnScrollToListTop : HomeEvent

    data class OnMessage(
        val message: String,
    ) : HomeEvent
}
