package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.local.db.entities.UpdateHistoryEntity
import zed.rainxch.core.domain.model.UpdateHistory

fun UpdateHistory.toEntity(): UpdateHistoryEntity =
    UpdateHistoryEntity(
        id = id,
        packageName = packageName,
        appName = appName,
        repoOwner = repoOwner,
        repoName = repoName,
        fromVersion = fromVersion,
        toVersion = toVersion,
        updatedAt = updatedAt,
        updateSource = updateSource,
        success = success,
        errorMessage = errorMessage,
    )

fun UpdateHistoryEntity.toDomain(): UpdateHistory =
    UpdateHistory(
        id = id,
        packageName = packageName,
        appName = appName,
        repoOwner = repoOwner,
        repoName = repoName,
        fromVersion = fromVersion,
        toVersion = toVersion,
        updatedAt = updatedAt,
        updateSource = updateSource,
        success = success,
        errorMessage = errorMessage,
    )
