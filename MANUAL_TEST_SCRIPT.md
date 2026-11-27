# WatchTogether — Manual Multi-Device Test Script

Verify real-world synchronization across **2 physical Android phones + 1 laptop (Android Emulator / Chromebook / Tablet)** on **different networks** with **NO API key configured**.

## Setup

- **Device A (Host — Phone 1)**: Connected to **5G / Cellular Data**.
- **Device B (Guest 1 — Phone 2)**: Connected to **Home Wi-Fi**.
- **Device C (Guest 2 — Laptop / Tablet / Emulator)**: Connected to **Different Hotspot / Broadband**.
- Install the WatchTogether APK on all 3 devices (zero `YOUTUBE_API_KEY` set).

---

## Test 1: Keyless Room Creation, Deep Link & Join (< 30s)

1. On **Device A (Host)**, sign in with Google and tap **Create Room**.
2. Tap the **6-character room code pill** in the top bar (confirm toast `"Room code XXXXXX copied!"`), then tap **Share** and send the `watchtogether://room/XXXXXX` invite link to Device B & Device C.
3. On **Device B**, open the invite link directly (verifies deep linking).
4. On **Device C**, type the 6-character code manually on the Home screen and tap **Join Room**.
5. **Pass Criteria**: All 3 devices show `3` participants in the room header and load the starter YouTube video inside an unobstructed YouTube player with `"YouTube"` attribution and `"Report"` visible below the player.

## Test 2: Synced Playback, Seek & Speed Across Different Networks

1. On **Device A (Host)**, press **Play** in the sync bar.
2. Wait 15 seconds and compare timestamps across Devices A, B, and C.
3. On **Device A**, tap **+10s** twice, then change speed to **1.25x**, then **Pause**, then **Play** again.
4. **Pass Criteria**: All 3 devices stay within **~1 second** of each other, speed changes apply smoothly, and no playback echo/oscillation occurs.

## Test 3: Late Joiner & Disconnect / Reconnect Recovery

1. While Devices A & B are playing at ~01:00, toggle Airplane Mode ON on **Device C** for 12 seconds, then turn Airplane Mode OFF.
2. **Pass Criteria**: Devices A & B keep playing without pausing or jumping; Device C automatically restores and seeks to ~01:12 when connectivity returns.

## Test 4: Buffering Isolation (>3s) & Optional "Wait for Everyone"

1. Throttle **Device B** to slow 3G (or switch networks mid-playback) until YouTube buffers for >3 seconds.
2. **Pass Criteria (Default — Wait for Everyone OFF)**: Device B displays `"Catching up…"` in the status strip while Devices A & C keep playing uninterrupted.
3. Open **Host Controls** on Device A, enable **"Wait for everyone"**, and verify the toggle persists.

## Test 5: YouTube Ad Handling (Commercial-Safe)

1. On **Device A**, open the Content Picker, paste a monetized YouTube video link (e.g., an official music video or news clip) and tap **Play on YouTube IFrame**.
2. If an ad plays on **Device B** (but not A or C), observe for the duration of the ad.
3. **Pass Criteria**: Ad plays unobstructed with full YouTube controls (never hidden or skipped); Devices A & C stay in sync uninterrupted; Device B displays `"Ad • Resyncs after"` and automatically jumps to the room's live position once the ad ends.

## Test 6: Non-Embeddable Error Fallback & Background Play Rule

1. Paste a video ID whose creator disabled embedding (triggers YouTube error `101`/`150`).
2. **Pass Criteria**: Friendly message appears with **"Open on YouTube"**, **"Pick Another"** (for host), and **"Retry"** buttons.
3. Press the Android Home button while a YouTube video is playing on Device A.
4. **Pass Criteria**: YouTube playback pauses immediately (no background audio for YouTube content), and PiP button is hidden for YouTube rooms. Returning to the app resyncs to the room.

## Test 7: Room Queue (Suggest → Host Approve → Reorder → Play Next)

1. On **Device B (Guest)**, open **Queue**, paste a YouTube URL, and tap **+**. Confirm it appears under **Guest Suggestions (Awaiting Host Approval)**.
2. On **Device A (Host)**, open **Queue**, tap **✓ Approve**, reorder using **↑ / ↓**, and tap **▶** to play next.
3. **Pass Criteria**: All 3 devices switch to the queued video in sync.
