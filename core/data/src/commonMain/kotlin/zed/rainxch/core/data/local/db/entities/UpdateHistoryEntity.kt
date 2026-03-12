package zed.rainxch.core.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import zed.rainxch.core.domain.model.InstallSource

@Entity(tableName = "update_history")
data class UpdateHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val repoOwner: String,
    val repoName: String,
    val fromVersion: String,
    val toVersion: String,
    val updatedAt: Long,
    val updateSource: InstallSource,
    val success: Boolean = true,
    val errorMessage: String? = null,
)
