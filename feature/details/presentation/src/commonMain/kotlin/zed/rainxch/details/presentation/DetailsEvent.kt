package zed.rainxch.details.presentation

sealed interface DetailsEvent {
    data class OnOpenRepositoryInApp(
        val repositoryId: Long,
    ) : DetailsEvent

    data class OnMessage(
        val message: String,
    ) : DetailsEvent
}
