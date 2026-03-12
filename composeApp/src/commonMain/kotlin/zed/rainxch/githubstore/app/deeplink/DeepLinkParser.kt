package zed.rainxch.githubstore.app.deeplink

sealed interface DeepLinkDestination {
    data class Repository(
        val owner: String,
        val repo: String,
    ) : DeepLinkDestination

    data object None : DeepLinkDestination
}

object DeepLinkParser {
    private val INVALID_CHARS = setOf('/', '\\', '?', '#', '@', ':', '*', '"', '<', '>', '|', '%', '&', '=')

    private val FORBIDDEN_PATTERNS = listOf("..", "~", "\u0000")

    private val EXCLUDED_PATHS =
        setOf(
            "about",
            "account",
            "admin",
            "api",
            "apps",
            "articles",
            "blog",
            "business",
            "collections",
            "contact",
            "dashboard",
            "enterprises",
            "events",
            "explore",
            "features",
            "home",
            "issues",
            "marketplace",
            "new",
            "notifications",
            "orgs",
            "pricing",
            "pulls",
            "search",
            "security",
            "settings",
            "showcases",
            "site",
            "sponsors",
            "topics",
            "trending",
            "team",
        )

    fun parse(uri: String): DeepLinkDestination {
        return when {
            uri.startsWith("githubstore://repo/") -> {
                val path = uri.removePrefix("githubstore://repo/")
                val decoded = urlDecode(path)
                parseOwnerRepo(decoded)
            }

            uri.startsWith("https://github.com/") -> {
                val path =
                    uri
                        .removePrefix("https://github.com/")
                        .substringBefore('?')
                        .substringBefore('#')
                val decoded = urlDecode(path)

                val parts = decoded.split("/").filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    val owner = parts[0]
                    val repo = parts[1]
                    if (isStrictlyValidOwnerRepo(owner, repo)) {
                        return DeepLinkDestination.Repository(owner, repo)
                    }
                }
                DeepLinkDestination.None
            }

            uri.startsWith("https://github-store.org/app/") -> {
                extractQueryParam(uri, "repo")?.let { encodedRepoParam ->
                    val decoded = urlDecode(encodedRepoParam)
                    parseOwnerRepo(decoded)
                } ?: DeepLinkDestination.None
            }

            else -> {
                DeepLinkDestination.None
            }
        }
    }

    /**
     * URL-decode a string, handling percent-encoded characters.
     * Returns the original string if decoding fails.
     */
    private fun urlDecode(value: String): String =
        try {
            val result = StringBuilder()
            var i = 0
            while (i < value.length) {
                when (val c = value[i]) {
                    '%' -> {
                        if (i + 2 < value.length) {
                            val hex = value.substring(i + 1, i + 3)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                result.append(code.toChar())
                                i += 3
                                continue
                            }
                        }
                        result.append(c)
                        i++
                    }

                    '+' -> {
                        result.append(' ')
                        i++
                    }

                    else -> {
                        result.append(c)
                        i++
                    }
                }
            }
            result.toString()
        } catch (_: Exception) {
            value
        }

    private fun parseOwnerRepo(path: String): DeepLinkDestination {
        val parts = path.split("/").filter { it.isNotEmpty() }
        return if (parts.size >= 2) {
            val owner = parts[0]
            val repo = parts[1]
            if (isStrictlyValidOwnerRepo(owner, repo)) {
                DeepLinkDestination.Repository(owner, repo)
            } else {
                DeepLinkDestination.None
            }
        } else {
            DeepLinkDestination.None
        }
    }

    /**
     * Strictly validate owner and repo names to prevent injection attacks.
     * Rejects:
     * - Empty strings
     * - Special characters that could be used for injection
     * - Path traversal patterns
     * - Control characters and whitespace
     * - Excluded GitHub paths (like 'about', 'settings', etc.)
     * - Names that exceed GitHub's length limits
     * - Names that don't start with alphanumeric characters
     */
    private fun isStrictlyValidOwnerRepo(
        owner: String,
        repo: String,
    ): Boolean {
        if (owner.isEmpty() || repo.isEmpty()) {
            return false
        }

        if (owner.any { it in INVALID_CHARS } || repo.any { it in INVALID_CHARS }) {
            return false
        }

        if (FORBIDDEN_PATTERNS.any { pattern ->
                owner.contains(pattern, ignoreCase = true) ||
                    repo.contains(pattern, ignoreCase = true)
            }
        ) {
            return false
        }

        if (owner.any { it.isISOControl() } || repo.any { it.isISOControl() }) {
            return false
        }

        if (owner.contains(' ') || repo.contains(' ')) {
            return false
        }

        if (EXCLUDED_PATHS.contains(owner.lowercase())) {
            return false
        }

        if (owner.length > 39 || repo.length > 100) {
            return false
        }

        if (!owner.first().isLetterOrDigit() || !repo.first().isLetterOrDigit()) {
            return false
        }

        return true
    }

    private fun extractQueryParam(
        uri: String,
        key: String,
    ): String? {
        val queryStart = uri.indexOf('?')
        if (queryStart == -1) return null

        val queryString = uri.substring(queryStart + 1)
        val params = queryString.split('&')

        for (param in params) {
            val keyValue = param.split('=', limit = 2)
            if (keyValue.size == 2 && keyValue[0] == key) {
                return keyValue[1]
            }
        }
        return null
    }

    fun extractSupportedUrl(text: String): String? {
        val regex =
            """https?://(?:www\.)?(?:github\.com|github-store\.org)(?=[/\s?#]|$)[^\s<>"')\],;.!]*""".toRegex(
                RegexOption.IGNORE_CASE,
            )
        return regex.find(text)?.value
    }
}
