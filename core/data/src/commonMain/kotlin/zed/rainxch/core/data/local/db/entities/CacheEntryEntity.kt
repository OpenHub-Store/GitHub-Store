package zed.rainxch.core.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_entries")
data class CacheEntryEntity(
    @PrimaryKey
    val key: String,
    val jsonData: String,
    val cachedAt: Long,
    val expiresAt: Long,
)
