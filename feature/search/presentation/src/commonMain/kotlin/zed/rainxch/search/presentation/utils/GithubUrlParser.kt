package zed.rainxch.search.presentation.utils

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.search.presentation.model.ParsedGithubLink

private val GITHUB_URL_REGEX =
    Regex(
        """(?<![A-Za-z0-9.-])(?:https?://)?(?:www\.)?github\.com/([a-zA-Z0-9\-_.]+)/([a-zA-Z0-9\-_.]+)""",
    )

private val GITHUB_REPOSITORY_REFERENCE_REGEX =
    Regex(
        """([a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38})/([a-zA-Z0-9\-_.]+)""",
    )

fun parseGithubUrls(text: String): ImmutableList<ParsedGithubLink> =
    GITHUB_URL_REGEX
        .findAll(text)
        .map { match ->
            ParsedGithubLink(
                owner = match.groupValues[1],
                repo = match.groupValues[2].removeSuffix(".git"),
                fullUrl = "https://github.com/${match.groupValues[1]}/${match.groupValues[2].removeSuffix(".git")}",
            )
        }.distinctBy { "${it.owner}/${it.repo}" }
        .toImmutableList()

fun parseGithubSearchInput(text: String): ImmutableList<ParsedGithubLink> =
    parseBareGithubRepository(text)
        ?.let { listOf(it).toImmutableList() }
        ?: parseGithubUrls(text)

fun isEntirelyGithubUrls(text: String): Boolean {
    val stripped =
        text
            .replace(GITHUB_URL_REGEX, "")
            .replace(Regex("""[\s,;]+"""), "")
    return stripped.isEmpty() && parseGithubUrls(text).isNotEmpty()
}

fun isEntirelyGithubSearchInput(text: String): Boolean =
    parseBareGithubRepository(text) != null || isEntirelyGithubUrls(text)

private fun parseBareGithubRepository(text: String): ParsedGithubLink? {
    val match = GITHUB_REPOSITORY_REFERENCE_REGEX.matchEntire(text.trim()) ?: return null
    val owner = match.groupValues[1]
    val repo = match.groupValues[2].removeSuffix(".git")
    if (repo.length !in 1..100) return null

    return ParsedGithubLink(
        owner = owner,
        repo = repo,
        fullUrl = "https://github.com/$owner/$repo",
    )
}
