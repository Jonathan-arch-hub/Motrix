# Motrix Android

Download manager for Android powered by [aria2](https://aria2.github.io/). Ported from [Motrix](https://github.com/agalwood/Motrix) desktop app.

## Screens

[Download for Android](https://github.com/Jonathan-arch-hub/Motrix/releases/tag/v1.0.0).

| Screen | Description |
|--------|-------------|
| **TaskList** | Main screen with subnav tabs (Active / Waiting / Stopped), panel header with global action buttons, download cards with animated progress bar, percentage, speed (↑↓), ETA, status pill, floating speedometer |
| **AddTask** | Paste URL, auto-extract filename from URL, pick .torrent file, select download directory |
| **TaskDetail** | Tabs: General (info rows), Files (file list with selection), Peers (for torrents) |
| **Browser** | Built-in WebView with URL bar, back/forward/refresh/stop navigation, download button to send URL to AddTask |
| **Settings** | Theme (System/Light/Dark), Language (English/العربية/中文), Max concurrent downloads, Max connections per server, Split count, User-Agent, Proxy, Seed ratio/time, Auto-sync trackers, Notifications toggle, Resume all on start, Keep seeding |

## Features

- **Protocols**: HTTP/HTTPS (with OpenSSL), FTP, BitTorrent, Magnet links
- **Multi-thread**: Up to 16 connections per server (configurable), split file into segments
- **Queue management**: Pause, resume, stop, purge completed, delete with file
- **Background**: Foreground Service with persistent notification, survives app close
- **Notification**: Progress bar, download speed, per-task lines in expanded view, Pause/Resume/Stop actions
- **Auto-start**: Engine starts on app launch
- **Language**: English, Arabic (العربية), Chinese (简体中文) — RTL supported
- **Theme**: Matches original Motrix colors exactly (#5B5BFA primary, #FF6157 error, #2ACB42 complete, #737373 waiting) — System/Light/Dark modes
- **Tracker auto-sync**: Fetches tracker lists from multiple GitHub sources on engine start
- **Session persistence**: Saves aria2 session file, resumes downloads after restart
- **Thunder link decoding**: Thunder:// URLs decoded automatically
- **Delete with file**: Removes task from queue and deletes downloaded files from filesystem

## Architecture

```
UI (Jetpack Compose + Navigation Compose)
  -> ViewModels (Hilt @HiltViewModel)
    -> DownloadManager (injected singleton)
      -> Aria2Client (typed Kotlin coroutine API)
        -> JSONRPCClient (WebSocket JSON-RPC 2.0)
          -> Aria2Engine (native process manager)
            -> aria2c binary (cross-compiled from source)
```

### Layer Details

**`JSONRPCClient.kt`** — WebSocket client using OkHttp. Handles JSON-RPC 2.0 request/response/notification protocol. Auto-reconnects on disconnect. Sends method calls (e.g. `aria2.addUri`, `aria2.tellActive`) and routes responses back via `CompletableDeferred` map.

**`Aria2Client.kt`** — Typed Kotlin coroutine API wrapping JSONRPCClient. Functions like `addUri()`, `tellActive()`, `pause()`, `remove()`. Converts JSON responses into typed data classes. Maps aria2 `gid` strings to typed `TaskId` and status enums.

**`Aria2Engine.kt`** — Manages the aria2c native process lifecycle. Starts the binary with generated config (port 16800, WebSocket enabled, session file, DHT, proxy, tracker list). Monitors process health. Generates `aria2.conf` at runtime with Android-optimized defaults. Detects crashes and restarts.

**`DownloadManager.kt`** — Orchestrator layer. Fetches task lists (active/waiting/stopped), computes global stats (total speed, progress, active count), provides reactive flows (`allTasks`, `globalStat`) via `StateFlow`. Methods: `startEngine`, `stopEngine`, `addUri`, `addTorrent`, `pauseTask`, `resumeTask`, `removeTask`, `forceRemoveTask`, `pauseAll`, `resumeAll`, `purgeCompleted`, `saveSession`.

### Engine Communication

```
App                 aria2c (native process)
 |                        |
 |--- WebSocket JSON-RPC  |
 |    localhost:16800     |
 |                        |
 |--- "aria2.addUri" ---->|
 |<-- { "result":"gid" } -|
 |                        |
 |--- "aria2.tellActive" >|
 |<-- [ task[] ] ---------|
 |                        |
 |--- "aria2.changeUri" ->|
 |<-- { ... } ------------|
```

### Data Flow

```
User taps "Pause" on a task card
  -> TaskListScreen calls viewModel.pauseTask(gid)
    -> TaskListViewModel calls downloadManager.pauseTask(gid)
      -> DownloadManager calls aria2Client.pause(gid)
        -> Aria2Client sends JSON-RPC "aria2.pause" via WebSocket
          -> aria2c pauses the download
            -> aria2c sends WebSocket notification "aria2.onDownloadPause"
              -> JSONRPCClient receives notification
                -> triggers tellActive() refresh via polling
                  -> StateFlow updates -> Composable recomposes
```

## UI Components

### TaskCard
- Animated progress bar (smooth `animateFloatAsState` with `tween(500ms)`)
- Percentage text (e.g. "75%")
- Downloaded / Total size (e.g. "120 MB / 500 MB")
- Download speed with ↓ arrow, Upload speed with ↑ arrow
- ETA computed from `remaining bytes / downloadSpeed`
- Status pill (Active = blue, Completed = green, Paused = gray, Error = red)
- Action buttons (Pause/Resume, Stop, Delete)
- Swipe to delete with confirmation dialog (Delete Only Task / Delete With File)

### Speedometer
Floating card showing current download speed (e.g. "5.2 MB/s ↓"), updates every 1.5s via global stat polling.

### Subnav Bar
Three tabs: Active (downloading), Waiting (queued/paused), Stopped (completed/error). Count badges on each tab.

### Panel Header
Global action bar with: Play All, Pause All, Purge Completed buttons.

## Notification

Compact view:
```
Motrix
2 active downloads | 5.2 MB/s ↓
[=============-------------] 65%
[Pause] [Resume] [Stop]
```

Expanded view (InboxStyle):
```
Motrix — 5.2 MB/s ↓ | 2 active
─────────────────────────
file1.mp4 — 75% (2.4 MB/s)
file2.zip — 45% (1.8 MB/s)
─────────────────────────
[Pause] [Resume] [Stop]
```

- Updates every 1.5s via coroutine polling
- Uses `android.R.drawable.stat_sys_download` icon
- Notification channel: `Motrix Downloads`
- Foreground service type: `FOREGROUND_SERVICE_TYPE_DATA_SYNC`
- Action buttons: Pause all, Resume all, Stop engine

## Theme & Colors

Exact Motrix palette (no dynamic colors):

| Token | Light | Dark |
|-------|-------|------|
| Primary | `#5B5BFA` | `#7C7CFF` |
| On Primary | `#FFFFFF` | `#1C1B1F` |
| Error | `#FF6157` | `#FF8A82` |
| Complete | `#2ACB42` | `#5CE073` |
| Paused/Waiting | `#737373` | `#9E9E9E` |
| Surface | `#FFFFFF` | `#1E1E1E` |
| Background | `#F5F5F5` | `#121212` |
| On Surface | `#1C1B1F` | `#E6E1E5` |
| Surface Variant | `#E7E0EC` | `#49454F` |

## Languages

| Code | Language | Direction |
|------|----------|-----------|
| `en` | English | LTR |
| `ar` | العربية (Arabic) | RTL |
| `zh-rCN` | 简体中文 (Chinese) | LTR |

Locale switching via `attachBaseContext` override in `MainActivity`. Selected language stored in DataStore, applied on next launch.

## Building

### Prerequisites

- Android SDK 34+
- Android NDK 28.2+ (r28c)
- Java 17
- Gradle 8.5+

### 1. Build aria2 from source

**No prebuilt binaries are used.** aria2 (with OpenSSL) is cross-compiled from source:

```bash
# Build for arm64-v8a (default)
./scripts/build-aria2.sh

# Build specific ABI
./scripts/build-aria2.sh --abi arm64-v8a

# Build with custom version
ARIA2_VERSION=1.37.0 ./scripts/build-aria2.sh

# Clean + rebuild
./scripts/build-aria2.sh --clean
```

The script:
1. Downloads and extracts OpenSSL, cross-compiles it for the target ABI
2. Clones aria2 source from the official GitHub repo
3. Configures with `--with-openssl` for HTTPS support
4. Cross-compiles using Android NDK toolchain (arm-linux-androideabi-clang)
5. Strips the binary and copies to `app/src/main/jniLibs/{abi}/libaria2c.so`

### 2. Build the APK

```bash
# Debug
./gradlew assembleDebug

# Release (signs with keystore)
./gradlew assembleRelease

# Force clean rebuild
./gradlew clean assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### 3. Upgrade aria2 version

```bash
# Method 1: Environment variable
ARIA2_VERSION=1.38.0 ./scripts/build-aria2.sh && ./gradlew assembleRelease

# Method 2: Edit aria2.properties
# ARIA2_VERSION=1.38.0
./scripts/build-aria2.sh && ./gradlew assembleRelease
```

## aria2 Configuration

Runtime-generated `aria2.conf` at `app/src/main/assets/engine/aria2.conf`:

```
enable-rpc=true
rpc-listen-port=16800
rpc-listen-all=false
rpc-secret=motrix
enable-mmap=true
enable-color=false
console-log-level=warn
log-level=debug
max-connection-per-server=16
split=16
min-split-size=1M
max-concurrent-downloads=5
continue=true
allow-overwrite=true
auto-file-renaming=true
file-allocation=none
remote-time=true
human-readable=true
save-session=/path/to/session.txt
input-file=/path/to/session.txt
save-session-interval=30
bt-enable-lpd=true
bt-max-open-files=100
bt-remove-unselected-file=true
enable-dht=true
dht-listen-port=16801
seed-ratio=1.0
seed-time=60
```

## Project Structure

```
MotrixAndroid/
├── app/src/main/
│   ├── java/com/motrix/download/
│   │   ├── engine/                # aria2 communication layer
│   │   │   ├── JSONRPCClient.kt   # WebSocket JSON-RPC 2.0
│   │   │   ├── Aria2Client.kt     # Typed aria2 API
│   │   │   ├── Aria2Engine.kt     # Process lifecycle
│   │   │   └── DownloadManager.kt # High-level orchestrator
│   │   ├── domain/model/          # Data classes and enums
│   │   ├── data/datastore/        # PreferenceDataStore
│   │   ├── di/                    # Hilt DI modules
│   │   ├── service/
│   │   │   ├── DownloadForegroundService.kt # Foreground notification + engine lifecycle
│   │   │   └── BootReceiver.kt              # Boot-time engine restart
│   │   ├── ui/
│   │   │   ├── tasklist/          # Main download list (cards, subnav, speedometer)
│   │   │   ├── addtask/           # New download (URL, torrent picker)
│   │   │   ├── taskdetail/        # Task details (files, peers, info)
│   │   │   ├── browser/           # WebView browser
│   │   │   ├── settings/          # App settings
│   │   │   ├── navigation/        # NavGraph, Screen routes
│   │   │   ├── theme/             # Motrix exact colors, dark/light schemes
│   │   │   └── viewmodel/         # ViewModels (Hilt)
│   │   ├── util/
│   │   │   ├── Constants.kt       # Shared constants
│   │   │   ├── FormatUtils.kt     # Bytes -> human readable
│   │   │   └── TrackerUtils.kt    # Tracker list fetching
│   │   ├── MainActivity.kt        # Entry point, locale switching
│   │   └── MotrixApp.kt           # Application class
│   ├── jniLibs/arm64-v8a/         # aria2 native binary
│   │   └── libaria2c.so           # Cross-compiled with OpenSSL, stripped
│   └── res/
│       ├── values/                # English strings.xml
│       ├── values-ar/             # Arabic strings.xml
│       ├── values-zh-rCN/         # Chinese strings.xml
│       └── mipmap-*/              # App icon at all densities
├── scripts/
│   └── build-aria2.sh             # Cross-compile aria2 for Android
├── aria2.properties               # aria2 version config
├── gradlew                        # Gradle wrapper
└── build.gradle.kts               # Root build file
```

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| "Failed to start engine" | aria2c binary not found or incompatible | Rebuild with `./scripts/build-aria2.sh --abi arm64-v8a` |
| HTTPS downloads fail | aria2 built without OpenSSL | Rebuild: pass `--with-openssl` flag, check `aria2.properties` |
| Notification shows "Engine running" but no progress | Polling not started | Restart the app; check `DownloadForegroundService` logs |
| Delete with file doesn't remove files | File path mismatch | Check `filesDir` path; edit `removeTaskWithFile()` |
| Download errorCode=3 (Resource not found) | URL expired or server issue | Try the link in browser first |
| App crashes on locale change | Resources not reloaded | Force stop and reopen app |
| Gradle build fails: SDK not found | ANDROID_HOME not set | Run `export ANDROID_HOME=/opt/android-sdk` |
| Blank notification | Custom icon tint issue (fixed in v1.1.0) | Use system download icon |

## Download APK

Pre-built APKs are available in the `dist/` directory:

| File | Size | Type | Signing |
|------|------|------|---------|
| `dist/Motrix-v1.1.0-release.apk` | 16 MB | Release (signed) | `motrix-release.jks` (included) |
| `dist/Motrix-v1.1.0-debug.apk` | 21 MB | Debug (unsigned) | Auto-signed with debug key |

### Installation

```bash
# Install release APK on connected device
adb install dist/Motrix-v1.1.0-release.apk

# Or for debug
adb install dist/Motrix-v1.1.0-debug.apk
```

### Verify APK signature

```bash
apksigner verify --print-certs dist/Motrix-v1.1.0-release.apk
```

### Build from source (recommended for production)

See [Building](#building) section above. Building from source ensures the aria2 binary matches your device architecture.

## License

MIT
