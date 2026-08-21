package zed.rainxch.details.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.details.domain.model.ReleaseCategory

class RawDetailsStateMapperTest {
    private fun release(tag: String, publishedAt: String): GithubRelease =
        GithubRelease(
            id = tag.hashCode().toLong(),
            tagName = tag,
            name = tag,
            publishedAt = publishedAt,
            description = null,
            assets = emptyList(),
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "",
        )

    @Test
    fun stable_category_never_falls_back_to_nightly() {
        val releases = listOf(release("nightly", "2026-08-22T00:00:00Z"))

        assertNull(releases.firstReleaseForCategory(ReleaseCategory.STABLE))
        assertEquals("nightly", releases.firstReleaseForCategory(ReleaseCategory.PRE_RELEASE)?.tagName)
    }

    @Test
    fun stable_category_selects_stable_release_when_mixed_with_nightly() {
        val releases = listOf(
            release("nightly", "2026-08-22T00:00:00Z"),
            release("v1.9.2", "2026-08-20T00:00:00Z"),
        )

        assertEquals("v1.9.2", releases.firstReleaseForCategory(ReleaseCategory.STABLE)?.tagName)
    }
}
