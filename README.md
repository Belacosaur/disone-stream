# Disone — Android Hybrid Web3 Streaming Client

Production-ready Android hybrid torrent streaming app for Solana users. Connects to a Rust streaming server (stream-server) and optionally a Node.js backend for wallet auth and plan gating.

## Tech Stack

- **Kotlin** + **Jetpack Compose** + **MVVM**
- **Hilt** (DI), **Retrofit** (API), **OkHttp**
- **jlibtorrent** (torrent engine)
- **libVLC** (primary player), **ExoPlayer** (HLS fallback)
- **Solana Mobile Wallet Adapter** (wallet auth)
- **DataStore** + **EncryptedSharedPreferences** (secure JWT storage)
- **ForegroundService** (torrent + playback lifecycle)

**Min SDK:** 24 | **Target SDK:** 34

## Project Structure

```
app/
├── di/                 # Hilt modules
├── ui/
│   ├── screens/        # WalletConnect, Home, Player, etc.
│   ├── components/
│   ├── navigation/
│   └── theme/
├── core/
│   ├── addons/         # Stremio addon protocol (manifest, catalog, meta, streams)
│   ├── wallet/         # Solana MWA
│   ├── auth/
│   ├── api/
│   ├── catalog/
│   ├── torrent/
│   ├── player/
│   ├── streaming/
│   ├── playback/       # PendingPlayHolder (stream → Player)
│   ├── storage/
│   └── models/
├── service/            # TorrentService, PlaybackService
└── MainActivity.kt
```

## Setup

### 1. Clone & Open

```bash
cd disone-stream
```

Open in Android Studio or build from CLI:

```bash
./gradlew assembleDebug
```

### 2. Backend Configuration

The app expects:

- **Stream server** (Rust): `stream-server` in this repo
- **Auth backend** (optional): Node.js service with wallet auth

#### Stream server (required)

Run the Rust stream-server:

```bash
cd ../stream-server
cargo run
```

Default: `http://10.0.2.2:8080` (Android emulator localhost). Change in `di/AppModule.kt`:

```kotlin
@Named("apiBaseUrl") fun provideApiBaseUrl(): String = "http://YOUR_HOST:8080/"
```

#### Auth backend (optional)

For wallet-based auth, deploy a service with:

- `GET /auth/nonce?wallet=<pubkey>` → `{ "nonce": "..." }`
- `POST /auth/verify` with `{ "wallet", "signature", "nonce" }` → `{ "token", "expiresIn", "plan" }`


### 3. Wallet Adapter

- Uses **Solana Mobile Wallet Adapter 2.0**
- Requires an MWA-compatible wallet (e.g. Phantom, Solflare) installed on the device or emulator

### 4. Permissions

Declared in `AndroidManifest.xml`:

- `INTERNET`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `POST_NOTIFICATIONS`
- `ACCESS_NETWORK_STATE`

## Build & Release

```bash
# Debug
./gradlew assembleDebug

# Release
./gradlew assembleRelease
```

Signing: configure `signingConfigs` in `app/build.gradle.kts` for release builds.

## Stremio Addon Compatibility

Disone supports **Stremio-compatible addons** as remote HTTP JSON APIs. No Stremio SDK is embedded.

### Addon Protocol

Addons are remote HTTP endpoints that expose:

- **Manifest**: `GET {addonUrl}/manifest.json`
- **Catalog**: `GET {addonUrl}/catalog/{type}/{id}.json`
- **Meta**: `GET {addonUrl}/meta/{type}/{id}.json`
- **Streams**: `GET {addonUrl}/stream/{type}/{videoId}.json`

### Installing Addons

1. Open **Settings** → **Addon Manager**
2. Tap **+** and enter the addon URL (e.g. `https://v3-cinemeta.strem.io`)
3. The app fetches the manifest, validates it, and stores the addon locally
4. Enable/disable or remove addons from the list

### Stream Normalization

All addon streams are normalized to `DisoneStream`:

- If `infoHash` is present → magnet is built: `magnet:?xt=urn:btih:<infoHash>`
- If `url` is present → treated as hosted candidate (backend decides)
- If both → magnet preferred for P2P tier

**Critical**: Addon streams **never bypass the backend**. All playback requests go through `POST /play`. The backend returns `mode` (P2P, HOSTED_PROGRESSIVE, HOSTED_HLS) and the final playback URL or magnet.

### Premium Gating

- **P2P tier**: Only magnet-based streams are playable; hosted-only streams show a "Premium Required" badge
- **PREMIUM tier**: Backend may override to hosted fallback (HLS, progressive) when available
- Plan is determined by the backend only

### Catalog Aggregation

The Home screen merges catalogs from all enabled addons with the backend catalog. Results are deduplicated by ID. Addon catalogs use `type:id` (e.g. `movie:tt1234567`) for stream fetching.

## Playback Logic

When the user taps **Play**:

1. App calls `POST /play` with `{ "magnet", "client_supports_p2p": true }` or `{ "url", "info_hash", ... }` for addon streams
2. Server returns `mode`, `stream_url`, `hls_url`, or `torrent_metadata`
3. App behavior:
   - **P2P**: Add torrent with jlibtorrent, buffer ~5MB, play via libVLC
   - **HOSTED_PROGRESSIVE**: Play via libVLC
   - **HOSTED_HLS**: Play via ExoPlayer

## Testing

- **Unit tests**: Torrent prioritization, wallet signature flow
- **Integration**: Magnet → buffer → playback
- **Edge cases**: No peers, token expired, backend unavailable, low storage

## Design

- Dark-first theme
- Deep indigo primary, neon teal accent
- Minimalistic UI with smooth animations

## License

Proprietary.
