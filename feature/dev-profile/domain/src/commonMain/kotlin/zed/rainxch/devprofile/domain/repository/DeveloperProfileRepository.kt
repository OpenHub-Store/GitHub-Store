package zed.rainxch.devprofile.domain.repository

import zed.rainxch.devprofile.domain.model.DeveloperProfile
import zed.rainxch.devprofile.domain.model.DeveloperRepository

interface DeveloperProfileRepository {
    suspend fun getDeveloperProfile(username: String): Result<DeveloperProfile>

    suspend fun getDeveloperRepositories(username: String): Result<List<DeveloperRepository>>
}
