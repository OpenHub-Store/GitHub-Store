package zed.rainxch.core.domain.utils

data class UpdateCheckReport(
    val usedTimestampLogic: Boolean,
    val branch: String,
    val reason: String,
    val windowSource: String,
    val windowSize: Int,
    val includePreReleases: Boolean,
    val opaqueMatched: Boolean,
    val sameTag: Boolean,
    val reconcilable: Boolean,
    val installedTag: String,
    val matchedTag: String?,
    val matchedAssetName: String? = null,
    val matchedAssetUrl: String? = null,
    val storedPublishedAt: String?,
    val matchedPublishedAt: String?,
    val codesAlreadyMatch: Boolean,
    val isUpdate: Boolean,
) {
    fun format(): String =
        listOf(
            "usedTimestampLogic=$usedTimestampLogic",
            "branch=$branch",
            "reason=$reason",
            "windowSource=$windowSource",
            "windowSize=$windowSize",
            "includePreReleases=$includePreReleases",
            "opaqueMatched=$opaqueMatched",
            "sameTag=$sameTag",
            "reconcilable=$reconcilable",
            "installedTag=$installedTag",
            "matchedTag=${matchedTag ?: "null"}",
            "matchedAssetName=${matchedAssetName ?: "null"}",
            "matchedAssetUrl=${matchedAssetUrl ?: "null"}",
            "storedPublishedAt=${storedPublishedAt ?: "null"}",
            "matchedPublishedAt=${matchedPublishedAt ?: "null"}",
            "codesAlreadyMatch=$codesAlreadyMatch",
            "isUpdate=$isUpdate",
        ).joinToString("\n")
}
