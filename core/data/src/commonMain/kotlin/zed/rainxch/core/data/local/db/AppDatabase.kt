package zed.rainxch.core.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import zed.rainxch.core.data.local.db.dao.CacheDao
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.data.local.db.dao.FavoriteRepoDao
import zed.rainxch.core.data.local.db.dao.HiddenRepoDao
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.SearchHistoryDao
import zed.rainxch.core.data.local.db.dao.SeenRepoDao
import zed.rainxch.core.data.local.db.dao.SigningFingerprintDao
import zed.rainxch.core.data.local.db.dao.StarredRepoDao
import zed.rainxch.core.data.local.db.dao.UpdateHistoryDao
import zed.rainxch.core.data.local.db.entities.CacheEntryEntity
import zed.rainxch.core.data.local.db.entities.ExternalLinkEntity
import zed.rainxch.core.data.local.db.entities.FavoriteRepoEntity
import zed.rainxch.core.data.local.db.entities.HiddenRepoEntity
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.data.local.db.entities.SearchHistoryEntity
import zed.rainxch.core.data.local.db.entities.SeenRepoEntity
import zed.rainxch.core.data.local.db.entities.SigningFingerprintEntity
import zed.rainxch.core.data.local.db.entities.StarredRepositoryEntity
import zed.rainxch.core.data.local.db.entities.UpdateHistoryEntity

@Database(
    entities = [
        InstalledAppEntity::class,
        FavoriteRepoEntity::class,
        UpdateHistoryEntity::class,
        StarredRepositoryEntity::class,
        CacheEntryEntity::class,
        SeenRepoEntity::class,
        SearchHistoryEntity::class,
        ExternalLinkEntity::class,
        SigningFingerprintEntity::class,
        HiddenRepoEntity::class,
    ],
    version = 18,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract val installedAppDao: InstalledAppDao
    abstract val favoriteRepoDao: FavoriteRepoDao
    abstract val updateHistoryDao: UpdateHistoryDao
    abstract val starredReposDao: StarredRepoDao
    abstract val cacheDao: CacheDao
    abstract val seenRepoDao: SeenRepoDao
    abstract val searchHistoryDao: SearchHistoryDao
    abstract val externalLinkDao: ExternalLinkDao
    abstract val signingFingerprintDao: SigningFingerprintDao
    abstract val hiddenRepoDao: HiddenRepoDao
}
