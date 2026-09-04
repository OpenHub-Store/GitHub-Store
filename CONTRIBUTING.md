# Contributing to Komi Store

Thanks for your interest in helping out. This document covers everything you need to ship a change — from setting up the project locally to landing a clean PR.

If you're contributing to a sibling repo ([`backend`](https://github.com/komi-store/komi-store-backend) or [`api`](https://github.com/komi-store/komi-store-backend-data)), check that repo's own contributing notes; the rules below are specific to the **GitHub-Store** client app.

---

## Table of contents

- [Code of conduct](#code-of-conduct)
- [Ways to contribute](#ways-to-contribute)
- [Reporting bugs](#reporting-bugs)
- [Suggesting features](#suggesting-features)
- [Local development setup](#local-development-setup)
- [Project layout](#project-layout)
- [Coding conventions](#coding-conventions)
- [Branching and commit style](#branching-and-commit-style)
- [Pull request process](#pull-request-process)
- [Translations](#translations)
- [Release flow](#release-flow)
- [Security disclosures](#security-disclosures)
- [Questions](#questions)

---

## Code of conduct

Everyone interacting with this project — issues, pull requests, Discord, anywhere — is expected to follow our [Code of Conduct](CODE_OF_CONDUCT.md) (Contributor Covenant). Short version: be respectful, assume good intent, disagree about ideas — never about people. Maintainers will enforce it.

---

## Ways to contribute

Plenty of ways to help, even without writing code:

- **Report bugs.** Reproducible reports with logs are gold.
- **Suggest features.** Open an issue describing the user-facing problem first; the implementation can be discussed there.
- **Triage issues.** Reproduce open bugs, ask for missing info, label them.
- **Write code.** Pick up a [`good first issue`](https://github.com/komi-store/komi-store/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) or any [`help wanted`](https://github.com/komi-store/komi-store/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22) issue.
- **Translate strings.** See [Translations](#translations).
- **Test pre-releases.** Watch the repo for new tags and try them out.

---

## Reporting bugs

Two ways:

- **Inside the app: `Profile → Send feedback`.** Auto-fills app version, platform, and installer. Pick the channel (email or GitHub issue) and you're done.
- **On GitHub: [open a bug report](https://github.com/komi-store/komi-store/issues/new?template=bug_report.md).** Tell us what went wrong, how to reproduce, your setup. Logs and screenshots help but aren't required.

If you have logs handy, grab them from:

- **Android:** `adb logcat | grep zed.rainxch.githubstore`
- **Desktop:** `~/Library/Logs/GitHub-Store/session.log` (macOS), `%LOCALAPPDATA%/GitHub-Store/logs/session.log` (Windows), `$XDG_STATE_HOME/GitHub-Store/logs/session.log` (Linux). Crashes also drop a `crash-<timestamp>.log` next to it.

---

## Suggesting features

[Open a feature request](https://github.com/komi-store/komi-store/issues/new?template=feature_request.md). Lead with the **pain**, not the solution: what are you trying to do, where does the app fall short?

For bigger ideas (new screens, new platform, architectural shifts), open the issue first and wait for a quick discussion before writing code.

---

## Local development setup

### Requirements

- **JDK 21** (Temurin recommended). Set `JAVA_HOME` accordingly.
- **Android Studio** Hedgehog or newer with the Kotlin Multiplatform plugin.
- **Android SDK** for Android builds (target API 36, min API 26).
- **Git** with a configured username/email.

### One-time setup

```bash
git clone https://github.com/komi-store/komi-store.git
cd komi-store
```

Create `local.properties` in the repo root:

```properties
sdk.dir=/path/to/Android/sdk
GITHUB_CLIENT_ID=your-oauth-client-id-or-leave-blank-for-local
```

`GITHUB_CLIENT_ID` is only needed if you want to test the GitHub OAuth device-flow login locally. The rest of the app works without it.

### Common commands

```bash
# Android debug build
./gradlew :composeApp:assembleDebug

# Android release build (needs signing config in local.properties)
./gradlew :composeApp:assembleRelease

# Desktop run (dev mode)
./gradlew :composeApp:run

# Desktop installers
./gradlew :composeApp:packageExe :composeApp:packageMsi    # Windows
./gradlew :composeApp:packageDmg :composeApp:packagePkg    # macOS
./gradlew :composeApp:packageDeb :composeApp:packageRpm    # Linux

# Full build check (compile + lint)
./gradlew build

# Format check (ktlint)
./gradlew ktlintCheck

# Auto-format
./gradlew ktlintFormat
```

The first build will pull a lot of dependencies. Subsequent builds reuse the Gradle build cache (enabled in `gradle.properties`).

---

## Project layout

```text
composeApp/                          # Main app entry points + navigation
core/
  domain/                            # Repository interfaces, models, use cases
  data/                              # Repo implementations, Ktor, Room, DI
  presentation/                      # Shared theme + reusable Compose components
feature/
  apps/                              # Installed apps management
  auth/                              # GitHub OAuth device flow
  details/                           # Repo + release details
  dev-profile/                       # Developer / user profile
  favourites/                        # Saved favorites
  home/                              # Discovery (trending / hot / popular)
  profile/                           # User profile, settings, appearance
  search/                            # Search with filters
  starred/                           # Starred repos
build-logic/convention/              # Custom Gradle convention plugins
```

Each `feature/<name>/` typically has up to three sub-modules: `domain/`, `data/`, `presentation/`. Some features (favourites, starred) are presentation-only and consume core repositories directly.

The full architectural rundown lives in [CLAUDE.md](CLAUDE.md). Per-feature notes live in `feature/<name>/CLAUDE.md`.

---

## Coding conventions

We use Kotlin's official style (`kotlin.code.style=official`). CI lints every PR with ktlint. The big rules:

### Architecture

- **Clean Architecture + MVI.** Domain layer has zero framework dependencies. Data layer implements domain interfaces. Presentation layer holds ViewModels and Compose code.
- **State / Action / Event** pattern for every screen:

  ```kotlin
  class XViewModel : ViewModel() {
      private val _state = MutableStateFlow(XState())
      val state = _state.asStateFlow()

      private val _events = Channel<XEvent>()
      val events = _events.receiveAsFlow()

      fun onAction(action: XAction) { ... }
  }
  ```

- **Sealed interfaces** for actions, events, navigation routes.
- **Koin** for DI. Each feature module exposes a Koin module from `data/di/SharedModule.kt`. ViewModels are wired in `composeApp/.../app/di/initKoin.kt` and injected via `koinViewModel()`.
- **Type-safe navigation** via `@Serializable` sealed interface `GithubStoreGraph`.

### Source sets

- `commonMain` — shared code (Compose UI, ViewModels, repository contracts).
- `androidMain` — Android-only platform impls (Shizuku, PackageManager, OkHttp).
- `jvmMain` — Desktop-only platform impls (CIO, file paths, native installers).

### Naming

- Packages: `zed.rainxch.{module}.{layer}`.
- Private state: underscore prefix (`_state`, `_events`).
- Composables: `PascalCase`.
- Boolean state: `isXxx` / `hasXxx` / `canXxx`.

### Comments

- **Don't write KDoc / docstrings** to explain a function unless the function's intent isn't obvious from its signature and body.
- **Inline comments** are reserved for non-obvious invariants, tricky concurrency, third-party-bug workarounds, or counter-intuitive choices. If reading the code answers the "why," delete the comment.
- This applies globally. Reviewers will push back on filler comments.

### Tech stack reminders

- Kotlin 2.3.10 · Compose Multiplatform 1.10.x · Ktor 3.x · Room 2.8.x · Koin 4.x.
- All versions live in `gradle/libs.versions.toml` (Version Catalog). Adding a dep? Add the version + alias there, not directly in the module's `build.gradle.kts`.
- Convention plugins in `build-logic/convention/` standardize module setup. Adding a new module? Pick the right plugin (`convention.kmp.library`, `convention.cmp.library`, `convention.cmp.feature`, etc.) instead of hand-rolling the build script.

---

## Branching and commit style

### Branches

- Default branch: `main`.
- **Never** commit directly to `main`. Open a feature branch first.
- Naming convention (matches what's already in the repo):
  - `feat/<issue#>-<short-slug>` for features.
  - `fix/<short-slug>` for bug fixes.
  - `chore/<short-slug>` for refactors, dep bumps, docs, build cleanup.
  - Examples: `feat/470-sui-support`, `fix/auth-stuck-on-direct-and-dialog-loop`, `chore/drop-legacy-query-hash`.

### Commits

- One commit = one logical change. Don't bundle a refactor with a feature, or two unrelated features in the same commit. When asked to commit a batch of work, split it into the smallest meaningful commits.
- Commit messages are short and imperative. Match the repo's existing style — usually a single sentence under 72 characters:
  - ✅ `fix: clear parked install metadata once the system confirms install`
  - ✅ `feat: support multi-select platform filter on Home`
  - ❌ `fixed bug` / `wip` / `more changes`
- Long bodies are fine for non-trivial changes — explain the *why*, not the *what* (the diff already shows the *what*).
- AI assistants are fine — use whatever helps you ship. Just make sure the commit message + PR description are written in your own voice and the code is one you've read and stand behind. Auto-generated `Co-Authored-By: Claude/Cursor/...` trailers add noise to `git log`; trim them before pushing.

---

## Pull request process

1. **Open or claim an issue** before doing anything substantial. For tiny fixes (typos, one-liners) you can skip this step.
2. **Branch from latest `main`:**

   ```bash
   git fetch origin
   git checkout -b feat/123-some-thing origin/main
   ```

3. **Make the change.** Keep the PR focused; if it grows beyond one logical change, split it into a stack.
4. **Run the local checks:**

   ```bash
   ./gradlew ktlintCheck build
   ```

   (Or the narrower module-specific tasks if you know the change is scoped.)
5. **Push and open a PR** against `main`. Fill out the description with:
   - **What** the change does.
   - **Why** — the user-facing problem or technical motivation.
   - **How to test** — exact steps a reviewer can follow on a clean checkout.
   - **Screenshots** or screen recordings for any UI change. Mandatory for visual diffs.
   - **Linked issue:** `Closes #123` / `Fixes #123`.
6. **Run the local checks yourself before review.** No CI runs against PRs — `./gradlew ktlintCheck build` is your safety net. Don't expect the merge button to catch a broken build.
7. **Work through CodeRabbit's review.** Every PR gets an automated review from [CodeRabbit AI](https://coderabbit.ai). Read each comment, decide whether it's right, and either fix it or reply explaining why you're leaving it. "Looks fine to me" without engaging with the comments is not enough — a maintainer will ask you to address them before merging. CodeRabbit isn't always right; pushing back with reasoning is welcome.
8. **Address human review comments** with follow-up commits (don't force-push during review unless the reviewer asks). Squash on merge happens automatically.
9. **Merge** is performed by maintainers via GitHub's "Squash and merge". Your branch is deleted automatically.

### What gets a PR rejected

- Mixed concerns (refactor + feature + dep bump in one PR).
- New dependencies without justification in the description.
- Visual changes without screenshots.
- Code that ignores the project's existing patterns ("why isn't this an `XViewModel`?", "why did you skip the convention plugin?").
- Empty / vague commit messages or "WIP" commits left in history.

---

## Translations

Komi Store ships in 13 languages. String resources live in:

```text
core/presentation/src/commonMain/composeResources/
  values/strings.xml             # Default (English)
  values-ar/strings-ar.xml       # Arabic
  values-bn/strings-bn.xml       # Bengali
  values-es/strings-es.xml       # Spanish
  values-fr/strings-fr.xml       # French
  values-hi/strings-hi.xml       # Hindi
  values-it/strings-it.xml       # Italian
  values-ja/strings-ja.xml       # Japanese
  values-ko/strings-ko.xml       # Korean
  values-pl/strings-pl.xml       # Polish
  values-ru/strings-ru.xml       # Russian
  values-tr/strings-tr.xml       # Turkish
  values-zh-rCN/strings-zh-rCN.xml  # Chinese (Simplified)
```

To add a translation:

1. Find the missing or new key in `values/strings.xml`.
2. Add the localized version to the matching `values-<lang>/strings-<lang>.xml`.
3. Open a PR titled `i18n: <language> — <short note>` (e.g., `i18n: Spanish — translate APK Inspect strings`).

If you're adding a brand-new locale, ping a maintainer first so we can wire up the locale file and any locale-specific resources.

Don't machine-translate without verification — bad translations are worse than missing ones because they push out fallback English.

---

## Release flow

Releases are cut by maintainers from `main` after a short stabilization window:

1. Bump `versionName` and `versionCode` in `composeApp/build.gradle.kts`.
2. Tag the commit `vX.Y.Z`.
3. Maintainer builds Android with signing key (release) and Desktop (`.exe`, `.msi`, `.dmg`, `.pkg`, `.deb`, `.rpm`, `.appimage`, `.tar.zst`).
4. Release notes are written manually — covering the user-facing changes, not every commit.
5. F-Droid and the in-app updater pick up the release automatically.

You don't normally need to touch this as a contributor; just be aware that landing a feature in `main` doesn't immediately ship — there's a release cadence on top.

---

## Security disclosures

**Do not open public issues for security vulnerabilities.** Use [GitHub's private vulnerability reporting](https://github.com/komi-store/komi-store/security/advisories/new) or email `admin@komistore.app` with the details.

We treat token leaks, install-flow exploits, and signing-bypass paths as critical. Other issues we'll triage on a best-effort basis.

---

## Questions

- **Real-time chat:** join the [Discord server](https://discord.komistore.app) — fastest way to get a hand from maintainers and other contributors.
- **Email:** `admin@komistore.app` for anything that doesn't fit a public channel — sponsorship, partnerships, sensitive coordination.
- **General questions / discussion:** open a [GitHub Discussion](https://github.com/komi-store/komi-store/discussions) (if enabled) or a feature-request issue.
- **Stuck on local setup:** open a draft PR with what you have and ask for help in the description — we'd rather help you finish than have you give up silently.
- **Sibling repos:** [backend](https://github.com/komi-store/komi-store-backend) and [api](https://github.com/komi-store/komi-store-backend-data) have their own contributing notes.

Thanks for making Komi Store better.
