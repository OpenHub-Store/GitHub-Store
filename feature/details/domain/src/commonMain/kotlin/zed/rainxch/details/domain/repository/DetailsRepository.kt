package zed.rainxch.details.domain.repository

import zed.rainxch.core.domain.model.GithubRelease
import zed.rainxch.core.domain.model.GithubRepoSummary
import zed.rainxch.core.domain.model.GithubUserProfile
import zed.rainxch.details.domain.model.RepoStats

typealias ReadmeContent = String
typealias ReadmePath = String
typealias LanguageCode = String

interface DetailsRepository {
    suspend fun getRepositoryById(id: Long): GithubRepoSummary

    suspend fun getRepositoryByOwnerAndName(
        owner: String,
        name: String,
    ): GithubRepoSummary

    suspend fun getLatestPublishedRelease(
        owner: String,
        repo: String,
        defaultBranch: String,
    ): GithubRelease?

    suspend fun getAllReleases(
        owner: String,
        repo: String,
        defaultBranch: String,
    ): List<GithubRelease>

    suspend fun getReadme(
        owner: String,
        repo: String,
        defaultBranch: String,
    ): Triple<ReadmeContent, LanguageCode?, ReadmePath>?

    suspend fun getRepoStats(
        owner: String,
        repo: String,
    ): RepoStats

    suspend fun getUserProfile(username: String): GithubUserProfile
}
