# Komi Store

Cross-platform app store for GitHub + Codeberg + Forgejo releases. **Kotlin Multiplatform** + **Compose Multiplatform**. Android (min API 26) + Desktop (JVM: Win/macOS/Linux). Package `zed.rainxch.githubstore`. Version 1.8.3 (code 18). Target SDK 36.

## Build

```bash
./gradlew :composeApp:assembleDebug                                    # Android
./gradlew :composeApp:run                                              # Desktop dev
./gradlew :composeApp:packageExe :composeApp:packageMsi                # Win installer
./gradlew :composeApp:packageDmg :composeApp:packagePkg                # macOS
./gradlew :composeApp:packageDeb :composeApp:packageRpm                # Linux
./gradlew build                                                        # full
```

JDK 21+. Android SDK for Android.

## Structure

```text
composeApp/            # entry points, navigation, DI wiring (commonMain / androidMain / jvmMain)
core/
  domain/              # interfaces, models, use cases (no framework deps)
  data/                # repos, Ktor, Room, Koin, platform impls
  presentation/        # Material 3 theme + reusable components + 14-locale strings
feature/
  apps auth details dev-profile favourites homeP profile recently-viewed search starred tweaks
build-logic/convention/  # convention plugins
```

Each feature: up to 3 sub-modules (`domain/`, `data/`, `presentation/`). `favourites`, `starred`, `recently-viewed` are presentation-only.

## Architecture

Clean Architecture + MVVM. Layers: **Domain** (contracts), **Data** (Ktor + Room + Koin DI), **Presentation** (ViewModels with `StateFlow`/`Channel`, Compose).

### State pattern (every screen)

```kotlin
class XViewModel : ViewModel() {
    private val _state = MutableStateFlow(XState())
    val state = _state.asStateFlow()                  // or .stateIn(WhileSubscribed)
    private val _events = Channel<XEvent>()
    val events = _events.receiveAsFlow()
    fun onAction(action: XAction) { ... }
}
```

`State` = data class. `Action` = sealed (user input). `Event` = sealed (one-off effects).

### Navigation

`@Serializable` sealed interface `GithubStoreGraph` in `composeApp/.../app/navigation/`. Routes: `HomeScreen`, `SearchScreen`, `AuthenticationScreen`, `ProfileScreen`, `TweaksScreen`, `FavouritesScreen`, `StarredReposScreen`, `RecentlyViewedScreen`, `AppsScreen`, `OnboardingScreen`, `ExternalImportScreen`, `MirrorPickerScreen`, `StarredPickerScreen`, `SkippedUpdatesScreen`, `HiddenRepositoriesScreen`, `WhatsNewHistoryScreen`, `AnnouncementsScreen`, `HostTokensScreen`, `DetailsScreen(repositoryId, owner, repo, isComingFromUpdate, sourceHost)`, `DeveloperProfileScreen(username)`. `DetailsScreen.sourceHost` is non-null for Codeberg / Forgejo / custom-forge repos — routes all `DetailsRepository` calls through `ForgejoClientRegistry` instead of the GitHub-backed default path.

### DI

Koin. Feature modules in `data/di/SharedModule.kt`. ViewModels in `composeApp/.../app/di/ViewModelsModule.kt` (`viewModelOf(::X)` or explicit `viewModel { ... }`). Wired in `initKoin.kt`.

## Core repositories (`core/domain`)

`FavouritesRepository`, `StarredRepository`, `InstalledAppsRepository`, `SeenReposRepository`, `HiddenReposRepository`, `SearchHistoryRepository`, `TweaksRepository`, `AuthenticationState`, `ThemesRepository`, `ProxyRepository`, `RateLimitRepository`, `ExternalImportRepository`, `TelemetryRepository`, `HostTokenRepository` (per-host PATs, KSafe-encrypted). Network: `ForgejoApiClient` + `ForgejoClientRegistry` (per-host Ktor clients, thread-safe via Mutex, proxy-aware, closes cached engines on shutdown / proxy change). Util: `AssetVariant` (token/glob/stem fingerprinting), `assetPlatformOf`, `RepoIdCodec` (23-bit host fingerprint + 40-bit raw id packed into the existing 64-bit `repoId` slot — sign bit = foreign source), `RepositoryUrlParser` (recognises GitHub + Codeberg + gitea.com + git.disroot.org + user-added forge hosts). System interfaces: `Installer`, `InstallerStatusProvider`, `PackageMonitor`, `SystemInstallSerializer`.

## Tech

Kotlin 2.3.10, Compose Multiplatform 1.10.3, Ktor 3.4.0, Room 2.8.4, Koin 4.1.1, kotlinx.serialization 1.10.0, DataStore 1.2.0, Landscapist 2.9.5, Kermit 2.0.8, MOKO Permissions 0.20.1, Navigation Compose 2.9.2, multiplatform-markdown-renderer 0.39.2, Shizuku 13.1.5, WorkManager 2.11.1, kotlinx.datetime 0.7.1. Versions in `gradle/libs.versions.toml`.

## Convention plugins (`build-logic/convention/`)

`convention.kmp.library` (domain/data), `convention.cmp.library` (core/presentation), `convention.cmp.feature` (feature presentation), `convention.cmp.application` (main app), `convention.room`, `convention.buildkonfig`.

## Adding a feature

1. `feature/<name>/{domain,data,presentation}/` with appropriate convention plugin
2. `include` in `settings.gradle.kts`
3. Domain interfaces → impl + Koin module in `data/di/SharedModule.kt` → ViewModel + Screen
4. Route in `GithubStoreGraph.kt` + wire in `AppNavigation.kt` + register Koin in `initKoin.kt`

## Key configuration

- **GitHub OAuth:** `GITHUB_CLIENT_ID` in `local.properties`. Deep links: `githubstore://auth` (web-OAuth handoff), `githubstore://callback` (legacy device-flow leftover), `githubstore://repo`, `githubstore://apps`.
- **Shizuku (Android):** silent install via `ShizukuProvider` → AIDL → `pm install -S`. Fallback to standard installer on failure.
- **Desktop logs:** `CrashReporter` (first line of `DesktopApp.main`) tees stdout/stderr to rotating `session.log` + writes `crash-<ts>.log` on uncaught. Paths: `~/Library/Logs/GitHub-Store/` (macOS), `%LOCALAPPDATA%/GitHub-Store/logs/` (Win), `$XDG_STATE_HOME/GitHub-Store/logs/` (Linux). Android = Logcat.
- **macOS distribution:** Homebrew cask in tap `komi-store/komi-store` (separate repo `homebrew-komi-store`). `brew install --cask komi-store`. Unsigned at present — user must `xattr -dr com.apple.quarantine /Applications/Komi-Store.app` after install. CI builds `.dmg` + `.pkg` on every push to `generate-installers`; tap cask updates automatically on release.
- **`X-GitHub-Token` header:** Client attaches when `TokenStore.currentToken()` is non-null on `/v1/search`, `/v1/search/explore`, `/v1/repo`, `/v1/releases`, `/v1/readme`, `/v1/user`. Backend re-sends as `Authorization: token $token` to GitHub. Without it, backend round-robins a 4-token service pool. Upstream 401 remapped to backend `502` (handled like "GitHub unreachable" — fall back via `shouldFallbackToGithubOrRethrow`). `429` = no fallback (same wall), only backoff. `UnauthorizedInterceptor` only on direct-GitHub client; `AuthenticationStateImpl` debounces consecutive 401s by token snapshot.
- **Auth flow (web-OAuth-first):** Primary path is web OAuth with PKCE + handoff. `feature/auth/data/crypto/PkceGenerator` mints `(state, codeVerifier, codeChallenge)`; `WebAuthApi.register` POSTs verifier + challenge + state to `https://github-store.org/auth/register` (Cloudflare Worker stashes them in Workers KV) and returns `authUrl`. User opens it, authorizes on `github.com`, GitHub redirects to `github-store.org/auth/callback?code&state` where the Worker exchanges the code via `api.github-store.org` (backend stores `(handoffId → access_token)` for 60s in Postgres with atomic `DELETE…RETURNING`), then bounces back to `githubstore://auth?h=<handoffId>`. App reads handoff via `WebAuthApi.consumeHandoff` (GETDEL semantics). Secondary path: device flow via backend `/v1/auth/device/start` + `/poll`, `AuthPath` (`Backend`|`Direct`) tracked in `SavedStateHandle`, only escalates `Backend → Direct` on infra errors. Tertiary: paste a Personal Access Token (`signInWithPat` — validates against `/user`, persists optimistically when GitHub unreachable). Backend rate limits: 10 device-starts/hr, 200 device-polls/hr per IP. Endpoints in `core/data/network/BackendEndpoints.kt` (`BACKEND_ORIGIN`, `WEB_ORIGIN`).
- **Windows installer signing (SignPath Foundation):** CI workflow `.github/workflows/build-desktop-platforms.yml` job `sign-windows` after every push to `generate-installers` branch. Action pinned to commit SHA (not `@v2`). Secrets: `SIGNPATH_API_TOKEN`, `SIGNPATH_ORGANIZATION_ID` (`1ecf111e-...`). Variable `SIGNPATH_SIGNING_POLICY_SLUG` = `test-signing` until prod cert issued; flip to `release-signing`. Project slug `GitHub-Store`, artifact config slug `initial`. Unsigned artifact deleted post-sign; only `windows-installers-signed` reaches the draft release.
- **WinGet publish:** `.github/workflows/winget-publish.yml` fires on `release: [released]`. Action `vedantmgoyal9/winget-releaser@main`. Secret `WINGET_TOKEN` = PAT with `Contents+Pull requests: write` on `komi-store/winget-pkgs` (fork of `microsoft/winget-pkgs`). Pin `fork-user: komi-store` explicitly so the action doesn't infer from token owner.
- **Forges (Codeberg / Forgejo / Gitea):** `ForgejoApiClient` per host (60s req / 30s connect+socket timeouts, exponential retry on 5xx + IOException). `ForgejoClientRegistry.clientFor(host)` cached + Mutex-guarded. Direct-to-forge — no backend mediator. `RepoIdCodec` packs host fingerprint into `repoId` so the existing GitHub-shaped schema survives. README via `/contents/README.md?ref={branch}` (Forgejo has NO `/readme` endpoint). License sniffed from `/contents/LICENSE` regex against SPDX headers. Downloads aggregated by summing `asset.download_count` across releases.
- **Per-host PATs:** `HostTokenRepository` stores `{host, token, label, createdAt}` rows AES-256-GCM encrypted via KSafe. `HostTokenInterceptor` (Ktor plugin) injects `Authorization: token $pat` on matched host. `HostNames.apiHostToTokenHost` maps `api.github.com → github.com` so the GitHub-direct client looks up the right PAT. UI at `Tweaks → Access Tokens` (`HostTokensScreen`).
- **KSafe:** AES-256-GCM with hardware-backed Keystore on Android. Wraps every persisted credential / pref via `core/data/secure/KSafeSafe.kt` extension funcs (`safeGet`, `safePut`, `safeDelete`, `safeGetFlow`) — surface log + return null/false on transient failure instead of throwing through coroutine scopes.
- **Translation providers:** `TranslationProvider` enum = `GOOGLE`, `YOUDAO`, `LIBRE_TRANSLATE`, `DEEPL`, `MICROSOFT`. Each per-provider config persisted via `TweaksRepository` (KSafe-encrypted). `TranslationRepositoryImpl.resolveTranslator()` picks the impl. LibreTranslate defaults to the bundled `translate.disroot.org` mirror when user URL pref blank. DeepL auto-routes `:fx`-suffixed keys to `api-free.deepl.com`. Microsoft uses No-Trace by default — text never stored, never used for training.
- **Gradle:** Config + build cache enabled. 4GB Gradle heap, 3GB Kotlin daemon. Official Kotlin style.

## Active skills (apply on matching domain)

- **caveman** — session default, terse output.
- **karpathy-guidelines** — anti-overcomplication, minimal diffs, surface assumptions, verifiable success criteria. Every coding task.
- **one-skill-to-rule-them-all** — watch for skill-capture opportunities during multi-step work.
- **gsd-inbox** - Triage open GitHub issues + PRs against templates. Our exact pattern — automate the "check issue #N, draft reply, ship fix" loop.
- **gsd-ship** - Create PR + review + prep for merge. Every task ends here.
- **gsd-quick** - Trivial task with atomic commits + state tracking. Matches our small-commit policy.
- **gsd-debug** - Systematic debugging with persistent state across context resets. For bug-hunt cycles.
- **android-* skills** (`~/.claude/skills/android/`) — auto-fire by description match; apply when in matching domain:
  - `android-compose-ui` — composables, recomposition, animations, modifiers, design system
  - `android-data-layer` — repos, DTOs, Room, Ktor, mappers
  - `android-di-koin` — Koin module setup, ViewModel injection
  - `android-error-handling` — Result wrapper, typed errors
  - `android-module-structure` — feature-layered modules, convention plugins
  - `android-navigation` — type-safe Compose nav
  - `android-presentation-mvi` — State/Action/Event, Root/Screen split, UiText, SavedStateHandle
  - `android-testing` — testing patterns

## Conventions

- Packages `zed.rainxch.{module}.{layer}`
- Private state fields prefix `_state`
- Sealed routes/actions/events
- Repository pattern: interface in `domain/`, impl in `data/`
- Source sets: `commonMain` shared, `androidMain`, `jvmMain`
- **No KDoc, no inline comments** unless the user explicitly asks. No function/class docs. Inline only for non-obvious invariants, tricky concurrency, workarounds. Applies globally.
- Feature-specific guidance in each `feature/*/CLAUDE.md`

## Approach

- Read existing files before writing. Don't re-read unless changed.
- Thorough in reasoning, concise in output.
- Skip files over 100KB unless required.
- No sycophantic openers or closing fluff.
- No emojis or em-dashes.
- Do not guess APIs, versions, flags, commit SHAs, or package names. Verify by reading code or docs before asserting, researching if necessary.
