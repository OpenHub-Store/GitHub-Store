package zed.rainxch.search.presentation.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import zed.rainxch.search.presentation.model.ParsedGithubLink

class GithubUrlParserTest {
    @Test
    fun parsesTrimmedBareRepositoryReferenceForSearch() {
        val expected =
            listOf(
                ParsedGithubLink(
                    owner = "aa",
                    repo = "bb",
                    fullUrl = "https://github.com/aa/bb",
                ),
            )

        assertEquals(expected, parseGithubSearchInput("aa/bb"))
        assertEquals(expected, parseGithubSearchInput(" \t aa/bb.git \n"))
        assertTrue(isEntirelyGithubSearchInput("aa/bb"))
        assertTrue(isEntirelyGithubSearchInput(" \t aa/bb.git \n"))
    }

    @Test
    fun validatesNormalizedBareRepositoryNameLength() {
        val repositoryAtLimit = "r".repeat(100)
        val repositoryOverLimit = "r".repeat(101)

        assertEquals(repositoryAtLimit, parseGithubSearchInput("aa/$repositoryAtLimit").single().repo)
        assertEquals(repositoryAtLimit, parseGithubSearchInput("aa/$repositoryAtLimit.git").single().repo)
        assertTrue(parseGithubSearchInput("aa/$repositoryOverLimit").isEmpty())
        assertTrue(parseGithubSearchInput("aa/$repositoryOverLimit.git").isEmpty())
    }

    @Test
    fun keepsClipboardParsingUrlOnly() {
        assertTrue(parseGithubUrls("aa/bb").isEmpty())
        assertFalse(isEntirelyGithubUrls("aa/bb"))
    }

    @Test
    fun preservesFullGithubUrlBehavior() {
        assertEquals(
            listOf(
                ParsedGithubLink(
                    owner = "aa",
                    repo = "bb",
                    fullUrl = "https://github.com/aa/bb",
                ),
            ),
            parseGithubUrls("https://github.com/aa/bb.git"),
        )
        assertTrue(isEntirelyGithubUrls("https://github.com/aa/bb.git"))
    }

    @Test
    fun preservesMultipleGithubUrlBehaviorForSearch() {
        val input = "https://github.com/aa/bb, https://www.github.com/cc/dd.git"

        assertEquals(
            listOf(
                ParsedGithubLink(
                    owner = "aa",
                    repo = "bb",
                    fullUrl = "https://github.com/aa/bb",
                ),
                ParsedGithubLink(
                    owner = "cc",
                    repo = "dd",
                    fullUrl = "https://github.com/cc/dd",
                ),
            ),
            parseGithubSearchInput(input),
        )
        assertTrue(isEntirelyGithubSearchInput(input))
    }

    @Test
    fun rejectsInvalidBareRepositoryReferencesForSearch() {
        val nonMatches =
            listOf(
                "https://gitlab.com/aa/bb",
                "aa/bb/issues",
                "open aa/bb please",
                "example.com/repository",
                "/aa/bb",
                "aa/bb/",
                "-aa/bb",
                "aa-/bb",
                "aa--bb/repository",
                "aa_bb/repository",
                "aa.bb/repository",
                "${"a".repeat(40)}/bb",
                "aa/.git",
            )

        nonMatches.forEach { input ->
            assertTrue(parseGithubSearchInput(input).isEmpty(), input)
            assertFalse(isEntirelyGithubSearchInput(input), input)
        }
    }
}
