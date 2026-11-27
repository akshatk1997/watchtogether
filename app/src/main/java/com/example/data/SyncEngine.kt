package com.example.data

import java.security.SecureRandom
import kotlin.math.abs

/**
 * Authoritative room playback snapshot held as the single source of truth.
 */
data class AuthoritativeSyncState(
    val videoId: String,
    val playbackState: PlaybackState,
    val positionAtTimestampMs: Long,
    val serverTimeMs: Long,
    val playbackRate: Double = 1.0,
    val controllerId: String = "",
    val everyoneCanControl: Boolean = false,
    val hostId: String = "",
    val waitForEveryone: Boolean = false,
    val revision: Long = 0L
)

enum class DriftCorrectionMode {
    NONE,
    RATE_NUDGE,
    HARD_SEEK,
    SUSPENDED_FOR_AD
}

/**
 * Result of evaluating an authoritative server snapshot against local playback state.
 */
data class LocalSyncDecision(
    val loadNewVideo: Boolean,
    val targetVideoId: String,
    val targetState: PlaybackState,
    val expectedPositionMs: Long,
    val correctionMode: DriftCorrectionMode,
    val shouldSeek: Boolean,
    val targetRate: Double,
    val baseRate: Double,
    val driftMs: Long
)

/**
 * NTP-style clock-offset sample from a ping/pong handshake:
 * - t0: client timestamp when ping was sent
 * - t1: server timestamp when ping was received
 * - t2: server timestamp when pong was sent
 * - t3: client timestamp when pong was received
 */
data class ClockSyncSample(
    val offsetMs: Long,
    val roundTripMs: Long,
    val measuredAtMs: Long
)

/**
 * Maintains a rolling low-RTT median clock offset between the client clock and authoritative server clock.
 * Recomputed every 30 seconds during active room playback.
 */
class ClockOffsetEstimator(private val maxSamples: Int = 7) {
    private val samples = ArrayDeque<ClockSyncSample>()

    fun recordPingPong(
        t0ClientSendMs: Long,
        t1ServerReceiveMs: Long,
        t2ServerSendMs: Long,
        t3ClientReceiveMs: Long
    ): ClockSyncSample {
        val rtt = ((t3ClientReceiveMs - t0ClientSendMs) - (t2ServerSendMs - t1ServerReceiveMs))
            .coerceAtLeast(0L)
        val offset = ((t1ServerReceiveMs - t0ClientSendMs) + (t2ServerSendMs - t3ClientReceiveMs)) / 2L
        val sample = ClockSyncSample(offsetMs = offset, roundTripMs = rtt, measuredAtMs = t3ClientReceiveMs)
        if (samples.size >= maxSamples) {
            samples.removeFirst()
        }
        samples.addLast(sample)
        return sample
    }

    /**
     * Computes the estimated offset (serverTime - clientTime) using the lowest-RTT half of samples
     * to reject transient network jitter spikes.
     */
    fun currentOffsetMs(): Long {
        if (samples.isEmpty()) return 0L
        val bestByRtt = samples.sortedBy { it.roundTripMs }
        val takeCount = ((bestByRtt.size + 1) / 2).coerceAtLeast(1)
        val filteredOffsets = bestByRtt.take(takeCount).map { it.offsetMs }.sorted()
        return filteredOffsets[filteredOffsets.size / 2]
    }

    fun estimatedServerNowMs(clientNowMs: Long): Long = clientNowMs + currentOffsetMs()

    fun sampleCount(): Int = samples.size

    fun reset() {
        samples.clear()
    }
}

/**
 * Best-effort ad state signal observed from the official YouTube IFrame Player API.
 * Used ONLY to suspend drift correction and seeks while YouTube plays an ad to a viewer.
 */
data class AdDetectionSignal(
    val expectedVideoId: String,
    val reportedVideoId: String?,
    val reportedDurationSec: Double,
    val expectedContentDurationSec: Double,
    val recentStateTransitionsIn2Sec: Int
)

object SyncEngine {
    // 3-tier drift thresholds:
    //  < 0.3s  -> do nothing (restore base rate)
    //  0.3..2s -> gentle playbackRate nudge (+/- 0.05) until closed
    //  > 2.0s  -> hard seekTo()
    const val DRIFT_IGNORE_THRESHOLD_MS = 300L
    const val DRIFT_SEEK_THRESHOLD_MS = 2000L
    const val RATE_NUDGE_DELTA = 0.05
    const val CLOCK_SYNC_INTERVAL_MS = 30_000L
    const val BUFFERING_NOTICE_THRESHOLD_MS = 3_000L
    const val ECHO_SUPPRESSION_WINDOW_MS = 900L
    const val EMPTY_ROOM_EXPIRATION_MS = 5 * 60 * 1000L
    const val MAX_POSITION_MS = 86_400_000L
    const val DEFAULT_DRIFT_THRESHOLD_MS = DRIFT_SEEK_THRESHOLD_MS

    private const val ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    private val youtubePatterns = listOf(
        Regex("""(?:youtube\.com/watch\?.*v=|youtu\.be/|youtube\.com/embed/|youtube-nocookie\.com/embed/|youtube\.com/live/|youtube\.com/shorts/)([A-Za-z0-9_-]{11})"""),
        Regex("""^([A-Za-z0-9_-]{11})$""")
    )

    private val youtubePlaylistPattern = Regex("""(?:youtube\.com|youtu\.be)/.*[?&]list=([A-Za-z0-9_-]{10,})""")

    private val drivePatterns = listOf(
        Regex("""drive\.google\.com/file/d/([A-Za-z0-9_-]+)"""),
        Regex("""drive\.google\.com/open\?id=([A-Za-z0-9_-]+)"""),
        Regex("""drive\.google\.com/uc\?.*id=([A-Za-z0-9_-]+)""")
    )

    fun generateRoomCode(): String {
        return buildString(6) {
            repeat(6) {
                append(ROOM_CODE_ALPHABET[random.nextInt(ROOM_CODE_ALPHABET.length)])
            }
        }
    }

    fun normalizeRoomCode(raw: String): String =
        raw.trim().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(6)

    fun isValidRoomCode(code: String): Boolean =
        code.length == 6 && code.all { it in 'A'..'Z' || it in '0'..'9' }

    /**
     * Computes delay (ms) until a scheduled server action moment, accounting for local clock offset.
     */
    fun computeScheduledDelayMs(
        targetServerTimeMs: Long,
        clientNowMs: Long,
        clockOffsetMs: Long
    ): Long {
        val estimatedServerNow = clientNowMs + clockOffsetMs
        return (targetServerTimeMs - estimatedServerNow).coerceAtLeast(0L)
    }

    /**
     * Computes the authoritative expected playback position (ms) right now, accounting for elapsed
     * server time since [syncUpdatedAtMs] when PLAYING.
     */
    fun computeExpectedPositionMs(
        roomPositionMs: Long,
        syncUpdatedAtMs: Long,
        nowMs: Long,
        playbackState: PlaybackState,
        playbackRate: Double
    ): Long {
        val safeBase = roomPositionMs.coerceIn(0L, MAX_POSITION_MS)
        if (playbackState != PlaybackState.PLAYING) {
            return safeBase
        }
        val elapsedMs = (nowMs - syncUpdatedAtMs).coerceAtLeast(0L)
        val safeRate = playbackRate.coerceIn(0.25, 2.0)
        val projected = safeBase + (elapsedMs * safeRate).toLong()
        return projected.coerceIn(0L, MAX_POSITION_MS)
    }

    /**
     * Computes the desired playbackRate for gentle drift closure when |drift| is between 0.3s and 2.0s.
     * - If local is behind expected (driftMs < -300ms): nudge up (+0.05, e.g., 1.05x).
     * - If local is ahead of expected (driftMs > +300ms): nudge down (-0.05, e.g., 0.95x).
     * - Otherwise restore [baseRate].
     */
    fun computeNudgedPlaybackRate(
        driftMs: Long,
        baseRate: Double,
        ignoreThresholdMs: Long = DRIFT_IGNORE_THRESHOLD_MS,
        seekThresholdMs: Long = DRIFT_SEEK_THRESHOLD_MS,
        nudgeDelta: Double = RATE_NUDGE_DELTA
    ): Double {
        val safeBase = baseRate.coerceIn(0.25, 2.0)
        val absDrift = abs(driftMs)
        if (absDrift < ignoreThresholdMs || absDrift > seekThresholdMs) {
            return safeBase
        }
        val nudged = if (driftMs < 0L) safeBase + nudgeDelta else safeBase - nudgeDelta
        return nudged.coerceIn(0.5, 2.0)
    }

    /**
     * Returns true when local playback has drifted beyond the hard-seek threshold.
     */
    fun shouldCorrectDrift(
        localPositionMs: Long,
        expectedPositionMs: Long,
        thresholdMs: Long = DRIFT_SEEK_THRESHOLD_MS
    ): Boolean {
        return abs(localPositionMs - expectedPositionMs) > thresholdMs
    }

    fun computeDriftMs(localPositionMs: Long, expectedPositionMs: Long): Long =
        localPositionMs - expectedPositionMs

    /**
     * Evaluates an incoming authoritative state against a client's current local playback state
     * using the 3-tier drift algorithm (<0.3s ignore, 0.3s-2s rate nudge, >2s seekTo) and
     * automatically suspends all correction while a viewer is watching a YouTube ad.
     */
    fun evaluateSyncDecision(
        currentVideoId: String,
        localPositionMs: Long,
        authoritative: AuthoritativeSyncState,
        serverNowMs: Long,
        forceSeek: Boolean = false,
        isAdPlaying: Boolean = false,
        ignoreThresholdMs: Long = DRIFT_IGNORE_THRESHOLD_MS,
        seekThresholdMs: Long = DRIFT_SEEK_THRESHOLD_MS
    ): LocalSyncDecision {
        val baseRate = authoritative.playbackRate.coerceIn(0.25, 2.0)
        val expectedMs = computeExpectedPositionMs(
            roomPositionMs = authoritative.positionAtTimestampMs,
            syncUpdatedAtMs = authoritative.serverTimeMs,
            nowMs = serverNowMs,
            playbackState = authoritative.playbackState,
            playbackRate = baseRate
        )
        val isNewVideo = currentVideoId != authoritative.videoId
        val drift = computeDriftMs(localPositionMs, expectedMs)

        // Never interrupt or seek during an active YouTube ad on this client
        if (isAdPlaying && !isNewVideo) {
            return LocalSyncDecision(
                loadNewVideo = false,
                targetVideoId = authoritative.videoId,
                targetState = authoritative.playbackState,
                expectedPositionMs = expectedMs,
                correctionMode = DriftCorrectionMode.SUSPENDED_FOR_AD,
                shouldSeek = false,
                targetRate = baseRate,
                baseRate = baseRate,
                driftMs = drift
            )
        }

        if (isNewVideo || forceSeek || abs(drift) > seekThresholdMs) {
            return LocalSyncDecision(
                loadNewVideo = isNewVideo,
                targetVideoId = authoritative.videoId,
                targetState = authoritative.playbackState,
                expectedPositionMs = expectedMs,
                correctionMode = DriftCorrectionMode.HARD_SEEK,
                shouldSeek = true,
                targetRate = baseRate,
                baseRate = baseRate,
                driftMs = drift
            )
        }

        // Between 0.3s and 2.0s during active playback: smoothly nudge playbackRate
        if (authoritative.playbackState == PlaybackState.PLAYING && abs(drift) >= ignoreThresholdMs) {
            val nudgedRate = computeNudgedPlaybackRate(
                driftMs = drift,
                baseRate = baseRate,
                ignoreThresholdMs = ignoreThresholdMs,
                seekThresholdMs = seekThresholdMs
            )
            return LocalSyncDecision(
                loadNewVideo = false,
                targetVideoId = authoritative.videoId,
                targetState = authoritative.playbackState,
                expectedPositionMs = expectedMs,
                correctionMode = DriftCorrectionMode.RATE_NUDGE,
                shouldSeek = false,
                targetRate = nudgedRate,
                baseRate = baseRate,
                driftMs = drift
            )
        }

        return LocalSyncDecision(
            loadNewVideo = false,
            targetVideoId = authoritative.videoId,
            targetState = authoritative.playbackState,
            expectedPositionMs = expectedMs,
            correctionMode = DriftCorrectionMode.NONE,
            shouldSeek = false,
            targetRate = baseRate,
            baseRate = baseRate,
            driftMs = drift
        )
    }

    /**
     * Best-effort YouTube ad detection heuristic:
     * 1. getVideoData().video_id differs from the room's expected videoId during active playback.
     * 2. Known content duration suddenly shrinks to a short ad clip (<= 65s vs >= 120s content).
     * 3. Rapid player state oscillation (>= 4 transitions in 2s) accompanied by mismatched/empty video_id.
     */
    fun detectAdPlayback(signal: AdDetectionSignal): Boolean {
        val reportedId = signal.reportedVideoId?.trim().orEmpty()
        if (reportedId.isNotEmpty() && reportedId != signal.expectedVideoId) {
            return true
        }
        if (
            signal.expectedContentDurationSec >= 120.0 &&
            signal.reportedDurationSec in 1.0..65.0 &&
            abs(signal.expectedContentDurationSec - signal.reportedDurationSec) > 50.0
        ) {
            return true
        }
        if (signal.recentStateTransitionsIn2Sec >= 4 && reportedId.isEmpty()) {
            return true
        }
        return false
    }

    /**
     * Returns true if a local viewer has been buffering long enough (> 3s) to display "Catching up...".
     */
    fun shouldShowCatchingUp(bufferingStartedAtMs: Long?, nowMs: Long): Boolean {
        if (bufferingStartedAtMs == null) return false
        return (nowMs - bufferingStartedAtMs) >= BUFFERING_NOTICE_THRESHOLD_MS
    }

    /**
     * Returns true if a local player event occurred inside the echo-suppression window immediately
     * following an applied remote sync command, preventing infinite re-broadcast loops.
     */
    fun isEchoEvent(
        lastRemoteAppliedAtMs: Long,
        nowMs: Long,
        suppressionWindowMs: Long = ECHO_SUPPRESSION_WINDOW_MS
    ): Boolean {
        if (lastRemoteAppliedAtMs <= 0L) return false
        val elapsed = nowMs - lastRemoteAppliedAtMs
        return elapsed in 0L..suppressionWindowMs
    }

    /**
     * Returns true when a room has had zero active members for longer than [expirationMs].
     */
    fun isRoomExpired(
        memberCount: Int,
        emptySinceMs: Long?,
        nowMs: Long,
        expirationMs: Long = EMPTY_ROOM_EXPIRATION_MS
    ): Boolean {
        if (memberCount > 0 || emptySinceMs == null) return false
        return (nowMs - emptySinceMs) >= expirationMs
    }

    fun parseYouTubeVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        for (pattern in youtubePatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    fun parseYouTubePlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return youtubePlaylistPattern.find(trimmed)?.groupValues?.get(1)
    }

    /**
     * Maps official YouTube IFrame Player API error codes to commercial-safe, friendly room messages.
     */
    fun mapYouTubeErrorCode(code: Int): String = when (code) {
        2 -> "Invalid YouTube video ID. Check the link and try another video."
        5 -> "HTML5 player error on this device. Tap Retry or Open on YouTube."
        100 -> "This YouTube video was removed, deleted, or is private."
        101, 150 -> "This video cannot be embedded (disabled by owner, age-restricted, or region-blocked). Pick another video or open it on YouTube."
        else -> "YouTube playback unavailable (error $code). Try another video or open it on YouTube."
    }

    fun buildYouTubeWatchUrl(videoId: String): String =
        "https://www.youtube.com/watch?v=$videoId"

    fun buildYouTubeReportUrl(): String =
        "https://support.google.com/youtube/answer/2802027"

    fun parseGoogleDriveFileId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        for (pattern in drivePatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    fun buildDrivePreviewUrl(fileId: String): String =
        "https://drive.google.com/file/d/$fileId/preview"

    fun sanitizeChatText(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\p{Cntrl}&&[^\n\t]]"), "")
            .trim()
        return cleaned.take(400)
    }

    fun sanitizeDisplayName(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\p{Cntrl}]"), "")
            .trim()
            .take(40)
        return cleaned.ifBlank { "Cinema Fan" }
    }

    fun formatDurationMs(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun buildInviteDeepLink(roomCode: String): String =
        "watchtogether://room/${normalizeRoomCode(roomCode)}"
}
