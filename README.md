<div align="center">
  <br/>
  <img src="media-resources/app_icon.png" width="200" alt="Komi Store app icon" />

# Komi Store

</div>

<p align="center">
  <img alt="Kotlin" src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Kotlin/kotlin1.svg" />
  <img src="https://api.github-store.org/v1/badge/static/11/2?label=Apache--2.0&icon=palette" alt="Apache-2.0"/>
  <br/>
  <br/>
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Android/android1.svg" />
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Windows/windows1.svg" />
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/macOS/macos1.svg" />
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Linux/linux2.svg" />
  <br/>
  <br/>
 <a href="https://github.com/komi-store/komi-store/releases/latest">
  <img src="https://api.github-store.org/v1/badge/komi-store/komi-store/downloads/5/2?label=Downloads%20:" alt="Downloads"/>
</a>
<a href="https://github.com/komi-store/komi-store/stargazers">
  <img src="https://m3-markdown-badges.vercel.app/stars/3/2/komi-store/komi-store" alt="Stars"/>
</a>
<a href="https://github.com/komi-store/komi-store/issues">
  <img src="https://m3-markdown-badges.vercel.app/issues/1/2/komi-store/komi-store" alt="Issues"/>
</a>
<a href="https://github.com/komi-store/komi-store/releases/latest">
  <img src="https://api.github-store.org/v1/badge/komi-store/komi-store/release/9/1?label=Latest%20version%20:" alt="Latest release"/>
</a>
</p>

<table align="center">
  <tr>
    <td>
      <a href="https://trendshift.io/repositories/22313" target="_blank"><img src="https://trendshift.io/api/badge/repositories/22313" alt="komi-store%2Fkomi-store | Trendshift" width="250" height="55" /></a>
    </td>
    <td>
      <a href="https://hellogithub.com/en/repository/kurikomi-labs/komi-store" target="_blank"><img src="https://abroad.hellogithub.com/v1/widgets/recommend.svg?rid=a95f4a4830bc4a69b56f96ac7efaacf8&claim_uid=sOz1lfiG4ARQYIK&theme=dark" alt="Featured｜HelloGitHub" width="250" height="54" /></a>
    </td>
  </tr>
</table>

<div align="center">

## Project Overview 🗺️

Komi Store is a cross-platform app store for GitHub releases, designed to simplify discovering and installing open-source software.

Built with Kotlin Multiplatform and Compose Multiplatform for Android and Desktop platforms.

</div>

---

<div align="center">
  <a href="https://github.com/Safouene1/support-palestine-banner/blob/master/Markdown-pages/Support.md">
    <img src="https://raw.githubusercontent.com/Safouene1/support-palestine-banner/master/banner-project.svg" alt="Support Palestine" style="width: 100%;" />
  </a>
</div>

> [!CAUTION]
> Free and Open-Source Android is under threat. Google will turn Android into a locked-down platform, restricting your essential freedom to install apps of your choice. Make your voice heard – [keepandroidopen.org](https://keepandroidopen.org/).

<p align="center">
  <img src="media-resources/banner.jpeg" width="99%" />
</p>

| 1 | 2 | 3 | 4 | 5 | 6 |
| --- | --- | --- | --- | --- | --- |
| ![](media-resources/screenshots/mobile/01.jpg) | ![](media-resources/screenshots/mobile/02.jpg) | ![](media-resources/screenshots/mobile/03.jpg) | ![](media-resources/screenshots/mobile/04.jpg) | ![](media-resources/screenshots/mobile/05.jpg) | ![](media-resources/screenshots/mobile/06.jpg) |

---

<div align="center">

## Download

</div>

<p align="center">
  <a href="https://github.com/komi-store/komi-store/releases">
    <img src="https://i.ibb.co/q0mdc4Z/get-it-on-github.png" height="80" alt="Get it on GitHub" />
  </a>
  <a href="https://f-droid.org/en/packages/zed.rainxch.githubstore/">
    <img src="https://f-droid.org/badge/get-it-on.png" height="80" alt="Get it on F-Droid" />
  </a>
  <br/>
  <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/komi-store/komi-store/">
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="55" alt="Get it on Obtainium" />
  </a>
  <a href="https://github-store.org/app?repo=komi-store/komi-store">
    <img src="media-resources/ghs_download_badge.png" alt="Get it on Komi Store" height="58" />
  </a>
</p>

> [!IMPORTANT]
> **macOS Users:** You may see a warning that Apple cannot verify Komi Store. This happens because the app is distributed outside the App Store and is not notarized yet. Allow it via System Settings → Privacy & Security → Open Anyway.

**Scoop**

```powershell
scoop bucket add komi-store https://github.com/komi-store/komi-store-scoop-bucket
scoop install komi-store/komi-store
```

**WinGet**

```powershell
winget install zed.rainxch.githubstore
```

> [!TIP]
> **macOS Users:** Install Komi Store with Homebrew.

**Homebrew**

```bash
brew tap komi-store/komi-store
brew install --cask komi-store
xattr -dr com.apple.quarantine /Applications/Komi-Store.app
```

The final `xattr` command is required until the app is signed and notarized; without it, macOS Gatekeeper blocks the app with a "damaged" or "cannot be opened" error.

---

<div align="center">
  
## Features

</div>

- **Smart discovery**
    - Home sections for "Trending", "Hot Release", and "Most Popular" projects with time‑based filters.
    - Curated discovery layer through the Komi Store backend, with the live GitHub passthrough as a backup so freshness is honest.
    - Only repos with valid installable assets are shown.
    - Platform‑aware topic scoring so Android/desktop users see relevant apps first.
    - Search with filters for platform, programming language, and sort order — including **Recently Updated** (repo activity) and **Recently Released** (latest stable release date) sorts backed by the curated index.
    - Search history — recent queries are saved locally and shown as suggestions.
    - Clipboard link detection — automatically detects GitHub URLs from your clipboard and offers quick navigation.
    - Hide seen repos — optionally filter out repositories you've already viewed from discovery feeds.
    - **Add from starred** — discover APK‑shipping repos from your GitHub stars and jump straight into installing.

- **Release browser & installs**
    - Release picker to browse and install from any release, not just the latest.
    - Fetches all releases for each repository.
    - Single "Install latest" action, plus an expandable list of all available releases and their installers.
    - Manual install option with automatic compatibility checks.
    - **APK Inspect** (Android) — peek inside any release before installing. Surfaces app label, signing fingerprint, version codes, min/target SDK, components, file metadata, and grouped permissions colour‑coded by protection level.

- **Download mirror system**
    - Multi‑source race against the direct GitHub CDN, helpful on networks where `github.com` is throttled.
    - End‑to‑end SHA‑256 verification against GitHub's published asset digests.
    - Curated community mirror list, updatable from the backend without an app release.
    - Custom mirror URL for self‑hosted `gh-proxy`‑style instances.
    - Auto‑suggest sheet after sustained slow downloads — opt in with one tap, dismissible, never nags.

- **Rich details screen**
    - App name, version and share action.
    - Stars, forks, open issues, and license — sourced from the backend so anonymous and signed‑in users see the same data without burning GitHub quota.
    - Rendered README content ("About this app") with optional translation to the user's chosen language.
    - Release notes with Markdown formatting for any selected release.
    - List of installers with platform labels and file sizes.
    - Pre‑release channel chip — toggle "Include betas" / "Stable only" per app, with a "switch to stable" rollback when a clean stable exists.
    - "Merged what's changed since v1.0" — concatenated release notes for every version skipped between updates.
    - **Manual refresh** — pull‑to‑refresh on Android, an overflow‑menu Refresh on every platform, and `Ctrl/Cmd+R` on desktop. 30‑second per‑repo cooldown surfaces as a friendly retry‑after toast.
    - Deep linking support — open repository details via `githubstore://`, `github.com`, or `github-store.org` URLs.
    - Developer profile screen to explore a developer's repositories and activity.
    - **Per‑app variant pinning + asset filter regex** — pin the asset variant you actually want (`-arm64`, `-stable`, `-no-jvm`, etc.) and write a glob/regex filter so updates always pick the right artifact even when the publisher reshuffles asset names.

- **App management**
    - Open, uninstall, and downgrade installed apps directly from Komi Store.
    - **Library Imports** (Android) — recognises GitHub‑sourced apps already on the device (Obtainium, sideload, F‑Droid). Three match strategies (manifest hint, signing fingerprint, backend lookup); high‑confidence matches link silently, the rest land in a one‑tap review wizard. Run on demand from Apps overflow → Scan for GitHub apps.
    - **Obtainium import / export** (Android) — bring your library over from Obtainium with one tap, or export back to Obtainium any time. Round‑trip preserved via a discriminator on the export.
    - **Link apps** — connect any app already installed on your device to its GitHub repository so Komi Store can track updates for it. A guided flow lets you pick the app, enter the repo URL, and select the matching release asset.
    - **Sectioned Apps screen** — updates, pending installs, and installed apps grouped so you see what needs attention first.
    - Android: APK architecture matching (armv7/armv8), package monitoring, and update tracking.
    - Android: Shizuku and Sui silent installation — install and update apps without prompts (requires [Shizuku](https://shizuku.rikka.app/) or Sui running with ADB or root).
    - Android: **Dhizuku silent install** — bypass OEM install prompts on Xiaomi, OPPO, vivo, and Huawei devices via the [Dhizuku](https://github.com/iamr0s/Dhizuku) Device Owner method. Magisk‑free silent installs on stock ROMs.
    - Android: **Signing fingerprint verification for auto‑updates** — auto‑update is blocked if the new APK's signing key doesn't match what the row was registered with. Prevents silent compromise on a hijacked release.
    - Android: Background update checking — configurable periodic checks (3h / 6h / 12h / 24h) with notifications when updates are found.
    - Android: Auto‑update — silently installs available updates via Shizuku/Sui/Dhizuku when enabled.
    - Desktop (Windows/macOS/Linux): downloads installers to the user's Downloads folder and opens them with the default handler.

- **Collections**
    - **Starred** — browse your GitHub starred repositories from within the app.
    - **Favourites** — save repositories locally for quick access, no GitHub login required.
    - **Recently viewed** — automatically tracks repositories you've opened for easy return.

- **Cross‑version communication**
    - **What's new sheet** — one‑shot release highlights on first launch after each update, plus a permanent history under Profile → What's new.
    - **Announcements feed** — privacy notices, surveys, security advisories, and news in Profile. Anonymous backend feed; dismissal and read state stay on the device.

- **Authentication**
    - GitHub OAuth via the device flow, routed through the backend by default so sign‑in keeps working on networks that throttle `github.com`. Falls back to direct GitHub on infrastructure errors.
    - **Personal Access Token sign‑in** for users whose network blocks the OAuth browser flow entirely. Validates locally, full feature parity with the OAuth path.

- **Tweaks**
    - Dedicated settings screen accessible from the bottom navigation bar.
    - **Appearance** — theme color picker (Dynamic, Ocean, Purple, Forest, Slate, Amber), light/dark/system mode, AMOLED black theme, system font toggle, scrollbar toggle (desktop).
    - **Network** — proxy configuration with HTTP/SOCKS support and optional authentication; download‑mirror picker with latency test.
    - **Installation** (Android) — choose between default installer, Shizuku/Sui silent install, or Dhizuku Device Owner install, with real‑time installer status indicator. **Installer attribution** sub‑setting lets you set the installer‑package name silent installs claim (System default, a preset like Play Store, or a custom package) so apps that gate on install source can be coaxed into running.
    - **Translation provider** — switch between Google Translate (free, no key) and Youdao (custom app key + secret) for README and release‑note translation.
    - **Updates** (Android) — update check interval, pre‑release inclusion, auto‑update toggle.
    - **Storage** — view and clear downloaded package cache.
    - **Send feedback** — bug reports, feature ideas, and change requests sent as email or a pre‑filled GitHub issue. Diagnostics card shows exactly what's being sent before you send it.

- **Localization**
    - Available in 13 languages: English, Arabic, Bengali, Chinese (Simplified), Spanish, French, Hindi, Italian, Japanese, Korean, Polish, Russian, and Turkish.

- **Network & performance**
    - Dynamic proxy support (HTTP, SOCKS, System) for configurable network routing.
    - Enhanced caching system for faster loading and reduced API usage.
    - Anonymous backend at `api.github-store.org` for auth proxy, curated discovery, and the announcements feed. Open source under Apache 2.0 — self‑hostable.
    - **Desktop trusts OS‑installed root certificates** in addition to the JVM bundle, so TLS‑intercepting tools (Watt Toolkit, FastGithub, Fiddler, corporate MITM proxies) work without `keytool` gymnastics.

---

<div align="center">

  ## FAQ
  
</div>

<details>
<summary><strong>🔍 How does my app appear in Komi Store?</strong></summary>

<br/>

Komi Store does not use any private indexing or manual curation rules.
Your project can appear automatically if it follows these conditions:

1. **Public repository on GitHub**
    - Visibility must be `public`.

2. **Installable assets in the latest release**
    - The latest release must contain at least one asset file with a supported extension:
        - Android: `.apk`
        - Windows: `.exe`, `.msi`
        - macOS: `.dmg`, `.pkg`
        - Linux: `.deb`, `.rpm`, `.AppImage`, `.pkg.tar.zst`
    - Komi Store ignores GitHub's auto‑generated source artifacts (`Source code (zip)` /
      `Source code (tar.gz)`).

3. **Discoverable by search / topics**
    - Repositories are fetched via the public GitHub Search API.
    - Topic, language, and description help the ranking:
        - Android apps: topics like `android`, `mobile`, `apk`.
        - Desktop apps: topics like `desktop`, `windows`, `linux`, `macos`, `compose-desktop`,
          `electron`.
    - Having at least a few stars makes it more likely to appear under Trending/Hot Release/Most Popular sections.

If your repo meets these conditions, Komi Store can find it through search and show it
automatically—no manual submission required.

</details>

<details>
<summary><strong>✅ Pros / Why use Komi Store?</strong></summary>

<br/>

- **No more hunting through GitHub releases**
  See only repos that actually ship binaries for your platform.

- **Knows what you installed**
  Tracks apps installed via Komi Store (Android) and highlights when new releases are available, so you can update them without hunting through GitHub again.

- **Always up to date**
  Installs default to the latest published release, with the option to browse and install from
  any previous release via the release picker. Background update checks notify you when new versions drop.

- **Hands‑free updates (Android)**
  Enable Shizuku or Sui silent install + auto‑update and never touch an install prompt again.

- **Your library, your way**
  Star, favourite, and track recently viewed repos — all synced locally with no account required for favourites and history.

- **Fully customizable**
  Theme colors, AMOLED mode, fonts, liquid glass effects, proxy settings, and more — all in one Tweaks screen.

- **Open source & extensible**
  Written in KMP with a clear separation between networking, domain logic, and UI—easy to fork,
  extend, or adapt.

</details>

---

<div align="center">
  
## 🏆 Featured In

</div>

<p align="center">
  <a href="https://www.youtube.com/@howtomen">
    <img src="https://img.shields.io/badge/HowToMen-red?style=for-the-badge&logo=youtube&logoColor=white" alt="Featured by HowToMen" />
  </a>
  <br/>
  <strong>HowToMen:</strong> <a href="https://www.youtube.com/watch?v=7favc9MDedQ">Top 20 Best Android Apps 2026</a> | <a href="https://www.youtube.com/watch?v=VR-MEwPDw4k">Top 12 App Stores that are Better than Google Play Store</a>
  <br/>
  <strong>HelloGitHub:</strong> <a href="https://hellogithub.com/en/repository/kurikomi-labs/komi-store">Featured Project</a>
</p>

---

<div align="center">
  
## 📺 Meet the Developer

</div> 

I made a video introducing myself and sharing what's next for Komi Store.

**[Watch on YouTube →](https://www.youtube.com/watch?v=iT1cok4-Txs)** | **[Watch on Bilibili →](https://www.bilibili.tv/en/video/4799266946423296)**

Help shape the future of Komi Store — take this 2-minute survey:

**[📋 Take the Survey →](https://tally.so/r/q4Ed88)**

---

<div align="center">
  
## 💬 Discord

</div>

You can submit any feedback in our [discord server](https://discord.github-store.org)

<p align="center">
  <a href="https://discord.github-store.org">
    <img src="https://invidget.switchblade.xyz/NBW4zeFcG6" />
  </a>
</p>

---

<div align="center">
  
## 🔐 Komi Store APK Signing Certificate

</div>

All official Komi Store releases are signed with the following certificate fingerprint:

SHA-256:
`B7:F2:8E:19:8E:48:C1:93:B0:38:C6:5D:92:DD:F7:BC:07:7B:0D:B5:9E:BC:9B:25:0A:6D:AC:48:C1:18:03:CA`

---

<div align="center">
  
## 🔑 GitHub OAuth Configuration

</div>

**TL;DR**
1. Create a GitHub OAuth App
2. Copy **Client ID**
3. Put it in `local.properties`

<details>
<summary><strong>Show full setup guide</strong></summary>

<br/>

### 1 - Create a GitHub OAuth App

Go to:
**GitHub → Settings → Developer settings → OAuth Apps → New OAuth App**

| Field                          | Value                                       |
| ------------------------------ | ------------------------------------------- |
| **Application name**           | Anything you like (e.g. *Komi Store Dev*) |
| **Homepage URL** | `https://github.com/username/repo_name`                   |
| **Authorization callback URL** | `githubstore://callback`                    |

Then click **Create application**.

### 2 - Copy Your Client ID

After creating the app, GitHub will show:
- **Client ID** ← you need this
- **Client Secret** ← ❗ NOT required for this project

### 3 - Add It to Your Project

Open your project's `local.properties` file (root of the project) and add:
```properties
GITHUB_CLIENT_ID=YOUR_CLIENT_ID_HERE
```

### 4 - Sync & Run

Sync the project and run the app. You should now be able to sign in with GitHub.

### ❗ Important Notes

- `local.properties` is **not committed to Git**, so your Client ID stays local.
- This project only needs the **Client ID** (not the Client Secret).
- Each developer should create their own OAuth app for development.

</details>

---

<div align="center">
  
## 📔 Wiki & Resources

</div>

Check out Komi Store [Wiki](https://github.com/komi-store/komi-store/wiki) for FAQ and useful information

🌐 **Website:** [github-store.org](https://github-store.org)
💬 **Discord:** [Join the community](https://discord.github-store.org)
📜 **Privacy Policy:** [github-store.org/privacy-policy](https://github-store.org/privacy-policy/)

---

<div align="center">
  
## ❤️ Support This Project

</div>

Komi Store is 100% free. No ads. No tracking.

- ⭐ **[Star](https://github.com/komi-store/komi-store/star)** this repository
- 🐛 **[Report](https://github.com/komi-store/komi-store/issues)** bugs and issues
- 💡 **[Suggest](https://github.com/komi-store/komi-store/discussions)** new features
- 💳 **[Sponsor](https://github.com/sponsors/rainxchzed)** the developer

---

<div align="center">
  
## 💼 Business Inquiries

</div>

Komi Store is open to partnerships, sponsorships, and integrations.

If you're interested in working together, reach out:

📧 **Email:** hello@github-store.org
💬 **Discord:** [Join our community](https://discord.github-store.org)

---

<div align="center">
  
## 📋 Legal Notice

</div>

Komi Store is an independent, open-source project not affiliated with GitHub, Inc.
The name describes the app's functionality (discovering GitHub releases) and does not imply trademark ownership.
GitHub® is a registered trademark of GitHub, Inc.

---

<div align="center">
  
## ⚠️ Disclaimer

</div>
Komi Store only helps you discover and download release assets that are already published on
GitHub by third‑party developers.
The contents, safety, and behavior of those downloads are entirely the responsibility of their
respective authors and distributors, not this project.

By using Komi Store, you understand and agree that you install and run any downloaded software at
your own risk.
This project does not review, validate, or guarantee that any installer is safe, free of malware, or
fit for any particular purpose.

---

<div align="center">
  
## 💫 Star History

</div>

<a href="https://www.star-history.com/#komi-store/komi-store&type=timeline&legend=top-left">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=komi-store/komi-store&type=timeline&theme=dark&legend=top-left" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=komi-store/komi-store&type=timeline&legend=top-left" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=komi-store/komi-store&type=timeline&legend=top-left" />
  </picture>
</a>

![Alt](https://repobeats.axiom.co/api/embed/20367dca127572e9c47db33850979d78df2c6a8b.svg "Repobeats analytics image")

---

<div align="center">

## 📄 License

</div>

Komi Store is released under the **Apache License, Version 2.0**.

```
Copyright 2025-2026 rainxchzed

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this project except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
