package zed.rainxch.search.presentation.utils

data class ParsedGithubLink(
    val owner: String,
    val repo: String,
    val fullUrl: String,
)

private val GITHUB_URL_REGEX =
    Regex(
        """(?<![A-Za-z0-9.-])(?:https?://)?(?:www\.)?github\.com/([a-zA-Z0-9\-_.]+)/([a-zA-Z0-9\-_.]+)""",
    )

fun parseGithubUrls(text: String): List<ParsedGithubLink> =
    GITHUB_URL_REGEX
        .findAll(text)
        .map { match ->
            ParsedGithubLink(
                owner = match.groupValues[1],
                repo = match.groupValues[2].removeSuffix(".git"),
                fullUrl = "https://github.com/${match.groupValues[1]}/${match.groupValues[2].removeSuffix(".git")}",
            )
        }.distinctBy { "${it.owner}/${it.repo}" }
        .toList()

fun isEntirelyGithubUrls(text: String): Boolean {
    val stripped =
        text
            .replace(GITHUB_URL_REGEX, "")
            .replace(Regex("""[\s,;]+"""), "")
    return stripped.isEmpty() && parseGithubUrls(text).isNotEmpty()
}
