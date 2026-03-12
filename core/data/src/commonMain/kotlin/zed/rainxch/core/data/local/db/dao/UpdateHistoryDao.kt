package zed.rainxch.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.local.db.entities.UpdateHistoryEntity

@Dao
interface UpdateHistoryDao {
    @Query("SELECT * FROM update_history ORDER BY updatedAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<UpdateHistoryEntity>>

    @Query("SELECT * FROM update_history WHERE packageName = :packageName ORDER BY updatedAt DESC")
    fun getHistoryForApp(packageName: String): Flow<List<UpdateHistoryEntity>>

    @Insert
    suspend fun insertHistory(history: UpdateHistoryEntity)

    @Query("DELETE FROM update_history WHERE updatedAt < :timestamp")
    suspend fun deleteOldHistory(timestamp: Long)
}
