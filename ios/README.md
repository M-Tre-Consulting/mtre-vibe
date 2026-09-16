# Vibe iOS (Swift & SwiftUI)

Native iOS implementation for Vibe, built with 100% pure Swift, SwiftUI, and modern Apple Frameworks (AVFoundation, Network.framework / Bonjour).

## Architecture

The iOS client mirrors the modular architecture designed for the project:

- **`Vibe/App/`**: Application lifecycle, SwiftUI `@main` entrypoint, deep linking (`spotify:` and Universal Links).
- **`Vibe/Core/`**:
  - `Model/`: Domain models (Tracks, Playlists, Artists, Devices, Lyrics, Queue).
  - `Network/`: Spotify Web API client with PKCE OAuth2 authentication and 5s failover resiliency.
  - `Playback/`: Native audio playback engine powered by `AVFoundation` / `CoreAudio`, supporting 320 kbps playback, volume normalization, and on-disk audio cache.
  - `Connect/`: Native Spotify Connect controller & receiver protocol implementation in Swift with mDNS/Bonjour discovery via `Network.framework`.
  - `Storage/`: Local persistence with SwiftData / CoreData, account-specific metadata caches, and optimistic updates.
  - `UI/`: Common SwiftUI components, dynamic color extraction from album artwork, and system theme adaptors.
- **`Vibe/Features/`**: Feature-based SwiftUI views and ViewModels (Home, Search, Library, Playlist, Artist, Album, Player, Queue, Lyrics, Devices).
- **`Tests/`**: Unit and UI test targets (`VibeTests`, `VibeUITests`).
