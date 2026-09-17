# Vibe

> **Vibe** is a fully **native**, high-performance multi-project Spotify client for **Android** and **iOS**. It is built from the ground up using clean modular architecture, without cross-platform web wrappers or non-native runtimes (no Rust, no Electron/Cordova), written purely in **Kotlin** (Android) and **Swift** (iOS).

---

## Table of Contents
- [Vision & Philosophy](#vision--philosophy)
- [Multi-Project Repository Architecture](#multi-project-repository-architecture)
  - [Android Project Structure (Kotlin)](#android-project-structure-kotlin)
  - [iOS Project Structure (Swift)](#ios-project-structure-swift)
- [Internationalization (i18n)](#internationalization-i18n)
- [Comprehensive Feature Specifications](#comprehensive-feature-specifications)
  - [1. Native Audio Playback & Spotify Connect Target](#1-native-audio-playback--spotify-connect-target)
  - [2. Remote Device Control (Connect Controller)](#2-remote-device-control-connect-controller)
  - [3. Local Network Discovery (mDNS / Zeroconf)](#3-local-network-discovery-mdns--zeroconf)
  - [4. Personal Library & Caching Infrastructure](#4-personal-library--caching-infrastructure)
  - [5. Resilient Dual-Source Search Engine](#5-resilient-dual-source-search-engine)
  - [6. Dynamic Home & Recommendation Feeds](#6-dynamic-home--recommendation-feeds)
  - [7. Artist & Album Pages with Smart EP Detection](#7-artist--album-pages-with-smart-ep-detection)
  - [8. Advanced Playlist Editing & Drag-and-Drop](#8-advanced-playlist-editing--drag-and-drop)
  - [9. Deep Linking & Universal Links](#9-deep-linking--universal-links)
  - [10. Dynamic Play Queue & Repeat History Tracking](#10-dynamic-play-queue--repeat-history-tracking)
  - [11. Cache Integrity & Streamed Checkpoint Buffering](#11-cache-integrity--streamed-checkpoint-buffering)
  - [12. Real-Time Synchronized Lyrics](#12-real-time-synchronized-lyrics)
  - [13. Session State Restoration on Launch](#13-session-state-restoration-on-launch)
  - [14. Dynamic Album Art Tinting & Material You Palette](#14-dynamic-album-art-tinting--material-you-palette)
- [Technology Stack](#technology-stack)
- [Developer Guide & Getting Started](#developer-guide--getting-started)

---

## Vision & Philosophy

Vibe is built for instant responsiveness, memory efficiency, and battery optimization by taking full advantage of modern platform-native APIs:
- **100% Native**: Zero toolchain overhead from third-party runtimes. Pure **Kotlin** on Android and pure **Swift** on iOS.
- **Modern UI**: Declarative reactive UI using **Jetpack Compose** (Android) and **SwiftUI** (iOS).
- **Internationalized by Design**: Built from day one with multi-language support (English and Italian).
- **Pure Mobile Experience**: Clean design adhering to Material 3 and Apple Human Interface Guidelines, avoiding vintage non-contextual themes (e.g. Winamp).

---

## Multi-Project Repository Architecture

The monorepo separates platform targets cleanly while maintaining shared domain naming conventions:

```
mtre-vibe/
├── README.md                       # Comprehensive project documentation (English)
├── .gitignore                      # Git rules for Gradle, Android Studio, Xcode, and secrets
├── android/                        # Native Android multi-module Gradle project (Kotlin)
│   ├── build.gradle.kts            # Root build script
│   ├── settings.gradle.kts         # Gradle modules declaration (rootProject.name = "Vibe")
│   ├── gradle.properties
│   ├── gradlew                     # Gradle Wrapper script
│   ├── gradle/
│   │   ├── libs.versions.toml      # Unified Version Catalog (AGP, Kotlin 2.x, Media3, Compose)
│   │   └── wrapper/
│   │       └── gradle-wrapper.properties
│   ├── app/                        # Main Android application module
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml # Deep linking (spotify: & open.spotify.com), Media3 Service
│   │       ├── res/
│   │       │   ├── values/strings.xml    # English base strings
│   │       │   └── values-it/strings.xml # Italian localization strings
│   │       └── java/com/vibe/app/
│   │           ├── MainActivity.kt
│   │           ├── VibeApplication.kt
│   │           └── playback/VibeMediaSessionService.kt
│   ├── core/                       # Shared core infrastructure modules
│   │   ├── core-model/             # Domain entities (Track, Album, Playlist, Device, Queue, etc.)
│   │   ├── core-common/            # Coroutine dispatchers, Resource wrappers, async utilities
│   │   ├── core-network/           # Spotify Web API, PKCE Auth, 5-second timeout failover client
│   │   ├── core-playback/          # ExoPlayer/Media3, 512MB disk cache, gapless, seek buffer discard
│   │   ├── core-connect/           # mDNS discovery (Android NSD / JmDNS), device ID deduplication
│   │   ├── core-database/          # Room DB, account-specific Liked Songs cache, 64KB streamed buffer
│   │   └── core-ui/                # M3 Theme, Dynamic Palette extractor, localized strings & components
│   └── feature/                    # Isolated feature UI modules (Jetpack Compose)
│       ├── feature-home/           # Home feed, Made for You, recommendation shelves
│       ├── feature-search/         # Isolated dual-source search (personal catalog + playlists)
│       ├── feature-library/        # Library navigation, Liked Songs pinning, sorting modes
│       ├── feature-playlist/       # Playlist view, metadata editing, cover upload, in-place insertion
│       ├── feature-artist/         # Artist page, popular tracks, discography with EP badge
│       ├── feature-album/          # Album detail with EP / Single / Album badge
│       ├── feature-player/         # Docked MiniPlayer & Full-screen player with dynamic tinting
│       ├── feature-queue/          # Contextual play queue, repeat history tracking
│       ├── feature-lyrics/         # Synced scrolling lyrics with plain text fallback
│       └── feature-devices/        # Spotify Connect device picker with remote volume slider
└── ios/                            # Native iOS multi-module project (Swift & SwiftUI)
    ├── README.md                   # iOS target guide
    └── Vibe/                       # Native Xcode / Swift directory layout with .gitkeep placeholders
        ├── App/                    # SwiftUI @main entrypoint, app lifecycle, universal links (.gitkeep)
        ├── Core/                   # Model, Network, Playback, Connect, Storage, UI (.gitkeep)
        ├── Features/               # Home, Search, Library, Playlist, Artist, Album, Player, Queue, Lyrics, Devices (.gitkeep)
        ├── Resources/              # Localizable string catalogs (en, it) & Assets.xcassets (.gitkeep)
        └── Tests/                  # VibeTests, VibeUITests (.gitkeep)
```

---

## Internationalization (i18n)

Vibe is internationalized from the very beginning. Hardcoded user-facing strings are strictly prohibited:
- **Android**: Strings are managed via Android XML resource catalogs (`res/values/strings.xml` for English, `res/values-it/strings.xml` for Italian).
- **iOS**: Strings are organized in String Catalogs / Localization tables (`en.lproj` and `it.lproj`).
- **Supported Languages**:
  - 🇺🇸 **English** (Default)
  - 🇮🇹 **Italian** (Italiano)

---

## Comprehensive Feature Specifications

### 1. Native Audio Playback & Spotify Connect Target
- **Connect Target**: Vibe registers on the local network as an active Spotify Connect receiver. It can be targeted from any smartphone, desktop, or controlled directly within the app.
- **Bitrate & Gapless**: Supports high-fidelity playback up to **320 kbps** (Very High Quality Ogg Vorbis/AAC) with true **gapless playback** across track boundaries.
- **Volume Normalization**: Optional ReplayGain / Loudness normalizer to eliminate drastic volume jumps between tracks.
- **On-Disk Audio Cache**: Dedicated local cache (512 MB by default, user-configurable) stored on internal storage for zero-buffering instant replay.
- **5-Second Timeout & CDN Failover**: Stalled network connections time out after **5 seconds per attempt**, automatically failing over to alternative Spotify CDN edge servers (`audio-fa.scdn.co`, `audio-ak.scdn.co`, `audio4-fa.scdn.co`).
- **Immediate Seek Purge**: Confirmed local seeks immediately discard and flush audio buffers queued from the old position (`Player.DISCONTINUITY_REASON_SEEK`), eliminating stale playback delays.
- **Instant Metadata Display**: Starting a sorted playlist or Liked Songs immediately updates the player bar with preloaded metadata while the audio stream resolves in the background.
- **Sorted Views**: Automatically start at the first playable row (`isPlayable == true`).
- **Filtered Playback**: Active search or filter constraints restrict playback strictly to the displayed songs while preserving duplicate tracks; Play is disabled when no matching song is playable.

### 2. Remote Device Control (Connect Controller)
- Move playback seamlessly to external speakers, smart TVs, smartphones, or computers from the device picker.
- Complete remote playback control: Play, Pause, Skip Next, Skip Previous, Seek, Shuffle, Repeat Mode, and Volume adjustment.
- Long device lists scroll smoothly with fast response times.

### 3. Local Network Discovery (mDNS / Zeroconf)
- Discovers Spotify Connect receivers (`_spotify-connect._tcp.local.`) on the local WiFi network using Android Network Service Discovery (`NsdManager` / `JmDNS`) and iOS Bonjour (`Network.framework`).
- Detects `librespot`, `spotifyd`, smart speakers, and hardware AV receivers.
- **Intelligent Deduplication**: Groups and merges entries sharing the same `device_id`, prioritizing responding receiver names and eliminating duplicates in the picker.

### 4. Personal Library & Caching Infrastructure
- Full navigation: Playlists, Liked Songs, Saved Albums, Followed Artists, Podcasts, and Saved Episodes.
- Fast filtering, pinning, and custom reordering.
- **Quick Actions**: Double-tap a playlist in the library to start instant playback; single tap opens detail view.
- **Compact Tracklist Mode**: Settings toggle enabling a compressed one-line-per-song layout with spaced separators: `Title  ·  Artists  ·  Added date`.
- **Flexible Sorting**: Sort by Name, Recent Plays, or Date Added. Follow Spotify's cloud order or keep an independent local arrangement.
- **Liked Songs Pin Management**: Pin or unpin Liked Songs and customize its local position; placement survives application restarts.
- **Smart EP Badge Detection**: Releases classified by the Web API as singles are explicitly badged as **EP** when metadata confirms 3 to 6 tracks.
- **Instant Liked Songs Cache**: Opens instantaneously from an account-specific disk cache. Older items refresh silently in the background, while Like/Unlike actions reflect immediately via optimistic UI updates.
- **Context Menus**: Long-press (or right-click) action sheets for albums, artists, and podcasts.

### 5. Resilient Dual-Source Search Engine
- Comprehensive search across tracks, artists, albums, playlists, podcasts, and episodes, with a hero "Top Result" card and dedicated per-type tabs.
- **Fault-Tolerant Call Isolation**: Personal catalog search and shared playlist lookups execute concurrently in isolated asynchronous coroutines; if either fails or times out, the other displays its results without blocking the user.
- System clipboard support: Cut, Copy, Paste, Select All.
- Responsive search bar layout preventing overlap with system status bars, badges, or device controls.

### 6. Dynamic Home & Recommendation Feeds
- Personalized home screen featuring "Made for You", "Recently Played", top artists, top tracks, and algorithmic recommendation shelves.
- Quick shortcut shelf cards with long-press context menus.

### 7. Artist & Album Pages with Smart EP Detection
- **Artist Pages**: Popular songs, filterable discography (*Albums*, *EPs & Singles*, *Compilations*), and related artists.
- **Virtualized Scrolling**: In album and playlist views, scrollbars reflect the total track count; jumping to unloaded sections fetches that page directly.
- **Instant Artist Navigation**: Tapping artist names anywhere in the app or player bar navigates to their profile immediately, even during initial local playback before Web API metadata finishes resolving.

### 8. Advanced Playlist Editing & Drag-and-Drop
- Create, rename, describe, reorder, and delete playlists.
- **Edge Auto-Scroll**: Holding a dragged track near the top or bottom screen boundary scrolls the list smoothly to off-screen positions.
- **Cover Art Upload**: Upload custom playlist covers (JPEG or PNG).
- **Multi-Track Selection & Bulk Actions**: Select multiple rows with a selection count badge and translucent neutral highlight (without disruptive borders); drop the selection onto Liked Songs to bulk-save all selected tracks in displayed order.
- **In-Place Insertion**: Dropping a song between rows of an editable playlist inserts it at that exact index without altering active queue playback.
- Collaborative and shared playlist support with filterable destination search.

### 9. Deep Linking & Universal Links
- Registers intent handlers for native `spotify:` URIs (`spotify:track:...`, `spotify:album:...`, `spotify:artist:...`, `spotify:playlist:...`).
- Handles `https://open.spotify.com/...` universal web links, opening the corresponding content directly in Vibe whether the app is already running or cold-launched.

### 10. Dynamic Play Queue & Repeat History Tracking
- Queue accessible as a side panel (tablets/foldables) or dedicated screen, identifying the originating context.
- **Smart Queue Insertion**: "Add to Queue" inserts songs immediately after user-queued tracks and before continuous context playback.
- **Duplicate Preservation**: Adding repeated playlist rows queues every occurrence in requested order; duplicate taps register once with verified addition counts.
- **Accurate History**: "Recently Played" tracks repeated short songs separately, including consecutive local repeats of the same track. Tapping a history row launches it immediately in the player bar.

### 11. Cache Integrity & Streamed Checkpoint Buffering
- **Revision Sync**: Cached playlist data validates Spotify revision hashes and track counts before driving playback order.
- **Optimistic State with Rollback Protection**: Pending playlist edits remain visible and are committed to local cache only after remote write confirmation. Failed refreshes preserve active rows and offer retry options.
- **Streamed 64KB Checkpoint Buffer**: Checkpoint serialization reads and writes playlist JSON through a compact 64 KB streaming buffer, preventing memory spikes and eliminating duplicate JSON heap copies.

### 12. Real-Time Synchronized Lyrics
- Real-time lyrics display with automatic scrolling and highlight synchronization matching track timestamps.
- Available in split side-panel view or immersive full-screen display.
- Graceful automatic fallback to unsynchronized plain text lyrics.

### 13. Session State Restoration on Launch
- On launch, the last played track is restored paused at its exact stopping timestamp.
- Immediate response: Play resumes playback; skip, seek, and volume controls are interactive prior to unpausing.

### 14. Dynamic Album Art Tinting & Material You Palette
- Extracts dominant tonal accents dynamically from active album artwork (via AndroidX Palette on Android and CoreImage on iOS), subtly tinting player surfaces and backgrounds.
- Settings toggle to disable dynamic color tinting.
- Full support for Light Theme, Dark Theme, and Follow System.

---

## Technology Stack

| Domain | Android | iOS |
|---|---|---|
| **Language** | **Kotlin 2.0.20** | **Swift 5.10 / Swift 6** |
| **UI Framework** | Jetpack Compose + Material 3 | SwiftUI |
| **Audio Engine** | AndroidX Media3 (ExoPlayer) + SimpleCache | AVFoundation / CoreAudio |
| **Networking** | OkHttp 4.12 + Retrofit 2.11 + Coroutines | URLSession + Swift Concurrency |
| **Serialization** | Kotlinx Serialization JSON 1.7 | Swift `Codable` |
| **mDNS / Zeroconf** | Android `NsdManager` + JmDNS | `Network.framework` (Bonjour) |
| **Persistence** | Room 2.6 + DataStore Preferences | SwiftData / CoreData |
| **Dynamic Palette** | AndroidX Palette KTX | CoreImage / Vision Palette |
| **Dependency Injection** | Koin 3.5 | Swift Dependencies / Factory Pattern |
| **Localization (i18n)** | Android XML (`values/`, `values-it/`) | String Catalogs (`en.lproj`, `it.lproj`) |

---

## Developer Guide & Getting Started

### Prerequisites
- **Android**: JDK 17 or 21, Android SDK (API 34).
- **iOS**: macOS Sonoma or later with Xcode 15+.

### Building Android
All dependencies and versions are declared in the Gradle Version Catalog (`android/gradle/libs.versions.toml`).

```bash
cd android

# Compile and build the debug APK
./gradlew :app:assembleDebug

# Run unit tests across all modules
./gradlew test
```

### iOS Setup
The `ios/` directory contains the complete modular directory tree with `.gitkeep` files and localization catalogs, ready for Xcode project linking and Swift development.
