package zed.rainxch.apps.domain.model

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
)
