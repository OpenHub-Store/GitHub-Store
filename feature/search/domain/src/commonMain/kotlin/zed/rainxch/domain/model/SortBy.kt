package zed.rainxch.domain.model

enum class SortBy {
    MostStars,
    MostForks,
    BestMatch,
    ;

    fun toGithubSortParam(): String? =
        when (this) {
            MostStars -> "stars"
            MostForks -> "forks"
            BestMatch -> null
        }
}
