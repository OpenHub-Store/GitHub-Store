package zed.rainxch.details.data.utils

fun preprocessMarkdown(
    markdown: String,
    baseUrl: String,
): String {
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    var processed = markdown

    fun normalizeGitHubUrl(url: String): String =
        if (url.contains("github.com") && url.contains("/blob/")) {
            url
                .replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/")
        } else {
            url
        }

    fun isSvgUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".svg") ||
            lower.contains(".svg?") ||
            lower.contains(".svg#") ||
            lower.contains("/svg-badge") ||
            lower.contains("badge.svg")
    }

    fun isBadgeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("img.shields.io") ||
            lower.contains("shields.io/badge") ||
            lower.contains("badge.fury.io") ||
            lower.contains("badgen.net") ||
            lower.contains("repology.org/badge") ||
            lower.contains("hosted.weblate.org/widget") ||
            lower.contains("codecov.io") ||
            lower.contains("coveralls.io") ||
            lower.contains("travis-ci.") ||
            lower.contains("circleci.com") ||
            lower.contains("github.com/workflows") ||
            (lower.contains("/badge") && isSvgUrl(lower))
    }

    fun shouldSkipImage(url: String): Boolean = isSvgUrl(url) || isBadgeUrl(url)

    fun resolveUrl(path: String): String {
        val trimmed = path.trim()
        val isAbsolute =
            trimmed.startsWith("http://") ||
                trimmed.startsWith("https://") ||
                trimmed.startsWith("data:")
        return if (isAbsolute) {
            normalizeGitHubUrl(trimmed)
        } else {
            when {
                trimmed.startsWith("./") -> {
                    "$normalizedBaseUrl${trimmed.removePrefix("./")}"
                }

                trimmed.startsWith("/") -> {
                    "$normalizedBaseUrl${trimmed.removePrefix("/")}"
                }

                trimmed.startsWith("../") -> {
                    var base = normalizedBaseUrl.trimEnd('/')
                    var rel = trimmed
                    while (rel.startsWith("../")) {
                        base = base.substringBeforeLast('/', base)
                        rel = rel.removePrefix("../")
                    }
                    "$base/$rel"
                }

                else -> {
                    "$normalizedBaseUrl$trimmed"
                }
            }
        }
    }

    // ========================================================================
    // Phase 0: Handle reference-style markdown definitions and usages
    // ========================================================================
    // Reference definitions: [ref-name]: https://example.com/image.svg
    // Reference usages: ![alt][ref-name] or [![img-ref]][link-ref]

    // 0a. Parse all reference definitions
    val refDefinitionRegex =
        Regex(
            """^\[([^\]]+)\]:\s*(\S+).*$""",
            RegexOption.MULTILINE,
        )
    val referenceMap = mutableMapOf<String, String>()
    for (match in refDefinitionRegex.findAll(processed)) {
        val refName = match.groupValues[1].lowercase()
        val url = match.groupValues[2]
        referenceMap[refName] = url
    }

    // 0b. Identify which references point to SVGs/badges
    val skipRefNames =
        referenceMap
            .filter { (_, url) ->
                shouldSkipImage(resolveUrl(url))
            }.keys

    // 0c. Remove reference-style image usages that point to SVGs: ![alt][svg-ref]
    if (skipRefNames.isNotEmpty()) {
        processed =
            processed.replace(
                Regex("""!\[([^\]]*)\]\[([^\]]+)\]"""),
            ) { match ->
                val alt = match.groupValues[1]
                val refName = match.groupValues[2].lowercase()
                if (refName in skipRefNames) {
                    if (alt.isNotEmpty()) "**$alt**" else ""
                } else {
                    match.value
                }
            }
    }

    // 0d. Resolve remaining reference-style images to inline format: ![alt][ref] → ![alt](url)
    processed =
        processed.replace(
            Regex("""!\[([^\]]*)\]\[([^\]]+)\]"""),
        ) { match ->
            val alt = match.groupValues[1]
            val refName = match.groupValues[2].lowercase()
            val url = referenceMap[refName]
            if (url != null) {
                val resolved = resolveUrl(url)
                "![$alt]($resolved)"
            } else {
                match.value
            }
        }

    // 0e. Handle nested badge-as-link patterns: [![badge-ref]][link-ref]
    // After 0c strips the inner image, this can leave [**text**][link-ref] or [][link-ref]
    processed =
        processed.replace(
            Regex("""\[(\*\*[^*]*\*\*)\]\[([^\]]+)\]"""),
        ) { match ->
            val boldText = match.groupValues[1]
            val refName = match.groupValues[2].lowercase()
            val url = referenceMap[refName]
            if (url != null) {
                "[$boldText](${resolveUrl(url)})"
            } else {
                boldText
            }
        }
    // Clean empty bracket patterns left from stripped badge images: [][ref]
    processed =
        processed.replace(
            Regex("""\[\s*\]\[([^\]]+)\]"""),
            "",
        )

    // 0f. Handle reference-style links: [text][ref] → [text](url)
    processed =
        processed.replace(
            Regex("""\[([^\]]+)\]\[([^\]]+)\]"""),
        ) { match ->
            val text = match.groupValues[1]
            val refName = match.groupValues[2].lowercase()
            val url = referenceMap[refName]
            // Don't convert if text looks like it was already an image (starts with !)
            if (url != null && !text.startsWith("!")) {
                "[$text](${resolveUrl(url)})"
            } else {
                match.value
            }
        }

    // 0g. Remove all reference definitions that were resolved
    processed =
        processed.replace(
            Regex("""^\[([^\]]+)\]:\s*\S+.*$""", RegexOption.MULTILINE),
        ) { match ->
            val refName = match.groupValues[1].lowercase()
            if (refName in referenceMap) "" else match.value
        }

    // ========================================================================
    // Phase 1: HTML → Markdown conversions
    // ========================================================================

    // 1. Unwrap <picture> elements → keep only the <img> fallback
    processed =
        processed.replace(
            Regex(
                """<picture[^>]*>.*?(<img\s[^>]*?>).*?</picture>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            match.groupValues[1]
        }
    // Also strip orphaned <source> tags (outside <picture>)
    processed =
        processed.replace(
            Regex("""<source\s[^>]*?/?>""", RegexOption.IGNORE_CASE),
            "",
        )

    // 2. Unwrap <a> tags that wrap <img> tags — keep the <img> for step 3
    processed =
        processed.replace(
            Regex(
                """<a\s[^>]*?>\s*(<img\s[^>]*?>)\s*</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            match.groupValues[1]
        }

    // 3. Convert <img> tags → markdown images (handles multiline img tags)
    processed =
        processed.replace(
            Regex(
                """<img\s+([^>]*?)\s*/?>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { imgMatch ->
            val imgTag = imgMatch.groupValues[1]

            val srcMatch = Regex("""src\s*=\s*(["'])([^"']+)\1""").find(imgTag)
            val src = srcMatch?.groupValues?.get(2) ?: ""

            val altMatch = Regex("""alt\s*=\s*(["'])([^"']*)\1""").find(imgTag)
            val alt = altMatch?.groupValues?.get(2) ?: ""

            if (src.isNotEmpty()) {
                val normalizedSrc = resolveUrl(src)

                if (shouldSkipImage(normalizedSrc)) {
                    if (alt.isNotEmpty()) "**$alt**" else ""
                } else {
                    "![$alt]($normalizedSrc)"
                }
            } else {
                ""
            }
        }

    // 4. Normalize markdown image URLs (resolve relative, normalize GitHub blob)
    processed =
        processed.replace(
            Regex("""!\[([^\]]*)\]\(([^)]+)\)"""),
        ) { match ->
            val alt = match.groupValues[1]
            val originalPath = match.groupValues[2].trim()
            val finalUrl = resolveUrl(originalPath)

            if (shouldSkipImage(finalUrl)) {
                if (alt.isNotEmpty()) "**$alt**" else ""
            } else {
                "![$alt]($finalUrl)"
            }
        }

    // 5. Handle <video> tags → markdown link or remove
    processed =
        processed.replace(
            Regex(
                """<video[^>]*?\ssrc=(["'])([^"']+)\1[^>]*>.*?</video>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            val src = match.groupValues[2]
            "[Video](${resolveUrl(src)})"
        }
    // Video with <source> inside
    processed =
        processed.replace(
            Regex(
                """<video[^>]*>.*?<source\s[^>]*?\ssrc=(["'])([^"']+)\1[^>]*?>.*?</video>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            val src = match.groupValues[2]
            "[Video](${resolveUrl(src)})"
        }

    // 6. Convert HTML headings <h1>–<h6> → markdown headings
    for (level in 1..6) {
        val hashes = "#".repeat(level)
        processed =
            processed.replace(
                Regex(
                    """<h$level[^>]*>(.*?)</h$level>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
            ) { match ->
                val content = match.groupValues[1].trim()
                "\n$hashes $content\n"
            }
    }

    // 7. Convert <br> and <hr> tags
    processed =
        processed.replace(
            Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE),
            "\n",
        )
    processed =
        processed.replace(
            Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE),
            "\n---\n",
        )

    // 8. Convert inline formatting tags
    // <b> / <strong> → **text**
    processed =
        processed.replace(
            Regex(
                """<(b|strong)>(.*?)</\1>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            "**${match.groupValues[2]}**"
        }
    // <i> / <em> → *text*
    processed =
        processed.replace(
            Regex(
                """<(i|em)>(.*?)</\1>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            "*${match.groupValues[2]}*"
        }
    // <code> → `text` (single-line only, not <pre><code>)
    processed =
        processed.replace(
            Regex(
                """<code>([^<]*?)</code>""",
                RegexOption.IGNORE_CASE,
            ),
        ) { match ->
            "`${match.groupValues[1]}`"
        }
    // <s> / <del> / <strike> → ~~text~~
    processed =
        processed.replace(
            Regex(
                """<(s|del|strike)>(.*?)</\1>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            "~~${match.groupValues[2]}~~"
        }

    // 9. Convert <a href="url">text</a> → [text](url) (non-image links)
    processed =
        processed.replace(
            Regex(
                """<a\s+[^>]*?href\s*=\s*(["'])([^"']+)\1[^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            val url = match.groupValues[2]
            val text = match.groupValues[3].trim()
            val resolvedUrl = resolveUrl(url)
            if (text.isEmpty()) {
                "[$resolvedUrl]($resolvedUrl)"
            } else {
                "[$text]($resolvedUrl)"
            }
        }

    // 10. <kbd> → `text`
    processed =
        processed.replace(
            Regex(
                """<kbd>(.*?)</kbd>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            "`${match.groupValues[1]}`"
        }

    // 11. Strip remaining wrapper tags (keep content)
    // <div> tags
    processed =
        processed.replace(
            Regex("""<div[^>]*?>\s*""", RegexOption.IGNORE_CASE),
            "\n\n",
        )
    processed =
        processed.replace(
            Regex("""</div>\s*""", RegexOption.IGNORE_CASE),
            "\n\n",
        )
    // <p> / </p>
    processed =
        processed.replace(
            Regex("""<p[^>]*?>""", RegexOption.IGNORE_CASE),
            "\n",
        )
    processed =
        processed.replace(
            Regex("""</p>""", RegexOption.IGNORE_CASE),
            "\n",
        )
    // <details> / <summary>
    processed =
        processed.replace(
            Regex("""<details[^>]*?>""", RegexOption.IGNORE_CASE),
            "\n",
        )
    processed =
        processed.replace(
            Regex("""</details>""", RegexOption.IGNORE_CASE),
            "\n",
        )
    processed =
        processed.replace(
            Regex(
                """<summary[^>]*?>(.*?)</summary>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            "**${match.groupValues[1].trim()}**\n"
        }
    // <span>, <sup>, <sub> — strip tags, keep content
    processed =
        processed.replace(
            Regex("""</?(?:span|sup|sub)[^>]*?>""", RegexOption.IGNORE_CASE),
            "",
        )
    // Strip other common straggler HTML tags
    processed =
        processed.replace(
            Regex(
                """</?(?:center|font|u|section|article|header|footer|nav|main|aside|figure|figcaption)[^>]*?>""",
                RegexOption.IGNORE_CASE,
            ),
            "\n",
        )

    // 12. Decode common HTML entities
    processed =
        processed
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
    // Numeric HTML entities
    processed =
        processed.replace(Regex("""&#(\d+);""")) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 32..126) {
                code.toChar().toString()
            } else {
                match.value
            }
        }

    // 13. Clean up empty <p> tags and excess newlines
    processed =
        processed.replace(
            Regex("""<p[^>]*?>\s*</p>""", RegexOption.IGNORE_CASE),
            "",
        )
    processed =
        processed.replace(
            Regex("""\n{3,}"""),
            "\n\n",
        )

    // 14. Clean up orphaned markdown link fragments
    processed =
        processed.replace(
            Regex("""^\]\([^)]+\)""", RegexOption.MULTILINE),
            "",
        )

    return processed.trim()
}
