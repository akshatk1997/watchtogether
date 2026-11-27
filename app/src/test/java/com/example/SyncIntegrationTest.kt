package com.example

import com.example.data.AdDetectionSignal
import com.example.data.AuthoritativeSyncState
import com.example.data.ClockOffsetEstimator
import com.example.data.DriftCorrectionMode
import com.example.data.PlaybackState
import com.example.data.SyncEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Deterministic multi-device integration test suite simulating a Host + 3 Guests
 * with distinct device clock offsets, verifying:
 * 1. Play, pause, seek, speed change, video change across 4 devices staying within <1s.
 * 2. Smooth playbackRate nudge (0.3s–2s drift) and restoration (<0.3s) without stuttering seeks.
 * 3. Late joiner catch-up & disconnect/reconnect recovery without interrupting others.
 * 4. Guest buffering >3s ("Catching up...") without pausing room (and optional waitForEveryone mode).
 * 5. Simulated YouTube ad on one viewer: drift/seeks suspended only for that viewer while the
 *    host and other guests continue in sync uninterrupted, followed by automatic post-ad resync.
 */
class SyncIntegrationTest {

    private class SimulatedSyncServer(
        hostId: String,
        initialVideoId: String,
        startServerTimeMs: Long
    ) {
        var state = AuthoritativeSyncState(
            videoId = initialVideoId,
            playbackState = PlaybackState.PAUSED,
            positionAtTimestampMs = 0L,
            serverTimeMs = startServerTimeMs,
            playbackRate = 1.0,
            controllerId = hostId,
            everyoneCanControl = false,
            hostId = hostId,
            waitForEveryone = false,
            revision = 1L
        )
        val connectedClients = mutableListOf<SimulatedViewer>()
        var broadcastCount: Int = 0
        var pausedForWaitForEveryone: Boolean = false

        fun pingPong(viewer: SimulatedViewer, serverNowMs: Long, oneWayLatencyMs: Long) {
            val clientT0 = serverNowMs - viewer.trueClockSkewMs - oneWayLatencyMs
            val serverT1 = serverNowMs
            val serverT2 = serverNowMs + 2L
            val clientT3 = serverT2 - viewer.trueClockSkewMs + oneWayLatencyMs
            viewer.clockEstimator.recordPingPong(clientT0, serverT1, serverT2, clientT3)
        }

        fun connect(viewer: SimulatedViewer, serverNowMs: Long) {
            if (viewer !in connectedClients) {
                connectedClients.add(viewer)
            }
            pingPong(viewer, serverNowMs, oneWayLatencyMs = 25L)
            viewer.applyAuthoritative(state, serverNowMs, forceSeek = true)
        }

        fun disconnect(viewer: SimulatedViewer) {
            connectedClients.remove(viewer)
        }

        fun applyHostOrControllerAction(
            actorId: String,
            serverNowMs: Long,
            newState: PlaybackState = state.playbackState,
            newPositionMs: Long? = null,
            newVideoId: String = state.videoId,
            newRate: Double = state.playbackRate
        ): Boolean {
            if (actorId != state.hostId && !state.everyoneCanControl) return false
            val effectivePos = newPositionMs ?: SyncEngine.computeExpectedPositionMs(
                roomPositionMs = state.positionAtTimestampMs,
                syncUpdatedAtMs = state.serverTimeMs,
                nowMs = serverNowMs,
                playbackState = state.playbackState,
                playbackRate = state.playbackRate
            )
            state = state.copy(
                videoId = newVideoId,
                playbackState = newState,
                positionAtTimestampMs = effectivePos,
                serverTimeMs = serverNowMs,
                playbackRate = newRate,
                controllerId = actorId,
                revision = state.revision + 1L
            )
            broadcastCount++
            connectedClients.forEach { it.applyAuthoritative(state, serverNowMs) }
            return true
        }

        fun setWaitForEveryone(actorId: String, enabled: Boolean) {
            if (actorId != state.hostId) return
            state = state.copy(waitForEveryone = enabled, revision = state.revision + 1L)
        }

        fun onViewerBufferingChanged(viewer: SimulatedViewer, serverNowMs: Long) {
            if (!state.waitForEveryone) {
                // Default behavior: buffering guest never pauses everyone else
                return
            }
            val anyLongBuffering = connectedClients.any { it.isShowingCatchingUp(serverNowMs) }
            if (anyLongBuffering && state.playbackState == PlaybackState.PLAYING) {
                pausedForWaitForEveryone = true
                applyHostOrControllerAction(state.hostId, serverNowMs, newState = PlaybackState.PAUSED)
            } else if (!anyLongBuffering && pausedForWaitForEveryone) {
                pausedForWaitForEveryone = false
                applyHostOrControllerAction(state.hostId, serverNowMs, newState = PlaybackState.PLAYING)
            }
        }
    }

    private class SimulatedViewer(
        val userId: String,
        val trueClockSkewMs: Long // serverTime - clientClock
    ) {
        val clockEstimator = ClockOffsetEstimator()
        var currentVideoId: String = ""
        var playbackState: PlaybackState = PlaybackState.PAUSED
        var localPositionMs: Long = 0L
        var effectiveRate: Double = 1.0
        var lastRemoteAppliedAtMs: Long = 0L
        var bufferingStartedAtMs: Long? = null
        var isAdPlaying: Boolean = false
        var lastMode: DriftCorrectionMode = DriftCorrectionMode.NONE
        var seekCount: Int = 0
        var rateNudgeCount: Int = 0

        fun clientNowMs(serverNowMs: Long): Long = serverNowMs - trueClockSkewMs

        fun isShowingCatchingUp(serverNowMs: Long): Boolean =
            SyncEngine.shouldShowCatchingUp(bufferingStartedAtMs, serverNowMs)

        fun applyAuthoritative(
            snapshot: AuthoritativeSyncState,
            actualServerNowMs: Long,
            forceSeek: Boolean = false
        ) {
            val estimatedServerNow = clockEstimator.estimatedServerNowMs(clientNowMs(actualServerNowMs))
            val decision = SyncEngine.evaluateSyncDecision(
                currentVideoId = currentVideoId,
                localPositionMs = localPositionMs,
                authoritative = snapshot,
                serverNowMs = estimatedServerNow,
                forceSeek = forceSeek,
                isAdPlaying = isAdPlaying
            )
            lastMode = decision.correctionMode
            if (decision.correctionMode == DriftCorrectionMode.SUSPENDED_FOR_AD) {
                return
            }
            lastRemoteAppliedAtMs = clientNowMs(actualServerNowMs)
            if (decision.loadNewVideo) {
                currentVideoId = decision.targetVideoId
            }
            if (decision.shouldSeek) {
                localPositionMs = decision.expectedPositionMs
                seekCount++
            }
            if (decision.correctionMode == DriftCorrectionMode.RATE_NUDGE) {
                rateNudgeCount++
            }
            playbackState = decision.targetState
            effectiveRate = decision.targetRate
        }

        fun tickPlayback(deltaMs: Long, extraDriftMs: Long = 0L) {
            if (playbackState == PlaybackState.PLAYING && bufferingStartedAtMs == null && !isAdPlaying) {
                localPositionMs = (localPositionMs + (deltaMs * effectiveRate).toLong() + extraDriftMs)
                    .coerceAtLeast(0L)
            }
        }

        fun reportAdSignal(signal: AdDetectionSignal, server: SimulatedSyncServer, serverNowMs: Long) {
            val detected = SyncEngine.detectAdPlayback(signal)
            val wasAd = isAdPlaying
            isAdPlaying = detected
            if (wasAd && !detected) {
                // Ad finished -> automatically resync back to live room position
                applyAuthoritative(server.state, serverNowMs, forceSeek = true)
            }
        }
    }

    @Test
    fun hostAndThreeGuests_stayInSyncThroughPlayPauseSeekSpeedVideoChangeLateJoinReconnectAndBuffering() {
        var serverNowMs = 1_700_000_000_000L
        val server = SimulatedSyncServer("host", "aqz-KE-bpKQ", serverNowMs)

        val host = SimulatedViewer("host", trueClockSkewMs = +180L)
        val guest1 = SimulatedViewer("guest_1", trueClockSkewMs = -240L)
        val guest2 = SimulatedViewer("guest_2", trueClockSkewMs = +95L)
        val guest3 = SimulatedViewer("guest_3", trueClockSkewMs = -150L)

        // 1. Host, Guest 1, Guest 2 join initially
        server.connect(host, serverNowMs)
        server.connect(guest1, serverNowMs)
        server.connect(guest2, serverNowMs)

        // 2. Host starts playback at 0s
        serverNowMs += 1_000L
        assertTrue(server.applyHostOrControllerAction("host", serverNowMs, newState = PlaybackState.PLAYING, newPositionMs = 0L))
        assertEquals(PlaybackState.PLAYING, guest1.playbackState)
        assertEquals(PlaybackState.PLAYING, guest2.playbackState)

        // 3. Advance 10s; Guest 1 falls behind by 600ms -> gentle RATE_NUDGE (1.05x) instead of hard seek!
        serverNowMs += 10_000L
        host.tickPlayback(10_000L)
        guest1.tickPlayback(10_000L, extraDriftMs = -600L)
        guest2.tickPlayback(10_000L)

        val guest1SeeksBeforeNudge = guest1.seekCount
        guest1.applyAuthoritative(server.state, serverNowMs)
        assertEquals(DriftCorrectionMode.RATE_NUDGE, guest1.lastMode)
        assertEquals(1.05, guest1.effectiveRate, 0.001)
        assertEquals(guest1SeeksBeforeNudge, guest1.seekCount)

        // Run 10s at nudged 1.05x -> catches up by +500ms (drift closes to -100ms < 300ms) -> restores 1.0x!
        serverNowMs += 10_000L
        host.tickPlayback(10_000L)
        guest1.tickPlayback(10_000L)
        guest2.tickPlayback(10_000L)
        guest1.applyAuthoritative(server.state, serverNowMs)
        assertEquals(DriftCorrectionMode.NONE, guest1.lastMode)
        assertEquals(1.0, guest1.effectiveRate, 0.001)
        assertTrue(abs(host.localPositionMs - guest1.localPositionMs) < 300L)

        // 4. Guest 3 joins late at t = 20s -> jumps directly to 20s and plays in sync
        server.connect(guest3, serverNowMs)
        assertEquals("aqz-KE-bpKQ", guest3.currentVideoId)
        assertEquals(PlaybackState.PLAYING, guest3.playbackState)
        assertTrue(abs(guest3.localPositionMs - host.localPositionMs) < 100L)

        // 5. Host changes speed to 1.25x -> all 3 guests adopt 1.25x
        assertTrue(server.applyHostOrControllerAction("host", serverNowMs, newRate = 1.25))
        assertEquals(1.25, guest1.effectiveRate, 0.001)
        assertEquals(1.25, guest2.effectiveRate, 0.001)
        assertEquals(1.25, guest3.effectiveRate, 0.001)

        // 6. Advance 8s at 1.25x (10s of video -> 30s) while Guest 2 disconnects & reconnects without interrupting others
        server.disconnect(guest2)
        serverNowMs += 8_000L
        host.tickPlayback(8_000L)
        guest1.tickPlayback(8_000L)
        guest3.tickPlayback(8_000L)
        val hostSeeksBeforeReconnect = host.seekCount
        server.connect(guest2, serverNowMs)
        assertEquals(hostSeeksBeforeReconnect, host.seekCount) // Host uninterrupted
        assertTrue(abs(guest2.localPositionMs - 30_000L) < 100L)

        // 7. Guest 1 buffers for 4 seconds -> sees "Catching up..." while Host, Guest 2, Guest 3 KEEP PLAYING
        guest1.bufferingStartedAtMs = serverNowMs
        serverNowMs += 4_000L
        host.tickPlayback(4_000L)
        guest2.tickPlayback(4_000L)
        guest3.tickPlayback(4_000L)
        assertTrue(guest1.isShowingCatchingUp(serverNowMs))
        server.onViewerBufferingChanged(guest1, serverNowMs)
        assertEquals(PlaybackState.PLAYING, server.state.playbackState)
        assertEquals(PlaybackState.PLAYING, host.playbackState)
        assertEquals(PlaybackState.PLAYING, guest2.playbackState)
        assertEquals(PlaybackState.PLAYING, guest3.playbackState)

        // Guest 1 finishes buffering -> catches back up cleanly
        guest1.bufferingStartedAtMs = null
        guest1.applyAuthoritative(server.state, serverNowMs)
        assertTrue(abs(guest1.localPositionMs - host.localPositionMs) < 100L)

        // 8. Optional host setting "waitForEveryone = true": Guest 1 buffering >3s pauses room, then resumes when ready
        server.setWaitForEveryone("host", enabled = true)
        guest1.bufferingStartedAtMs = serverNowMs
        serverNowMs += 3_500L
        server.onViewerBufferingChanged(guest1, serverNowMs)
        assertEquals(PlaybackState.PAUSED, server.state.playbackState)
        assertEquals(PlaybackState.PAUSED, host.playbackState)

        guest1.bufferingStartedAtMs = null
        server.onViewerBufferingChanged(guest1, serverNowMs)
        assertEquals(PlaybackState.PLAYING, server.state.playbackState)
        assertEquals(PlaybackState.PLAYING, guest3.playbackState)

        // 9. Host seeks to 120s and pauses -> all 3 guests stay within <1s
        assertTrue(server.applyHostOrControllerAction("host", serverNowMs, newState = PlaybackState.PAUSED, newPositionMs = 120_000L))
        listOf(host, guest1, guest2, guest3).forEach { viewer ->
            assertEquals(PlaybackState.PAUSED, viewer.playbackState)
            assertTrue("Expected ${viewer.userId} near 120s", abs(viewer.localPositionMs - 120_000L) < 100L)
        }

        // 10. Host switches video -> all 4 switch to new video at 0s
        serverNowMs += 1_000L
        assertTrue(
            server.applyHostOrControllerAction(
                actorId = "host",
                serverNowMs = serverNowMs,
                newVideoId = "LXb3EKWsInQ",
                newState = PlaybackState.PLAYING,
                newPositionMs = 0L,
                newRate = 1.0
            )
        )
        listOf(host, guest1, guest2, guest3).forEach { viewer ->
            assertEquals("LXb3EKWsInQ", viewer.currentVideoId)
            assertEquals(0L, viewer.localPositionMs)
            assertEquals(PlaybackState.PLAYING, viewer.playbackState)
        }
    }

    @Test
    fun simulatedAdOnOneGuest_suspendsDriftForAdViewerWhileOthersStayInSyncThenResyncs() {
        var serverNowMs = 1_700_000_500_000L
        val server = SimulatedSyncServer("host", "aqz-KE-bpKQ", serverNowMs)

        val host = SimulatedViewer("host", trueClockSkewMs = 0L)
        val guest1 = SimulatedViewer("guest_1", trueClockSkewMs = +110L)
        val guest2 = SimulatedViewer("guest_2", trueClockSkewMs = -140L)
        val guest3 = SimulatedViewer("guest_3", trueClockSkewMs = +60L)

        server.connect(host, serverNowMs)
        server.connect(guest1, serverNowMs)
        server.connect(guest2, serverNowMs)
        server.connect(guest3, serverNowMs)

        // Everyone plays smoothly up to 40s
        assertTrue(server.applyHostOrControllerAction("host", serverNowMs, newState = PlaybackState.PLAYING, newPositionMs = 0L))
        serverNowMs += 40_000L
        listOf(host, guest1, guest2, guest3).forEach { it.tickPlayback(40_000L) }

        // Guest 2 gets a 15-second mid-roll YouTube ad (reported video_id changes to ad creative)
        val guest2SeeksBeforeAd = guest2.seekCount
        val guest2NudgesBeforeAd = guest2.rateNudgeCount
        guest2.reportAdSignal(
            AdDetectionSignal(
                expectedVideoId = "aqz-KE-bpKQ",
                reportedVideoId = "YT_AD_99999",
                reportedDurationSec = 15.0,
                expectedContentDurationSec = 600.0,
                recentStateTransitionsIn2Sec = 2
            ),
            server = server,
            serverNowMs = serverNowMs
        )
        assertTrue(guest2.isAdPlaying)

        // Room continues playing for 15s (40s -> 55s); periodic sync ticks run on all viewers
        repeat(5) {
            serverNowMs += 3_000L
            host.tickPlayback(3_000L)
            guest1.tickPlayback(3_000L)
            guest3.tickPlayback(3_000L)
            listOf(host, guest1, guest2, guest3).forEach { it.applyAuthoritative(server.state, serverNowMs) }
        }

        // Host, Guest 1, Guest 3 stayed playing cleanly at 55s (within <1s of each other)
        assertEquals(PlaybackState.PLAYING, host.playbackState)
        assertEquals(55_000L, host.localPositionMs)
        assertTrue(abs(guest1.localPositionMs - 55_000L) < 1_000L)
        assertTrue(abs(guest3.localPositionMs - 55_000L) < 1_000L)

        // Guest 2 received ZERO seeks and ZERO rate nudges while watching the YouTube ad
        assertEquals(DriftCorrectionMode.SUSPENDED_FOR_AD, guest2.lastMode)
        assertEquals(guest2SeeksBeforeAd, guest2.seekCount)
        assertEquals(guest2NudgesBeforeAd, guest2.rateNudgeCount)

        // Ad finishes on Guest 2 -> normal content resumes and Guest 2 auto-resyncs to 55s immediately
        guest2.reportAdSignal(
            AdDetectionSignal(
                expectedVideoId = "aqz-KE-bpKQ",
                reportedVideoId = "aqz-KE-bpKQ",
                reportedDurationSec = 600.0,
                expectedContentDurationSec = 600.0,
                recentStateTransitionsIn2Sec = 1
            ),
            server = server,
            serverNowMs = serverNowMs
        )
        assertFalse(guest2.isAdPlaying)
        assertTrue(
            "Expected Guest 2 to resync near 55s after ad, got ${guest2.localPositionMs}",
            abs(guest2.localPositionMs - 55_000L) < 1_000L
        )
    }
}
