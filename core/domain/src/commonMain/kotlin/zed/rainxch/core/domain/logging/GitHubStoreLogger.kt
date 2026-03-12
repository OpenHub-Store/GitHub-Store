package zed.rainxch.core.domain.logging

interface GitHubStoreLogger {
    fun debug(message: String)

    fun info(message: String)

    fun warn(message: String)

    fun error(
        message: String,
        throwable: Throwable? = null,
    )
}
