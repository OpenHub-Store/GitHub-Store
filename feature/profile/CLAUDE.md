# CLAUDE.md - Profile Feature

## Purpose

User profile screen combining account management, appearance settings, network proxy configuration, installer method selection (including Shizuku silent install on Android), and sponsor/about info. Replaces the former `feature/settings/` module. Accessible from the bottom navigation bar.

## Module Structure

```
feature/profile/
├── domain/
│   ├── model/UserProfile.kt          # User profile data model
│   └── repository/ProfileRepository.kt  # Auth state, user, logout, cache
├── data/
│   ├── di/SharedModule.kt            # Koin: profileModule
│   ├── repository/ProfileRepositoryImpl.kt  # Implementation
│   └── mappers/UserProfileMappers.kt # DTO → domain model mappers
└── presentation/
    ├── ProfileViewModel.kt            # State management for profile screen
    ├── ProfileState.kt                # User, theme, proxy, installer, Shizuku status
    ├── ProfileAction.kt               # Theme, logout, proxy, installer, Shizuku actions
    ├── ProfileEvent.kt                # One-off events (navigation, etc.)
    ├── ProfileRoot.kt                 # Main composable (LazyColumn of sections)
    ├── SponsorScreen.kt               # Sponsor/donation screen
    ├── model/ProxyType.kt             # NONE, HTTP, SOCKS
    └── components/
        ├── LogoutDialog.kt            # Logout confirmation dialog
        ├── SectionText.kt             # Section header text component
        └── sections/
            ├── Account.kt             # Login/logout actions
            ├── AccountSection.kt      # Account info display
            ├── Appearance.kt          # Theme color, font, dark mode, AMOLED
            ├── Installation.kt        # Installer type selector (Default/Shizuku) with status
            ├── Network.kt             # Proxy configuration (type, host, port, auth)
            ├── Options.kt             # Favourites, starred, clipboard detection
            ├── Others.kt              # Help, clear cache, version info
            ├── ProfileSection.kt      # User avatar, name, bio
            └── SettingsSection.kt     # Settings group container
```

## Key Interfaces

```kotlin
interface ProfileRepository {
    val isUserLoggedIn: Flow<Boolean>
    fun getUser(): Flow<UserProfile?>
    fun getVersionName(): String
    suspend fun logout()
    fun observeCacheSize(): Flow<Long>
    suspend fun clearCache()
}
```

## State

```kotlin
data class ProfileState(
    val userProfile: UserProfile?,
    val selectedThemeColor: AppTheme,
    val selectedFontTheme: FontTheme,
    val isLogoutDialogVisible: Boolean,
    val isUserLoggedIn: Boolean,
    val isAmoledThemeEnabled: Boolean,
    val isDarkTheme: Boolean?,
    val versionName: String,
    val proxyType: ProxyType,
    val proxyHost: String, val proxyPort: String,
    val proxyUsername: String, val proxyPassword: String,
    val isProxyPasswordVisible: Boolean,
    val autoDetectClipboardLinks: Boolean,
    val cacheSize: String,
    val installerType: InstallerType,          // DEFAULT or SHIZUKU
    val shizukuAvailability: ShizukuAvailability  // UNAVAILABLE, NOT_RUNNING, PERMISSION_NEEDED, READY
)
```

## Navigation

Routes:
- `GithubStoreGraph.ProfileScreen` (data object, no params) — main profile screen
- `GithubStoreGraph.SponsorScreen` (data object, no params) — sponsor/donation page

## Implementation Notes

- **Installation section** (Android only): Radio-button group to choose between Default (standard system dialog) and Shizuku (silent install). Uses `selectableGroup` + `selectable` with `Role.RadioButton` for accessibility.
- **Shizuku status**: Observes `InstallerStatusProvider.shizukuAvailability` flow to show real-time status (not installed, not running, permission needed, ready). Grant permission button calls `InstallerStatusProvider.requestShizukuPermission()`.
- **Installer preference** stored via `ThemesRepository.setInstallerType()` / `getInstallerType()` (persisted in DataStore).
- **Proxy settings**: Supports HTTP and SOCKS proxies with optional authentication. Saved via `ProxyRepository` from core/domain.
- **Appearance**: Theme color (`AppTheme` enum), font (`FontTheme`), dark mode toggle, AMOLED black toggle.
- **Account**: Shows GitHub user profile when logged in; login/logout with confirmation dialog.
- **Cache management**: Displays cache size and allows clearing.
- **BuildKonfig**: Uses `convention.buildkonfig` plugin for build-time configuration.
- ViewModel depends on: `ProfileRepository`, `ThemesRepository`, `ProxyRepository`, `InstallerStatusProvider`, `Platform`
