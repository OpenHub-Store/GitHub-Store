package zed.rainxch.apps.presentation.model

sealed interface UpdateState {
    data object Idle : UpdateState

    data object CheckingUpdate : UpdateState

    data object Downloading : UpdateState

    data object Installing : UpdateState

    data object Success : UpdateState

    data class Error(
        val message: String,
    ) : UpdateState
}
