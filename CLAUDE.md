# CLAUDE.md - GitHub Store

## Project Overview

GitHub Store is a cross-platform app store for GitHub releases, built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. Targets **Android** (min API 26) and **Desktop** (Windows, macOS, Linux via JVM).

Package: `zed.rainxch.githubstore` | Version: 1.6.2 (code 13) | Target SDK: 36

## Build & Run Commands

```bash
# Android
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:assembleRelease

# Desktop (run in dev mode)
./gradlew :composeApp:run

# Desktop installers
./gradlew :composeApp:packageExe :composeApp:packageMsi   # Windows
./gradlew :composeApp:packageDmg :composeApp:packagePkg   # macOS
./gradlew :composeApp:packageDeb :composeApp:packageRpm   # Linux

# Full build check
./gradlew build
```

**Requirements:** JDK 21+ (Temurin recommended), Android SDK for Android builds.

## Project Structure

```
composeApp/                          # Main app module (entry points, navigation, DI wiring)
  src/commonMain/                    # Shared UI & app wiring
  src/androidMain/                   # Android entry point (MainActivity)
  src/jvmMain/                       # Desktop entry point (DesktopApp.kt)
core/
  domain/                            # Shared interfaces, models, use cases (no framework deps)
  data/                              # Shared repos, networking (Ktor), database (Room), DI
  presentation/                      # Shared theming (Material 3) & reusable UI components
feature/
  apps/                              # Installed applications management
  auth/                              # GitHub OAuth device flow authentication
  details/                           # Repository details, releases, readme, downloads
  dev-profile/                       # Developer/user profile display
  favourites/                        # Saved favorite repositories (presentation-only)
  home/                              # Main discovery screen (trending, hot, popular)
  profile/                           # User profile, settings, appearance, proxy, Shizuku installer
  search/                            # Repository search with filters
  starred/                           # Starred repositories (presentation-only)
build-logic/convention/              # Custom Gradle convention plugins
```

Each feature has up to 3 sub-modules: `domain/` (interfaces & models), `data/` (implementations & DI), `presentation/` (screens & ViewModels). Some features (favourites, starred) are presentation-only and use core repositories directly.

## Architecture

**Clean Architecture + MVVM** with strict layer separation per feature module:

- **Domain** - Repository interfaces, models, use cases (no framework dependencies)
- **Data** - Repository implementations, Ktor API clients, Room DAOs, DTOs, mappers
- **Presentation** - ViewModels with `StateFlow`/`Channel`, Compose screens

### State Management Pattern

Every screen follows the same State/Action/Event pattern:

```kotlin
class XViewModel : ViewModel() {
    private val _state = MutableStateFlow(XState())
    val state = _state.asStateFlow()  // or .stateIn() with WhileSubscribed

    private val _events = Channel<XEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: XAction) { ... }
}
```

- `State` - data class holding all UI state
- `Action` - sealed interface for user input (clicks, refreshes, etc.)
- `Event` - sealed interface for one-off effects (navigation, toasts, scroll)

### Navigation

Type-safe navigation using `@Serializable` sealed interface `GithubStoreGraph`:

```
HomeScreen, SearchScreen, AuthenticationScreen, ProfileScreen,
FavouritesScreen, StarredReposScreen, AppsScreen, SponsorScreen
DetailsScreen(repositoryId, owner, repo, isComingFromUpdate)
DeveloperProfileScreen(username)
```

Routes defined in `composeApp/.../app/navigation/GithubStoreGraph.kt`, wired in `AppNavigation.kt`.

### Dependency Injection

**Koin** - modules defined in each feature's `data/di/SharedModule.kt`, registered in `composeApp/.../app/di/initKoin.kt`. ViewModels injected via `koinViewModel()`.

### Core Modules

| Module | Purpose | Key Contents |
|--------|---------|--------------|
| `core/domain` | Shared contracts | Repository interfaces (`FavouritesRepository`, `StarredRepository`, `InstalledAppsRepository`, `ThemesRepository`, `ProxyRepository`, `RateLimitRepository`), models (`GithubRepoSummary`, `GithubRelease`, `InstalledApp`, `ProxyConfig`, `InstallerType`, `ShizukuAvailability`), system interfaces (`Installer`, `InstallerInfoExtractor`, `InstallerStatusProvider`, `PackageMonitor`) |
| `core/data` | Shared implementations | `HttpClientFactory` (Ktor + interceptors), `AppDatabase` (Room), `ProxyManager`, `TokenStore`, `LocalizationManager`, platform-specific clients (OkHttp for Android, CIO for Desktop), Shizuku integration (Android: `ShizukuServiceManager`, `ShizukuInstallerWrapper`, `ShizukuInstallerServiceImpl`, `AndroidInstallerStatusProvider`; Desktop: `DesktopInstallerStatusProvider`) |
| `core/presentation` | Shared UI | `GithubStoreTheme` (Material 3), reusable components (`RepositoryCard`, `GithubStoreButton`), formatting utils, localized strings (11 languages) |

## Tech Stack

| Area | Library | Version |
|------|---------|---------|
| Language | Kotlin | 2.3.10 |
| UI | Compose Multiplatform | 1.10.1 |
| HTTP | Ktor | 3.4.0 |
| Database | Room | 2.8.4 |
| DI | Koin | 4.1.1 |
| Serialization | Kotlinx Serialization | 1.10.0 |
| Preferences | DataStore | 1.2.0 |
| Image Loading | Landscapist (Coil3) | 2.9.5 |
| Logging | Kermit | 2.0.8 |
| Permissions | MOKO Permissions | 0.20.1 |
| Navigation | Navigation Compose | 2.9.2 |
| Markdown | Multiplatform Markdown Renderer | 0.39.2 |
| Shizuku | Shizuku API | 13.1.5 |
| Background Work | WorkManager | 2.11.1 |
| Date/Time | Kotlinx Datetime | 0.7.1 |

All versions managed in `gradle/libs.versions.toml` (Version Catalog).

## Convention Plugins

Custom Gradle plugins in `build-logic/convention/` standardize module setup:

| Plugin | Use For |
|--------|---------|
| `convention.kmp.library` | KMP shared library modules (domain, data) |
| `convention.cmp.library` | Compose Multiplatform library modules (core/presentation) |
| `convention.cmp.feature` | Feature presentation modules (auto-adds Compose + Koin + core:presentation) |
| `convention.cmp.application` | Main app module |
| `convention.room` | Room database modules |
| `convention.buildkonfig` | Build-time config (API keys from local.properties) |

## Adding a New Feature

1. Create `feature/<name>/domain/`, `feature/<name>/data/`, `feature/<name>/presentation/`
2. Add `build.gradle.kts` in each using the appropriate convention plugin
3. Add `include` entries in `settings.gradle.kts`
4. Define domain interfaces/models in `domain/`
5. Implement repository + Koin DI module in `data/di/SharedModule.kt`
6. Create ViewModel (State/Action/Event pattern) and Screen in `presentation/`
7. Add navigation route to `GithubStoreGraph.kt` and wire in `AppNavigation.kt`
8. Register the Koin module in `initKoin.kt`

## Key Configuration

- **GitHub OAuth:** Set `GITHUB_CLIENT_ID` in `local.properties`. Callback URL: `githubstore://callback`. Deep link: `githubstore://repo`
- **Shizuku (Android):** Optional silent install via `ShizukuProvider` (registered in AndroidManifest). Requires Shizuku app running with ADB or root. AIDL service passes APK via `ParcelFileDescriptor` to `pm install -S`. Falls back to standard installer on failure.
- **Gradle properties:** Config cache enabled, build cache enabled, 4GB Gradle heap, 3GB Kotlin daemon heap
- **Code style:** Official Kotlin style (`kotlin.code.style=official`)

## Coding Conventions

- Packages follow `zed.rainxch.{module}.{layer}` pattern
- Private state properties use underscore prefix: `_state`
- Sealed classes/interfaces for type-safe navigation routes, actions, events
- Repository pattern: interface in `domain/`, implementation in `data/`
- Composition over inheritance via Koin DI
- Source sets: `commonMain` for shared, `androidMain` for Android, `jvmMain` for Desktop
- Feature CLAUDE.md files exist in each `feature/` directory for module-specific guidance
