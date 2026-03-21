package zed.rainxch.details.domain.util

import zed.rainxch.core.domain.model.GithubRelease

/**
 * Pure utility for normalising and comparing release version strings.
 */
object VersionHelper {
    fun normalizeVersion(version: String?): String =
        version
            ?.trim()
            ?.removePrefix("refs/tags/")
            ?.removePrefix("v")
            ?.removePrefix("V")
            .orEmpty()

    /**
     * Returns `true` if [candidate] is strictly older than [current].
     * Uses list-index order as the primary heuristic (releases are newest-first),
     * and falls back to semantic version comparison when list lookup fails.
     */
    fun isDowngradeVersion(
        candidate: String,
        current: String,
        allReleases: List<GithubRelease>,
    ): Boolean {
        val normalizedCandidate = normalizeVersion(candidate)
        val normalizedCurrent = normalizeVersion(current)

        if (normalizedCandidate == normalizedCurrent) return false

        val candidateIndex =
            allReleases.indexOfFirst {
                normalizeVersion(it.tagName) == normalizedCandidate
            }
        val currentIndex =
            allReleases.indexOfFirst {
                normalizeVersion(it.tagName) == normalizedCurrent
            }

        if (candidateIndex != -1 && currentIndex != -1) {
            return candidateIndex > currentIndex
        }

        return compareSemanticVersions(normalizedCandidate, normalizedCurrent) < 0
    }

    /**
     * Compares two semantic version strings.
     * Returns positive if [a] > [b], negative if [a] < [b], 0 if equal.
     */
    fun compareSemanticVersions(
        a: String,
        b: String,
    ): Int {
        val aCore = a.split("-", limit = 2)
        val bCore = b.split("-", limit = 2)
        val aParts = aCore[0].split(".")
        val bParts = bCore[0].split(".")

        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val aPart = aParts.getOrNull(i)?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
            val bPart = bParts.getOrNull(i)?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
            if (aPart != bPart) return aPart.compareTo(bPart)
        }

        val aHasPre = aCore.size > 1
        val bHasPre = bCore.size > 1
        if (aHasPre != bHasPre) return if (aHasPre) -1 else 1

        return 0
    }
}
