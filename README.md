# Vibe

> **Vibe** è un client Spotify multipiattaforma completamente **nativo** per **Android** e **iOS**, progettato con architettura a moduli pulita (Clean Architecture), zero dipendenze da framework cross-platform non nativi o runtime esterni (es. Rust), implementato interamente in **Kotlin** (Android) e **Swift** (iOS).

---

## Indice dei Contenuti
- [Visione e Filosofia](#visione-e-filosofia)
- [Architettura del Repository Multiprogetto](#architettura-del-repository-multiprogetto)
  - [Struttura Android (Kotlin)](#struttura-android-kotlin)
  - [Struttura iOS (Swift)](#struttura-ios-swift)
- [Specifiche delle Funzionalità](#specifiche-delle-funzionalità)
  - [1. Riproduzione Audio Nativa & Ricevitore Spotify Connect](#1-riproduzione-audio-nativa--ricevitore-spotify-connect)
  - [2. Controllo Remoto dei Dispositivi (Connect Controller)](#2-controllo-remoto-dei-dispositivi-connect-controller)
  - [3. Discovery di Rete Locale (mDNS / Zeroconf)](#3-discovery-di-rete-locale-mdns--zeroconf)
  - [4. Libreria Personale & Gestione Caching](#4-libreria-personale--gestione-caching)
  - [5. Sistema di Ricerca a Doppia Sorgente Isolato](#5-sistema-di-ricerca-a-doppia-sorgente-isolato)
  - [6. Home & Raccomandazioni Dinamiche](#6-home--raccomandazioni-dinamiche)
  - [7. Pagine Artista & Album con Riconoscimento EP](#7-pagine-artista--album-con-riconoscimento-ep)
  - [8. Editing Avanzato Playlist & Drag-and-Drop](#8-editing-avanzato-playlist--drag-and-drop)
  - [9. Deep Linking & Universal Links](#9-deep-linking--universal-links)
  - [10. Gestione Coda Dinamica & Cronologia Separata](#10-gestione-coda-dinamica--cronologia-separata)
  - [11. Integrità Cache & Buffer per Checkpoint Playlist](#11-integrità-cache--buffer-per-checkpoint-playlist)
  - [12. Testi Sincronizzati (Lyrics)](#12-testi-sincronizzati-lyrics)
  - [13. Ripristino Sessione all'Avvio](#13-ripristino-sessione-allavvio)
  - [14. Tema Dinamico con Tinting dalla Copertina (Dynamic Color)](#14-tema-dinamico-con-tinting-dalla-copertina-dynamic-color)
- [Stack Tecnologico](#stack-tecnologico)
- [Guida per Sviluppatori](#guida-per-sviluppatori)

---

## Visione e Filosofia

Vibe è pensato per offrire la massima velocità, efficienza della memoria e reattività dell'interfaccia utente, sfruttando appieno le API di sistema native:
- **100% Nativo**: Nessun overhead di linguaggi o runtime complessi da compilare con toolchain C/Rust. Tutto è sviluppato in standard **Kotlin** per Android e **Swift** per iOS.
- **Interfaccia Moderna**: Jetpack Compose su Android, SwiftUI su iOS.
- **Nessuna funzione non pertinente**: Esclusi temi o player vintage (es. Winamp), mantenendo l'esperienza focalizzata sulle prestazioni mobile native e il design system Material 3 / iOS Human Interface Guidelines.

---

## Architettura del Repository Multiprogetto

Il repository è organizzato come monorepo con separazione chiara tra le piattaforme e scomposizione modulare:

```
mtre-vibe/
├── README.md
├── .gitignore
├── android/                        # Progetto Android nativo (Gradle Multi-Modulo in Kotlin)
│   ├── build.gradle.kts            # Root build script
│   ├── settings.gradle.kts         # Definizione dei moduli Gradle (rootProject.name = "Vibe")
│   ├── gradle.properties
│   ├── gradlew                     # Gradle Wrapper script
│   ├── gradle/
│   │   ├── libs.versions.toml      # Version Catalog unificato (AGP, Kotlin 2.x, Media3, Compose)
│   │   └── wrapper/
│   │       └── gradle-wrapper.properties
│   ├── app/                        # Modulo applicazione Android
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml # Deep linking (spotify: e open.spotify.com), Service Media3
│   │       └── java/com/vibe/app/
│   │           ├── MainActivity.kt
│   │           ├── VibeApplication.kt
│   │           └── playback/VibeMediaSessionService.kt
│   ├── core/                       # Core modules (condivisi tra le feature Android)
│   │   ├── core-model/             # Entità di dominio (Track, Album, Playlist, Device, ecc.)
│   │   ├── core-common/            # Utility coroutine, dispatchers, Resource result wrappers
│   │   ├── core-network/           # Spotify Web API, auth PKCE, timeout a 5s per failover rapido
│   │   ├── core-playback/          # ExoPlayer/Media3, cache disco 512MB, gapless, purge seek buffer
│   │   ├── core-connect/           # mDNS discovery (Android NSD / JmDNS), deduplica per device ID
│   │   ├── core-database/          # Room DB, cache per account per Liked Songs, checkpoint buffer
│   │   └── core-ui/                # Material 3 Theme, Dynamic Palette extractor, TrackRow compatto
│   └── feature/                    # Feature modules (UI isolate con Compose)
│       ├── feature-home/           # Home, Made for You, scaffali consigliati
│       ├── feature-search/         # Ricerca a doppia sorgente (catalogo + playlist)
│       ├── feature-library/        # Libreria, Liked Songs pin, ordinamenti
│       ├── feature-playlist/       # Vista playlist, editing dettagli, upload cover, in-place drop
│       ├── feature-artist/         # Pagina artista, discografia filtrabile (badge EP)
│       ├── feature-album/          # Pagina album con tipo (EP / Single / Album)
│       ├── feature-player/         # MiniPlayer docked & Player full-screen con tinting
│       ├── feature-queue/          # Gestione coda, tracciamento storico ripetizioni
│       ├── feature-lyrics/         # Testi sincronizzati a scorrimento e fallback testo semplice
│       └── feature-devices/        # Device picker Spotify Connect con slider volume remoto
└── ios/                            # Progetto nativo iOS (Swift & SwiftUI)
    ├── README.md
    └── Vibe/                       # Alberatura completa con file .gitkeep per ciascun modulo
        ├── App/                    # Entrypoint SwiftUI @main, lifecycle, handling universal links (.gitkeep)
        ├── Core/                   # Model, Network, Playback, Connect, Storage, UI (.gitkeep)
        ├── Features/               # Home, Search, Library, Playlist, Artist, Album, Player, Queue, Lyrics, Devices (.gitkeep)
        ├── Resources/              # Assets.xcassets (.gitkeep)
        └── Tests/                  # VibeTests, VibeUITests (.gitkeep)
```

---

## Specifiche delle Funzionalità

### 1. Riproduzione Audio Nativa & Ricevitore Spotify Connect
- **Ricevitore Connect**: Vibe appare nella rete locale come dispositivo Spotify Connect. Può essere selezionato direttamente da un telefono o comandato internamente.
- **Qualità & Bitrate**: Supporto per streaming fino a **320 kbps** (Very High Quality), riproduzione **gapless** senza interruzioni tra le tracce.
- **Normalizzazione Volume**: Controllo opzionale del guadagno ReplayGain/Loudness per uniformare il volume delle tracce.
- **Cache Audio su Disco**: Cache dedicata locale (default 512MB espandibile) su memoria interna ad accesso istantaneo, che azzera il buffering per i brani riprodotti di frequente.
- **Failover di Rete a 5 Secondi**: Timeout di connessione rigido a **5 secondi per tentativo**: se un endpoint Spotify rallenta o va in stallo, il player tenta immediatamente un endpoint CDN alternativo senza bloccare la riproduzione.
- **Purge Immediato del Buffer al Seek**: Quando l'utente conferma un seek temporale, il buffer audio residuo della posizione precedente viene **immediatamente scartato** (`onPositionDiscontinuity`), azzerando le code di decodifica obsolete.
- **Visualizzazione Istantanea dei Metadati**: All'avvio di una playlist ordinata o della vista *Liked Songs*, la canzone richiesta viene mostrata all'istante nel player bar utilizzando i metadati precaricati, mentre la connessione audio si stabilisce in background.
- **Viste Ordinate**: Partono automaticamente dalla prima riga effettivamente riproducibile (`isPlayable == true`).
- **Vincolo di Riproduzione Filtrata**: L'applicazione di un filtro di testo mantiene la riproduzione limitata **esclusivamente ai brani mostrati** a schermo, preservando le tracce duplicate; il pulsante Play viene disabilitato se nessun brano filtrato è riproducibile.

### 2. Controllo Remoto dei Dispositivi (Connect Controller)
- Spostamento trasparente della sessione di riproduzione verso altoparlanti, TV, smartphone o computer dal selettore dei dispositivi.
- Controllo remoto completo: play, pausa, salto traccia (avanti/indietro), seek temporale, shuffle, modalità di ripetizione e controllo del volume.
- La lista dei dispositivi supporta lo scorrimento fluido anche con elenchi molto lunghi.

### 3. Discovery di Rete Locale (mDNS / Zeroconf)
- Ricerca attiva tramite protocollo mDNS/DNS-SD (`_spotify-connect._tcp.local.`) via Android `NsdManager` / `JmDNS` su Android e `Network.framework` (Bonjour) su iOS.
- Rilevamento automatico di istanze `librespot`, `spotifyd` e sintoamplificatori/ricevitori hardware compatibili.
- **Deduplicazione Intelligente**: Unione automatica delle voci che condividono lo stesso `device_id`, mostrando il nome comunicato dal ricevitore e prevenendo duplicati nel menu di selezione.

### 4. Libreria Personale & Gestione Caching
- Navigazione completa: Playlist, Brani che ti piacciono (Liked Songs), Album salvati, Artisti seguiti, Podcast ed Episodi salvati.
- Filtro rapido, fissaggio in alto (pin) e riordinamento degli elementi.
- **Interazione Rapida**: Doppio tocco/click su una playlist nella libreria per avviare subito la riproduzione; tocco singolo per aprire il dettaglio.
- **Modalità Elenco Compatto**: Opzione nelle impostazioni per una visualizzazione compressa a riga singola per traccia, con separatori spaziati: `Titolo  ·  Artisti  ·  Data di aggiunta`.
- **Ordinamento Flessibile**: Per Nome, Riproduzioni recenti o Data di salvataggio. Possibilità di seguire l'ordinamento cloud di Spotify o mantenere un arrangiamento locale personalizzato.
- **Gestione Fissaggio (Pin) Liked Songs**: Possibilità di spostare o rimuovere dai pin la sezione *Liked Songs*; la posizione locale viene preservata tra i riavvii dell'app.
- **Cache dei Brani che ti Piacciono**: Apertura istantanea da una cache di metadati legata allo specifico account. Le righe più datate si aggiornano silenziosamente in background, mentre le azioni di *Mi piace* / *Non mi piace* applicano una modifica ottimistica istantanea all'interfaccia.
- **Menu Contestuali**: Pressione prolungata (o tasto destro su tablet/desktop) su schede di album, artisti e podcast con foglio di azioni dedicate.

### 5. Sistema di Ricerca a Doppia Sorgente Isolato
- Ricerca estesa su brani, artisti, album, playlist, podcast ed episodi, con scheda "Miglior Risultato" (Top Result) e visualizzazioni dedicate per tipologia.
- **Isolamento delle Chiamate**: La ricerca del catalogo personale e la ricerca condivisa delle playlist operano in parallelo; se uno dei due servizi remoti fallisce o va in timeout, l'altra parte dei risultati viene comunque mostrata senza bloccare la schermata.
- Supporto completo agli appunti di sistema (Taglia, Copia, Incolla, Seleziona tutto).
- Layout reattivo dei campi di ricerca che evita sovrapposizioni con indicatori di stato o controlli del dispositivo in finestre o display compatti.

### 6. Home & Raccomandazioni Dinamiche
- Feed iniziale composto da "Made for You", "Ascoltati di recente", i tuoi artisti e brani preferiti, e raccomandazioni algoritmiche.
- Scorciatoie rapide per le playlist recenti con menu contestuale per ogni card della schermata.

### 7. Pagine Artista & Album con Riconoscimento EP
- **Pagina Artista**: Brani più popolari, discografia completa filtrabile (*Album*, *EP e singoli*, *Compilation*) e artisti correlati.
- **Riconoscimento Distintivo EP**: Le uscite che la Web API raggruppa genericamente come singoli vengono etichettate esplicitamente come **EP** quando i metadati di streaming confermano tale classificazione.
- **Paginazione Virtualizzata**: Nelle playlist e negli album, la barra di scorrimento riflette il numero totale effettivo dei brani; trascinando la vista su una sezione non ancora caricata in memoria, l'app richiede direttamente la porzione necessaria.
- **Navigazione Immediata**: Il tocco sui nomi degli artisti nella barra del player apre la relativa pagina istantaneamente, anche prima che i metadati della Web API abbiano completato la sincronizzazione locale.

### 8. Editing Avanzato Playlist & Drag-and-Drop
- Creazione, ridenominazione, descrizione, riordino e cancellazione delle playlist.
- **Auto-scroll ai Bordi**: Mantenendo premuto e trascinando un brano vicino al bordo superiore o inferiore della schermata, la lista scorre automaticamente per raggiungere posizioni fuori vista.
- **Caricamento Copertina**: Selezione e upload di immagini personalizzate in formato JPEG o PNG.
- **Selezione Multipla e Salvataggio Rapido**: La selezione di più righe mostra un badge di conteggio con evidenziazione neutrale traslucida (senza contorno); trascinando la selezione su *Liked Songs* si salvano simultaneamente tutte le tracce selezionate nell'ordine visualizzato.
- **Inserimento Diretto tra Righe**: Rilasciando un brano (dal player bar, dalla coda o da un'altra lista) tra due righe di una playlist aperta ed editabile, la traccia viene inserita in quella precisa posizione lasciando invariata la coda di riproduzione corrente.
- Supporto all'inserimento su playlist vuote e playlist collaborative condivise da altri utenti.
- Menu "Aggiungi a playlist" con campo di filtro rapido per nome per localizzare all'istante la playlist di destinazione.

### 9. Deep Linking & Universal Links
- Registrazione per la gestione dei link con schema proprietario `spotify:` (`spotify:track:...`, `spotify:album:...`, `spotify:artist:...`, `spotify:playlist:...`).
- Gestione automatica dei collegamenti web universali `https://open.spotify.com/...`, aprendo direttamente l'entità corrispondente all'interno dell'app sia a processo già avviato che a freddo.

### 10. Gestione Coda Dinamica & Cronologia Separata
- Coda visualizzabile come pannello laterale (su schermi grandi/tablet) o schermata dedicata, con indicazione chiara del contesto di origine.
- **Aggiunta in Coda Intelligente**: L'azione "Aggiungi alla coda" inserisce i brani subito dopo quelli già accodati manualmente dall'utente e prima della continuazione del contesto automatico.
- **Preservazione dei Duplicati**: L'accodamento di righe ripetute inserisce ogni occorrenza nell'ordine selezionato; un tocco ripetuto viene conteggiato una sola volta con notifica delle aggiunte effettive.
- **Storico "Ascoltati di recente"**: Conserva separatamente le riproduzioni ripetute di brani brevi, inclusi i replay locali consecutivi della stessa traccia. Ogni elemento nello storico mostra immediatamente il brano nel player bar all'avvio della riproduzione.

### 11. Integrità Cache & Buffer per Checkpoint Playlist
- **Verifica di Revisione**: Le playlist caricate dalla cache locale devono corrispondere alla revisione e al numero di tracce di Spotify prima di determinare l'ordine di riproduzione.
- **Aggiornamenti Ottimistici**: Le modifiche pendenti rimangono visibili nella UI e vengono scritte definitivamente nella cache locale solo a scrittura remota confermata. In caso di refresh fallito, le righe correnti vengono preservate e viene data la possibilità di riprovare.
- **Buffer di Checkpoint Streamed**: Il salvataggio e il caricamento dei grossi checkpoint JSON delle playlist avvengono attraverso un buffer di background a blocchi ridotti (64 KB), evitando allocazioni massive o duplicazioni dell'intero albero JSON nella memoria heap.

### 12. Testi Sincronizzati (Lyrics)
- Visualizzazione dei testi con evidenziazione e auto-scorrimento in tempo reale sincronizzato ai timestamp della traccia.
- Modalità pannello laterale o a schermo intero.
- Fallback automatico su testo non sincronizzato quando i timestamp non sono disponibili.

### 13. Ripristino Sessione all'Avvio
- Al lancio dell'applicazione, l'ultimo brano in ascolto viene ripristinato in pausa esattamente alla posizione temporale in cui era stato interrotto.
- Il pulsante Play riprende la traccia; i controlli di riproduzione (skip, seek, volume) sono interattivi prima ancora di riavviare l'audio.

### 14. Tema Dinamico con Tinting dalla Copertina (Dynamic Color)
- Le schermate e la barra del player estraggono dinamicamente una tonalità d'accento dalla copertina dell'album in ascolto (tramite Palette API su Android e Color extraction su iOS).
- Opzione nelle Impostazioni per disattivare il tinting dinamico a favore dei colori standard.
- Supporto completo ai temi Chiaro, Scuro o basato sulle impostazioni di sistema.

---

## Stack Tecnologico

| Componente | Android | iOS |
|---|---|---|
| **Linguaggio** | **Kotlin 2.0.20** | **Swift 5.10 / Swift 6** |
| **UI Framework** | Jetpack Compose + Material 3 | SwiftUI |
| **Audio Engine** | AndroidX Media3 (ExoPlayer) + SimpleCache | AVFoundation / CoreAudio |
| **Networking** | OkHttp 4.12 + Retrofit + Coroutines | URLSession + Swift Concurrency |
| **Discovery mDNS** | Android `NsdManager` + JmDNS | `Network.framework` (Bonjour) |
| **Persistenza Locale** | Room + DataStore | SwiftData / CoreData |
| **Palette Copertine** | AndroidX Palette KTX | CoreImage / Vision Palette |
| **Dependency Injection** | Koin | Swift Native Dependencies / Factory |

---

## Guida per Sviluppatori

### Requisiti
- **Android**: JDK 17 o superiore, Android SDK (API 34).
- **iOS**: macOS con Xcode 15 o superiore (quando inizierà l'integrazione del target iOS).

### Esecuzione e Compilazione Android

Tutti i moduli Android sono strutturati con il Version Catalog (`gradle/libs.versions.toml`).

```bash
cd android

# Verifica delle dipendenze e build del modulo principale
./gradlew :app:assembleDebug

# Esecuzione dei test unitari
./gradlew test
```

### Struttura iOS
La directory `ios/` contiene già l'alberatura completa dei moduli con file `.gitkeep` pronta per l'aggiunta dei file `.swift` e la configurazione del progetto Xcode / Swift Package Manager.
