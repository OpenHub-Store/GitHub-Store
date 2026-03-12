package zed.rainxch.details.data.utils

import zed.rainxch.details.data.model.ReadmeAttempt

class ReadmeLocalizationHelper(
    private val localizationManager: zed.rainxch.core.data.services.LocalizationManager,
) {
    private val searchPaths =
        listOf(
            ".github",
            "",
            "docs",
            "doc",
        )

    fun generateReadmeAttempts(): List<ReadmeAttempt> {
        val attempts = mutableListOf<ReadmeAttempt>()
        val currentLang = localizationManager.getCurrentLanguageCode().lowercase()
        val primaryLang = localizationManager.getPrimaryLanguageCode().lowercase()

        var globalPriority = 0

        for ((pathIndex, searchPath) in searchPaths.withIndex()) {
            val pathPrefix = if (searchPath.isEmpty()) "" else "$searchPath/"

            var localPriority = 0

            if (currentLang.contains("-")) {
                attempts.add(
                    ReadmeAttempt(
                        path = "${pathPrefix}README.$currentLang.md",
                        filename = "README.$currentLang.md",
                        priority = globalPriority + localPriority++,
                    ),
                )
                attempts.add(
                    ReadmeAttempt(
                        path = "${pathPrefix}README.${currentLang.replace("-", "_")}.md",
                        filename = "README.${currentLang.replace("-", "_")}.md",
                        priority = globalPriority + localPriority++,
                    ),
                )
            }

            attempts.add(
                ReadmeAttempt(
                    path = "${pathPrefix}README.$primaryLang.md",
                    filename = "README.$primaryLang.md",
                    priority = globalPriority + localPriority++,
                ),
            )

            if (currentLang.contains("-")) {
                val parts = currentLang.split("-")
                attempts.add(
                    ReadmeAttempt(
                        path = "${pathPrefix}README.${parts[0].uppercase()}.md",
                        filename = "README.${parts[0].uppercase()}.md",
                        priority = globalPriority + localPriority++,
                    ),
                )
                attempts.add(
                    ReadmeAttempt(
                        path = "${pathPrefix}README-${parts[0].uppercase()}.md",
                        filename = "README-${parts[0].uppercase()}.md",
                        priority = globalPriority + localPriority++,
                    ),
                )
            } else {
                attempts.add(
                    ReadmeAttempt(
                        path = "${pathPrefix}README.${primaryLang.uppercase()}.md",
                        filename = "README.${primaryLang.uppercase()}.md",
                        priority = globalPriority + localPriority++,
                    ),
                )
                attempts.add(
                    ReadmeAttempt(
                        path = "${pathPrefix}README-${primaryLang.uppercase()}.md",
                        filename = "README-${primaryLang.uppercase()}.md",
                        priority = globalPriority + localPriority++,
                    ),
                )
            }

            attempts.add(
                ReadmeAttempt(
                    path = "${pathPrefix}README_$primaryLang.md",
                    filename = "README_$primaryLang.md",
                    priority = globalPriority + localPriority++,
                ),
            )
            attempts.add(
                ReadmeAttempt(
                    path = "${pathPrefix}readme.$primaryLang.md",
                    filename = "readme.$primaryLang.md",
                    priority = globalPriority + localPriority++,
                ),
            )

            attempts.add(
                ReadmeAttempt(
                    path = "${pathPrefix}README.md",
                    filename = "README.md",
                    priority = globalPriority + localPriority++,
                ),
            )

            if (primaryLang != "en") {
                attempts.add(
                    ReadmeAttempt(
                        path = "${pathPrefix}README.en.md",
                        filename = "README.en.md",
                        priority = globalPriority + localPriority++,
                    ),
                )
                attempts.add(
                    ReadmeAttempt(
                        path = "${pathPrefix}README.EN.md",
                        filename = "README.EN.md",
                        priority = globalPriority + localPriority++,
                    ),
                )
                attempts.add(
                    ReadmeAttempt(
                        path = "${pathPrefix}README-EN.md",
                        filename = "README-EN.md",
                        priority = globalPriority + localPriority++,
                    ),
                )
            }

            globalPriority += 100 * (pathIndex + 1)
        }

        return attempts.sortedBy { it.priority }
    }

    fun detectReadmeLanguage(content: String): String? {
        val sample = content.take(1000)
        val sampleLower = sample.lowercase()

        val chineseChars = sample.count { it in '\u4e00'..'\u9fff' }
        val japaneseHiragana = sample.count { it in '\u3040'..'\u309f' }
        val japaneseKatakana = sample.count { it in '\u30a0'..'\u30ff' }
        val koreanChars = sample.count { it in '\uac00'..'\ud7af' }
        val arabicChars = sample.count { it in '\u0600'..'\u06ff' }
        val cyrillicChars = sample.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

        val totalChars = sample.length
        val threshold = 0.15

        return when {
            chineseChars > totalChars * threshold -> {
                "zh"
            }

            (japaneseHiragana + japaneseKatakana) > totalChars * threshold -> {
                "ja"
            }

            koreanChars > totalChars * threshold -> {
                "ko"
            }

            arabicChars > totalChars * threshold -> {
                "ar"
            }

            cyrillicChars > totalChars * threshold -> {
                "ru"
            }

            else -> {
                val englishIndicators =
                    listOf(
                        "\\bthe\\b",
                        "\\band\\b",
                        "\\bfor\\b",
                        "\\bwith\\b",
                        "\\bthis\\b",
                        "\\bthat\\b",
                        "\\bfrom\\b",
                        "\\bare\\b",
                        "\\bwas\\b",
                        "\\bhave\\b",
                        "\\bhas\\b",
                        "\\bwill\\b",
                        "\\byou\\b",
                        "\\bcan\\b",
                        "\\buse\\b",
                        "\\binstall\\b",
                    )

                val matchCount =
                    englishIndicators.count { pattern ->
                        Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(sampleLower)
                    }

                if (matchCount >= 4) {
                    "en"
                } else {
                    null
                }
            }
        }
    }
}
