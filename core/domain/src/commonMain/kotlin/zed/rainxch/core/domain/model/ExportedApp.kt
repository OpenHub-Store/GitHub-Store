package zed.rainxch.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportedApp(
    val packageName: String,
    val repoOwner: String,
    val repoName: String,
    val repoUrl: String,
)

@Serializable
data class ExportedAppList(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val apps: List<ExportedApp> = emptyList(),
)
