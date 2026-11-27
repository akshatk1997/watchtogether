package com.example

import com.example.data.AdDetectionSignal
import com.example.data.AuthoritativeSyncState
import com.example.data.ClockOffsetEstimator
import com.example.data.DriftCorrectionMode
import com.example.data.MediaLookupService
import com.example.data.PasteOnlyProvider
import com.example.data.PlaybackState
import com.example.data.SyncEngine
import com.example.data.YouTubeApiFetchResult
import com.example.data.YouTubeDataApiProvider
import com.example.data.YouTubeSearchItem
import com.example.data.YouTubeSearchOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {

    @Test
    fun clockOffsetEstimator_computesNtpOffsetAndFiltersHighRttSpikes() {
        val estimator = ClockOffsetEstimator(maxSamples = 5)
        // Server is +500ms ahead of client; symmetric 40ms RTT
        val sample1 = estimator.recordPingPong(
            t0ClientSendMs = 1_000L,
            t1ServerReceiveMs = 1_520L,
            t2ServerSendMs = 1_520L,
            t3ClientReceiveMs = 1_040L
        )
        assertEquals(40L, sample1.roundTripMs)
        assertEquals(500L, sample1.offsetMs)

        // Second clean sample (+510ms offset, 30ms RTT)
        estimator.recordPingPong(
            t0ClientSendMs = 31_000L,
            t1ServerReceiveMs = 31_525L,
            t2ServerSendMs = 31_525L,
            t3ClientReceiveMs = 31_030L
        )

        // Third noisy spike sample (900ms RTT, skewed +1200ms offset) should be filtered out
        estimator.recordPingPong(
            t0ClientSendMs = 61_000L,
            t1ServerReceiveMs = 62_650L,
            t2ServerSendMs = 62_650L,
            t3ClientReceiveMs = 61_900L
        )

        val offset = estimator.currentOffsetMs()
        assertTrue("Expected low-RTT offset near 500-510ms, got $offset", offset in 495L..515L)
        assertEquals(10_000L + offset, estimator.estimatedServerNowMs(10_000L))
    }

    @Test
    fun computeScheduledDelayMs_accountsForClockOffset() {
        val delayMs = SyncEngine.computeScheduledDelayMs(
            targetServerTimeMs = 20_500L,
            clientNowMs = 19_800L,
            clockOffsetMs = 500L // serverNow is 20_300ms
        )
        assertEquals(200L, delayMs)
    }

    @Test
    fun driftCorrection_appliesThreeTierThresholdsAndRateNudgeLogic() {
        val authoritative = AuthoritativeSyncState(
            videoId = "aqz-KE-bpKQ",
            playbackState = PlaybackState.PLAYING,
            positionAtTimestampMs = 30_000L,
            serverTimeMs = 100_000L,
            playbackRate = 1.0
        )

        // 1. Under 0.3s drift -> NONE, base rate restored
        val under = SyncEngine.evaluateSyncDecision(
            currentVideoId = "aqz-KE-bpKQ",
            localPositionMs = 30_200L,
            authoritative = authoritative,
            serverNowMs = 100_000L
        )
        assertEquals(DriftCorrectionMode.NONE, under.correctionMode)
        assertFalse(under.shouldSeek)
        assertEquals(1.0, under.targetRate, 0.001)

        // 2. Behind by 0.8s (between 0.3s and 2.0s) -> RATE_NUDGE up to 1.05x
        val behindNudge = SyncEngine.evaluateSyncDecision(
            currentVideoId = "aqz-KE-bpKQ",
            localPositionMs = 29_200L,
            authoritative = authoritative,
            serverNowMs = 100_000L
        )
        assertEquals(DriftCorrectionMode.RATE_NUDGE, behindNudge.correctionMode)
        assertFalse(behindNudge.shouldSeek)
        assertEquals(1.05, behindNudge.targetRate, 0.001)

        // 3. Ahead by 1.2s (between 0.3s and 2.0s) -> RATE_NUDGE down to 0.95x
        val aheadNudge = SyncEngine.evaluateSyncDecision(
            currentVideoId = "aqz-KE-bpKQ",
            localPositionMs = 31_200L,
            authoritative = authoritative,
            serverNowMs = 100_000L
        )
        assertEquals(DriftCorrectionMode.RATE_NUDGE, aheadNudge.correctionMode)
        assertFalse(aheadNudge.shouldSeek)
        assertEquals(0.95, aheadNudge.targetRate, 0.001)

        // 4. Drift > 2.0s -> HARD_SEEK back to expected position and restore base rate
        val hardSeek = SyncEngine.evaluateSyncDecision(
            currentVideoId = "aqz-KE-bpKQ",
            localPositionMs = 26_500L,
            authoritative = authoritative,
            serverNowMs = 100_000L
        )
        assertEquals(DriftCorrectionMode.HARD_SEEK, hardSeek.correctionMode)
        assertTrue(hardSeek.shouldSeek)
        assertEquals(30_000L, hardSeek.expectedPositionMs)
        assertEquals(1.0, hardSeek.targetRate, 0.001)
    }

    @Test
    fun computeExpectedPositionMs_advancesWhenPlayingWithRateAndFreezesWhenPaused() {
        val playing = SyncEngine.computeExpectedPositionMs(
            roomPositionMs = 10_000L,
            syncUpdatedAtMs = 1_000_000L,
            nowMs = 1_004_000L,
            playbackState = PlaybackState.PLAYING,
            playbackRate = 1.5
        )
        assertEquals(16_000L, playing)

        val paused = SyncEngine.computeExpectedPositionMs(
            roomPositionMs = 42_500L,
            syncUpdatedAtMs = 1_000_000L,
            nowMs = 1_030_000L,
            playbackState = PlaybackState.PAUSED,
            playbackRate = 1.0
        )
        assertEquals(42_500L, paused)
    }

    @Test
    fun detectAdPlayback_catchesMismatchedVideoIdAndDurationAnomalies() {
        assertTrue(
            SyncEngine.detectAdPlayback(
                AdDetectionSignal(
                    expectedVideoId = "aqz-KE-bpKQ",
                    reportedVideoId = "AdClip12345",
                    reportedDurationSec = 15.0,
                    expectedContentDurationSec = 600.0,
                    recentStateTransitionsIn2Sec = 1
                )
            )
        )
        assertTrue(
            SyncEngine.detectAdPlayback(
                AdDetectionSignal(
                    expectedVideoId = "aqz-KE-bpKQ",
                    reportedVideoId = "aqz-KE-bpKQ",
                    reportedDurationSec = 30.0,
                    expectedContentDurationSec = 600.0,
                    recentStateTransitionsIn2Sec = 1
                )
            )
        )
        assertFalse(
            SyncEngine.detectAdPlayback(
                AdDetectionSignal(
                    expectedVideoId = "aqz-KE-bpKQ",
                    reportedVideoId = "aqz-KE-bpKQ",
                    reportedDurationSec = 600.0,
                    expectedContentDurationSec = 600.0,
                    recentStateTransitionsIn2Sec = 1
                )
            )
        )
    }

    @Test
    fun parseYouTubeUrlsAndPlaylists_handlesAllOfficialFormats() {
        assertEquals("jfKfPfyJRdk", SyncEngine.parseYouTubeVideoId("https://www.youtube.com/watch?v=jfKfPfyJRdk&t=20"))
        assertEquals("aqz-KE-bpKQ", SyncEngine.parseYouTubeVideoId("https://youtu.be/aqz-KE-bpKQ?si=abc"))
        assertEquals("LXb3EKWsInQ", SyncEngine.parseYouTubeVideoId("https://youtube.com/live/LXb3EKWsInQ"))
        assertEquals("YE7VzlLtp-4", SyncEngine.parseYouTubeVideoId("https://www.youtube.com/shorts/YE7VzlLtp-4"))
        assertEquals("4xDzrJKXOOY", SyncEngine.parseYouTubeVideoId("https://www.youtube-nocookie.com/embed/4xDzrJKXOOY"))
        assertEquals("PL1234567890abcd", SyncEngine.parseYouTubePlaylistId("https://www.youtube.com/playlist?list=PL1234567890abcd"))
        assertNull(SyncEngine.parseYouTubeVideoId("https://example.com/not-youtube"))
    }

    @Test
    fun searchProvider_pasteOnlyAndYouTubeApiCachingAndQuotaFallback() = runTest {
        val lookup = MediaLookupService()
        val pasteOnly = PasteOnlyProvider(lookup)
        assertFalse(pasteOnly.supportsKeywordSearch)
        val pasteResult = pasteOnly.search("lofi")
        assertTrue(pasteResult is YouTubeSearchOutcome.Success)
        assertTrue((pasteResult as YouTubeSearchOutcome.Success).notice?.contains("no API key", ignoreCase = true) == true)

        var fakeClock = 1_000_000L
        var networkCalls = 0
        var simulateQuota = false
        val apiProvider = YouTubeDataApiProvider(
            apiKey = "TEST_OPTIONAL_KEY",
            mediaLookupService = lookup,
            clock = { fakeClock },
            remoteFetcher = { q, _ ->
                networkCalls++
                if (simulateQuota) {
                    YouTubeApiFetchResult.QuotaExceeded
                } else {
                    YouTubeApiFetchResult.Items(
                        listOf(YouTubeSearchItem("aqz-KE-bpKQ", "Result for $q", "Channel", "https://img.youtube.com/vi/aqz-KE-bpKQ/hqdefault.jpg"))
                    )
                }
            }
        )

        // First query hits remote fetcher
        val first = apiProvider.search("blender movie") as YouTubeSearchOutcome.Success
        assertFalse(first.fromCache)
        assertEquals(1, networkCalls)

        // Repeat query 12 hours later served from 24h cache (0 extra network calls)
        fakeClock += 12 * 60 * 60 * 1000L
        val second = apiProvider.search("  BLENDER MOVIE ") as YouTubeSearchOutcome.Success
        assertTrue(second.fromCache)
        assertEquals(1, networkCalls)

        // Quota exceeded falls back gracefully without crashing
        simulateQuota = true
        val quotaFallback = apiProvider.search("new query") as YouTubeSearchOutcome.Success
        assertTrue(quotaFallback.notice?.contains("quota", ignoreCase = true) == true)
    }

    @Test
    fun echoGuardBufferingAndRoomExpiryWorkAccurately() {
        assertTrue(SyncEngine.isEchoEvent(50_000L, 50_200L))
        assertFalse(SyncEngine.isEchoEvent(50_000L, 51_000L))
        assertFalse(SyncEngine.shouldShowCatchingUp(10_000L, 12_500L))
        assertTrue(SyncEngine.shouldShowCatchingUp(10_000L, 13_100L))
        assertTrue(SyncEngine.isRoomExpired(0, 1_000L, 1_000L + SyncEngine.EMPTY_ROOM_EXPIRATION_MS))
        assertTrue(SyncEngine.mapYouTubeErrorCode(150).contains("cannot be embedded", ignoreCase = true))
    }
}
