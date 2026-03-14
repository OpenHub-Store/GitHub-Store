# CLAUDE.md - Details Feature

## Purpose

Repository detail screen. Displays full info for a GitHub repository including owner profile, stats, releases with download links, readme rendering (with translation support), and installation/update flow. This is the most complex feature module.

## Module Structure

```
feature/details/
├── domain/
│   ├── model/
│   │   ├── ReleaseCategory.kt        # Release filtering categories
│   │   ├── RepoStats.kt              # Stars, forks, open issues
│   │   ├── SupportedLanguage.kt      # Languages for readme translation
│   │   └── TranslationResult.kt      # Translation response model
│   └── repository/
│       ├── DetailsRepository.kt      # Repo, releases, readme, stats, user profile
│       └── TranslationRepository.kt  # Readme translation
├── data/
│   ├── di/SharedModule.kt            # Koin: detailsModule
│   ├── repository/
│   │   ├── DetailsRepositoryImpl.kt  # API calls + readme localization
│   │   └── TranslationRepositoryImpl.kt  # Translation API integration
│   ├── model/ReadmeAttempt.kt        # Readme fetch attempt tracking
│   └── utils/
│       ├── ReadmeLocalizationHelper.kt   # Find readme in user's language
│       └── preprocessMarkdown.kt     # Markdown preprocessing
└── presentation/
    ├── DetailsViewModel.kt            # State management for detail screen
    ├── DetailsState.kt                # Repo, releases, readme, download progress, etc.
    ├── DetailsAction.kt               # Load, download, install, favourite, star, etc.
    ├── DetailsEvent.kt                # Navigation, toast events
    ├── DetailsRoot.kt                 # Main composable
    ├── model/
    │   ├── DownloadStage.kt           # Download progress tracking
    │   ├── InstallLogItem.kt          # Installation log entries
    │   ├── LogResult.kt               # Log result types
    │   ├── ShowDowngradeWarning.kt    # Downgrade confirmation model
    │   ├── SupportedLanguages.kt      # UI language list
    │   ├── TranslationState.kt        # Translation UI state
    │   └── TranslationTarget.kt       # Translation target selection
    ├── components/
    │   ├── AppHeader.kt               # App icon, name, developer
    │   ├── LanguagePicker.kt          # Readme translation language selector
    │   ├── ReleaseAssetsPicker.kt     # Asset selection for download
    │   ├── SmartInstallButton.kt      # Context-aware install/update/open button
    │   ├── StatItem.kt                # Individual stat display
    │   ├── TranslationControls.kt     # Translation UI controls
    │   ├── VersionPicker.kt           # Release version selector
    │   ├── VersionTypePicker.kt       # Stable/pre-release filter
    │   └── sections/
    │       ├── About.kt               # Description & topics
    │       ├── Header.kt              # Top header section
    │       ├── Logs.kt                # Installation/download logs
    │       ├── Owner.kt               # Repository owner info
    │       ├── ReportIssue.kt         # Issue reporting section
    │       ├── Stats.kt               # Stars, forks, issues
    │       └── WhatsNew.kt            # Release changelog
    ├── states/ErrorState.kt           # Error display composable
    └── utils/
        ├── LocalTopbarLiquidState.kt
        ├── LogResultAsText.kt         # Log result formatting
        ├── MarkdownImageTransformer.kt  # Transform relative image URLs
        ├── MarkdownUtils.kt           # Markdown preprocessing
        └── SystemArchitecture.kt      # Platform architecture detection
```

## Key Interfaces

```kotlin
interface DetailsRepository {
    suspend fun getRepositoryById(id: Long): GithubRepoSummary
    suspend fun getRepositoryByOwnerAndName(owner: String, name: String): GithubRepoSummary
    suspend fun getLatestPublishedRelease(owner: String, repo: String, defaultBranch: String): GithubRelease?
    suspend fun getAllReleases(owner: String, repo: String, defaultBranch: String): List<GithubRelease>
    suspend fun getReadme(owner: String, repo: String, defaultBranch: String): Triple<ReadmeContent, LanguageCode?, ReadmePath>?
    suspend fun getRepoStats(owner: String, repo: String): RepoStats
    suspend fun getUserProfile(username: String): GithubUserProfile
}

interface TranslationRepository {
    suspend fun translate(text: String, targetLanguage: SupportedLanguage): TranslationResult
}
```

## Navigation

Route: `GithubStoreGraph.DetailsScreen(repositoryId: Long, owner: String, repo: String, isComingFromUpdate: Boolean)`

Can be reached via repo ID or owner+name (for deep links). Falls back to owner+name lookup if `repositoryId == -1`. `isComingFromUpdate` flag indicates navigation from an update notification.

## Implementation Notes

- Readme supports localization: `ReadmeLocalizationHelper` tries to find readme in user's language first
- Readme translation: `TranslationRepository` translates readme content to user's chosen language via `LanguagePicker`
- Markdown rendering uses `multiplatform-markdown-renderer` with custom `MarkdownImageTransformer` for relative URLs
- Download flow tracks stages via `DownloadStage` (idle → downloading → installing → done)
- `SmartInstallButton` changes behavior based on installed/update-available/not-installed state
- `ReleaseAssetsPicker` allows selecting specific assets; `VersionTypePicker` filters stable vs pre-release
- Version picker allows selecting specific releases for download
- Downgrade warning shown when installing an older version than currently installed
- Integrates with `FavouritesRepository`, `StarredRepository`, `InstalledAppsRepository` from core
- Uses `Downloader` and `Installer` interfaces from core/domain for platform-specific download/install
- On Android, install may use Shizuku (silent) or standard system installer depending on user preference in profile settings
