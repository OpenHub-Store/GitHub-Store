package zed.rainxch.domain.model

enum class SortOrder {
    Descending,
    Ascending,
    ;

    fun toGithubParam(): String =
        when (this) {
            Descending -> "desc"
            Ascending -> "asc"
        }
}
