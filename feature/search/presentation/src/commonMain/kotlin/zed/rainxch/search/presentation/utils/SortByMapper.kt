package zed.rainxch.search.presentation.utils

import org.jetbrains.compose.resources.StringResource
import zed.rainxch.domain.model.SortBy
import zed.rainxch.domain.model.SortBy.*
import zed.rainxch.githubstore.core.presentation.res.*

fun SortBy.label(): StringResource =
    when (this) {
        MostStars -> Res.string.sort_most_stars
        MostForks -> Res.string.sort_most_forks
        BestMatch -> Res.string.sort_best_match
    }
