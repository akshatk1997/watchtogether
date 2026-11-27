# WatchTogether (Android) — Commercial-Safe YouTube Engine

WatchTogether is a dark-first, Hearo-style synchronized watch party Android app built with Kotlin, Jetpack Compose, Material 3, Firebase Firestore real-time sync, Media3 ExoPlayer, and the official YouTube IFrame Player API — **working completely with zero API keys required**.

## Commercial-Safe YouTube Engine

- **Official Embedded Player Only**: Loads `https://www.youtube.com/iframe_api` with `host: "https://www.youtube-nocookie.com"` (privacy-enhanced mode) inside an unobstructed WebView (`>= 480x270dp` on wide screens, full width on mobile). Never scrapes, downloads, re-encodes, blocks ads, or alters YouTube's player UI.
- **No Background Playback for YouTube**: Automatically pauses YouTube playback on `ON_PAUSE`/`ON_STOP` and disables Picture-in-Picture for YouTube rooms.
- **30-Second Clock-Offset & 3-Tier Drift Sync**:
  - Measures client-server clock offset via NTP-style ping/pong every 30 seconds (`ClockOffsetEstimator`).
  - `< 0.3s` drift: ignored (restores base speed).
  - `0.3s – 2.0s` drift: smooth `playbackRate` nudge (`±0.05x`, e.g., `1.05x`/`0.95x`) until drift closes, then restored.
  - `> 2.0s` drift: `seekTo()` to expected server position.
  - Echo guard suppresses programmatic command feedback loops.
- **Buffering & Best-Effort Ad Isolation**:
  - Local buffering `> 3s` shows `"Catching up…"` without pausing other viewers (optional host **"Wait for everyone"** toggle in Host Controls).
  - Best-effort ad detection suspends drift correction and seeks only for the viewer watching an ad, never blocks/hides/skips ads, and auto-resyncs as soon as normal content resumes.
- **Friendly Error Recovery**: Maps YouTube IFrame error codes (`2`, `5`, `100`, `101`, `150`, age-restricted/region-blocked) to friendly room notices with **Open on YouTube**, **Pick Another**, and **Retry**.
- **Pluggable `SearchProvider` & Room Queue**:
  - `PasteOnlyProvider` (default, zero API key): parses `watch`, `youtu.be`, `shorts`, `live`, `embed`, and `/playlist?list=...` URLs with keyless oEmbed metadata lookup plus in-app interactive YouTube browsing.
  - `YouTubeDataApiProvider` (optional): activates only if `YOUTUBE_API_KEY` is provided in the build environment, with 24-hour caching, debounced queries, and automatic fallback when the key is missing or quota is exceeded.
  - **Room Queue**: any participant can suggest YouTube videos; host approves, reorders, plays next, and auto-advances on video end.

## YouTube Terms of Service & Policy Compliance

Built directly into the app and legal settings (`Settings → YouTube API Services & Legal`):

1. **Attribution & Legal Links**: Displays `"YouTube"` attribution wherever YouTube content appears and links to the [YouTube Terms of Service](https://www.youtube.com/t/terms) and [Google Privacy Policy](https://policies.google.com/privacy), disclosing that using WatchTogether means using YouTube API Services.
2. **No Caching / Republishing Video Content**: Stores only 11-character video IDs and minimal transient room metadata (title, channel, thumbnail) refreshed via official oEmbed; never stores, caches, downloads, or republishes video streams.
3. **No Audio/Video Separation or Ad Interference**: Never separates audio from video, never blocks/skips/obscures ads, and never places app ads over, around, or inside the YouTube player area.
4. **No Paywalling YouTube Access**: Any future paid features must apply strictly to WatchTogether features (room capacity, voice rooms, custom themes), never to access YouTube content.
5. **Direct Video Reporting**: Includes a `"Report"` button below the player linking directly to YouTube's official reporting flow.

## Running Tests

- **Unit tests (clock offset, 3-tier drift, rate nudge, ad detection, SearchProvider, error mapping) & Host + 3 Guests integration + simulated-ad tests**:
  ```bash
  gradle :app:testDebugUnitTest --tests "com.example.SyncEngineTest" --tests "com.example.SyncIntegrationTest"
  ```
- **Manual Multi-Device Test Script** (2 real phones + 1 laptop across different networks): see [`MANUAL_TEST_SCRIPT.md`](./MANUAL_TEST_SCRIPT.md).
- **Firestore Security Rules Tests**:
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
  ```
