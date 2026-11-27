package com.example.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AdDetectionSignal
import com.example.data.AuthoritativeSyncState
import com.example.data.DriftCorrectionMode
import com.example.data.PlaybackState
import com.example.data.SyncEngine
import com.example.data.WatchRoom
import kotlinx.coroutines.delay

/**
 * Commercial-safe official YouTube IFrame Player API embedded inside Android WebView.
 *
 * Compliance guarantees:
 * - Official IFrame Player API from https://www.youtube.com/iframe_api with host
 *   https://www.youtube-nocookie.com (privacy-enhanced mode).
 * - Never scrapes, downloads, re-encodes, blocks ads, hides, or restyles the YouTube player.
 * - Background play is strictly disabled: pauses automatically on ON_PAUSE/ON_STOP.
 * - Uses 3-tier clock-offset-aware drift correction (<0.3s ignore, 0.3-2s rate nudge, >2s seek),
 *   echo guard, >3s local buffering detection ("Catching up..."), and best-effort ad detection
 *   that suspends drift/seeks during ads and auto-resyncs when normal content resumes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSyncPlayer(
    room: WatchRoom,
    autoSyncEnabled: Boolean,
    clockOffsetMs: Long,
    resyncNonce: Int,
    canControl: Boolean,
    onPositionReported: (Long) -> Unit,
    onSyncModeChanged: (DriftCorrectionMode) -> Unit,
    onBufferingCatchingUpChanged: (Boolean) -> Unit,
    onAdPlaybackChanged: (Boolean) -> Unit,
    onVideoEnded: () -> Unit,
    onRequestPickAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isPlaylistMedia = remember(room.mediaUrl) { room.mediaUrl.startsWith("playlist:") }
    val playlistId = remember(room.mediaUrl) {
        if (isPlaylistMedia) room.mediaUrl.removePrefix("playlist:") else null
    }
    val videoId = remember(room.mediaUrl) {
        if (isPlaylistMedia) "" else (SyncEngine.parseYouTubeVideoId(room.mediaUrl) ?: "aqz-KE-bpKQ")
    }
    val mediaKey = remember(room.mediaUrl) {
        if (isPlaylistMedia) "playlist:$playlistId" else videoId
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var playerReady by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var loadedMediaKey by remember { mutableStateOf("") }
    var lastKnownPositionMs by remember { mutableLongStateOf(0L) }
    var lastRemoteAppliedAtMs by remember { mutableLongStateOf(0L) }
    var expectedContentDurationSec by remember { mutableDoubleStateOf(0.0) }
    var bufferingStartedAtMs by remember { mutableStateOf<Long?>(null) }
    var adPlaying by remember { mutableStateOf(false) }
    var isForeground by remember { mutableStateOf(true) }
    val stateTransitionTimestamps = remember { ArrayDeque<Long>() }

    val latestOnPosition by rememberUpdatedState(onPositionReported)
    val latestOnSyncMode by rememberUpdatedState(onSyncModeChanged)
    val latestOnBuffering by rememberUpdatedState(onBufferingCatchingUpChanged)
    val latestOnAdChanged by rememberUpdatedState(onAdPlaybackChanged)
    val latestOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val latestClockOffset by rememberUpdatedState(clockOffsetMs)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun syncPlayerToRoom(forceSeek: Boolean = false) {
        val wv = webViewRef ?: return
        if (!playerReady || !isForeground) return
        val clientNowMs = System.currentTimeMillis()
        val serverNowMs = clientNowMs + latestClockOffset
        val syncAtMs = room.syncUpdatedAt?.toDate()?.time ?: serverNowMs

        val authoritative = AuthoritativeSyncState(
            videoId = mediaKey,
            playbackState = room.parsedPlaybackState,
            positionAtTimestampMs = room.positionMs,
            serverTimeMs = syncAtMs,
            playbackRate = room.playbackRate,
            controllerId = room.hostId,
            everyoneCanControl = room.everyoneCanControl,
            hostId = room.hostId,
            waitForEveryone = room.waitForEveryone
        )

        val decision = SyncEngine.evaluateSyncDecision(
            currentVideoId = loadedMediaKey,
            localPositionMs = lastKnownPositionMs,
            authoritative = authoritative,
            serverNowMs = serverNowMs,
            forceSeek = forceSeek,
            isAdPlaying = adPlaying
        )
        latestOnSyncMode(decision.correctionMode)

        // Respect YouTube ads: never seek, pause, or nudge while an ad plays on this viewer
        if (decision.correctionMode == DriftCorrectionMode.SUSPENDED_FOR_AD) {
            return
        }

        val expectedSec = decision.expectedPositionMs / 1000.0
        val shouldPlay = decision.targetState == PlaybackState.PLAYING

        lastRemoteAppliedAtMs = clientNowMs

        if (decision.loadNewVideo) {
            playerError = null
            adPlaying = false
            expectedContentDurationSec = 0.0
            bufferingStartedAtMs = null
            latestOnAdChanged(false)
            latestOnBuffering(false)
            loadedMediaKey = mediaKey
            if (isPlaylistMedia && !playlistId.isNullOrBlank()) {
                wv.evaluateJavascript(
                    "window.wtLoadPlaylist('$playlistId', $shouldPlay, ${decision.baseRate});",
                    null
                )
            } else {
                wv.evaluateJavascript(
                    "window.wtLoadVideo('$videoId', $expectedSec, $shouldPlay, ${decision.baseRate});",
                    null
                )
            }
            return
        }

        val applyRate = if (autoSyncEnabled) decision.targetRate else decision.baseRate
        val needsSeek = forceSeek || (autoSyncEnabled && decision.shouldSeek)

        wv.evaluateJavascript(
            "window.wtSync($expectedSec, $shouldPlay, $applyRate, $needsSeek);",
            null
        )
    }

    // Strictly disable background playback for YouTube content
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isForeground = false
                    webViewRef?.evaluateJavascript("window.wtPauseForBackground();", null)
                }
                Lifecycle.Event.ON_RESUME -> {
                    isForeground = true
                    if (playerReady) {
                        syncPlayerToRoom(forceSeek = true)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(
        playerReady,
        mediaKey,
        room.playbackState,
        room.positionMs,
        room.playbackRate,
        room.syncUpdatedAt,
        resyncNonce,
        isForeground
    ) {
        syncPlayerToRoom(forceSeek = resyncNonce > 0)
    }

    // Periodic telemetry & 3-tier drift check loop (every 1.5s)
    LaunchedEffect(playerReady, room.roomCode, autoSyncEnabled, isForeground) {
        while (playerReady && isForeground) {
            delay(1500L)
            webViewRef?.evaluateJavascript("window.wtPollTelemetry();", null)
            val bufStart = bufferingStartedAtMs
            if (bufStart != null && SyncEngine.shouldShowCatchingUp(bufStart, System.currentTimeMillis())) {
                latestOnBuffering(true)
            }
            if (autoSyncEnabled && !adPlaying && room.parsedPlaybackState == PlaybackState.PLAYING) {
                syncPlayerToRoom(forceSeek = false)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            latestOnBuffering(false)
            latestOnAdChanged(false)
            webViewRef?.apply {
                evaluateJavascript("window.wtPauseForBackground();", null)
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(0xFF0A0B10.toInt())
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onReady() {
                                mainHandler.post {
                                    playerReady = true
                                    playerError = null
                                }
                            }

                            @JavascriptInterface
                            fun onStateChange(ytState: Int, reportedVideoId: String, durationSec: Double) {
                                val now = System.currentTimeMillis()
                                mainHandler.post {
                                    // Record state transition cadence for best-effort ad detection
                                    stateTransitionTimestamps.addLast(now)
                                    while (stateTransitionTimestamps.isNotEmpty() && now - stateTransitionTimestamps.first() > 2000L) {
                                        stateTransitionTimestamps.removeFirst()
                                    }

                                    // Buffering tracking (>3s shows local "Catching up..." without pausing room)
                                    if (ytState == 3) {
                                        if (bufferingStartedAtMs == null) {
                                            bufferingStartedAtMs = now
                                        }
                                    } else {
                                        bufferingStartedAtMs = null
                                        latestOnBuffering(false)
                                    }

                                    // Update baseline content duration when normal video is playing
                                    if (
                                        !isPlaylistMedia &&
                                        reportedVideoId == videoId &&
                                        durationSec > 10.0 &&
                                        expectedContentDurationSec <= 0.0
                                    ) {
                                        expectedContentDurationSec = durationSec
                                    }

                                    // Best-effort ad detection
                                    if (!isPlaylistMedia) {
                                        val adNow = SyncEngine.detectAdPlayback(
                                            AdDetectionSignal(
                                                expectedVideoId = videoId,
                                                reportedVideoId = reportedVideoId,
                                                reportedDurationSec = durationSec,
                                                expectedContentDurationSec = expectedContentDurationSec,
                                                recentStateTransitionsIn2Sec = stateTransitionTimestamps.size
                                            )
                                        )
                                        if (adNow != adPlaying) {
                                            adPlaying = adNow
                                            latestOnAdChanged(adNow)
                                            if (!adNow && isForeground) {
                                                // Ad just finished -> immediately resync viewer to live room time
                                                syncPlayerToRoom(forceSeek = true)
                                            }
                                        }
                                    }

                                    // Video ended (0) -> auto-advance room queue if host
                                    if (ytState == 0 && !adPlaying && !SyncEngine.isEchoEvent(lastRemoteAppliedAtMs, now)) {
                                        latestOnVideoEnded()
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onPlaybackRateChange(newRate: Double) {
                                // Tracked silently; echo guard prevents feedback loops
                            }

                            @JavascriptInterface
                            fun onTelemetry(seconds: Double, reportedVideoId: String, durationSec: Double) {
                                val now = System.currentTimeMillis()
                                val ms = (seconds * 1000.0).toLong().coerceAtLeast(0L)
                                mainHandler.post {
                                    if (!isPlaylistMedia && reportedVideoId == videoId && durationSec > 10.0 && expectedContentDurationSec <= 0.0) {
                                        expectedContentDurationSec = durationSec
                                    }
                                    if (!isPlaylistMedia) {
                                        val adNow = SyncEngine.detectAdPlayback(
                                            AdDetectionSignal(
                                                expectedVideoId = videoId,
                                                reportedVideoId = reportedVideoId,
                                                reportedDurationSec = durationSec,
                                                expectedContentDurationSec = expectedContentDurationSec,
                                                recentStateTransitionsIn2Sec = stateTransitionTimestamps.size
                                            )
                                        )
                                        if (adNow != adPlaying) {
                                            adPlaying = adNow
                                            latestOnAdChanged(adNow)
                                            if (!adNow && isForeground) {
                                                syncPlayerToRoom(forceSeek = true)
                                            }
                                        }
                                    }
                                    if (!adPlaying && !SyncEngine.isEchoEvent(lastRemoteAppliedAtMs, now)) {
                                        lastKnownPositionMs = ms
                                        latestOnPosition(ms)
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onError(code: Int) {
                                val message = SyncEngine.mapYouTubeErrorCode(code)
                                mainHandler.post {
                                    playerError = message
                                    bufferingStartedAtMs = null
                                    latestOnBuffering(false)
                                }
                            }
                        },
                        "WatchTogetherBridge"
                    )

                    val html = buildOfficialYouTubeIFrameHtml(
                        initialVideoId = videoId,
                        initialPlaylistId = playlistId
                    )
                    loadedMediaKey = mediaKey
                    loadDataWithBaseURL(
                        "https://www.youtube-nocookie.com",
                        html,
                        "text/html",
                        "utf-8",
                        null
                    )
                    webViewRef = this
                }
            }
        )

        if (!playerReady && playerError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC0A0B10)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEB0A0B10))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = playerError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (videoId.isNotBlank()) {
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(SyncEngine.buildYouTubeWatchUrl(videoId))
                                    )
                                    runCatching { context.startActivity(intent) }
                                }
                            ) {
                                Text("Open on YouTube")
                            }
                        }
                        if (canControl) {
                            FilledTonalButton(onClick = onRequestPickAnother) {
                                Text("Pick Another")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                playerError = null
                                playerReady = false
                                webViewRef?.loadDataWithBaseURL(
                                    "https://www.youtube-nocookie.com",
                                    buildOfficialYouTubeIFrameHtml(videoId, playlistId),
                                    "text/html",
                                    "utf-8",
                                    null
                                )
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

private fun buildOfficialYouTubeIFrameHtml(
    initialVideoId: String,
    initialPlaylistId: String?
): String {
    val playlistSetup = if (!initialPlaylistId.isNullOrBlank()) {
        "'listType': 'playlist', 'list': '$initialPlaylistId',"
    } else ""

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #0A0B10; overflow: hidden; }
            #player { width: 100%; height: 100%; border: 0; }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var player = null;
            var isReady = false;
            var suppressEchoUntil = 0;

            function markRemoteCommand() {
              suppressEchoUntil = Date.now() + 850;
            }

            function getSafeVideoId() {
              try {
                var data = player && player.getVideoData ? player.getVideoData() : null;
                return data && data.video_id ? String(data.video_id) : '';
              } catch (e) {
                return '';
              }
            }

            function getSafeDuration() {
              try {
                var d = player && player.getDuration ? Number(player.getDuration()) : 0;
                return isNaN(d) ? 0 : d;
              } catch (e) {
                return 0;
              }
            }

            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                host: 'https://www.youtube-nocookie.com',
                videoId: '$initialVideoId',
                playerVars: {
                  $playlistSetup
                  'playsinline': 1,
                  'rel': 0,
                  'controls': 1,
                  'enablejsapi': 1,
                  'origin': 'https://www.youtube-nocookie.com'
                },
                events: {
                  'onReady': function() {
                    isReady = true;
                    if (window.WatchTogetherBridge) {
                      window.WatchTogetherBridge.onReady();
                    }
                  },
                  'onStateChange': function(e) {
                    if (window.WatchTogetherBridge && e && typeof e.data === 'number') {
                      window.WatchTogetherBridge.onStateChange(e.data, getSafeVideoId(), getSafeDuration());
                    }
                  },
                  'onPlaybackRateChange': function(e) {
                    if (window.WatchTogetherBridge && e && typeof e.data === 'number') {
                      window.WatchTogetherBridge.onPlaybackRateChange(e.data);
                    }
                  },
                  'onError': function(e) {
                    if (window.WatchTogetherBridge) {
                      window.WatchTogetherBridge.onError(e && typeof e.data === 'number' ? e.data : -1);
                    }
                  }
                }
              });
            }

            window.wtLoadVideo = function(videoId, startSeconds, shouldPlay, rate) {
              if (!isReady || !player) return;
              markRemoteCommand();
              if (shouldPlay) {
                player.loadVideoById({ videoId: videoId, startSeconds: startSeconds });
              } else {
                player.cueVideoById({ videoId: videoId, startSeconds: startSeconds });
              }
              try { player.setPlaybackRate(rate); } catch (e) {}
            };

            window.wtLoadPlaylist = function(playlistId, shouldPlay, rate) {
              if (!isReady || !player) return;
              markRemoteCommand();
              if (shouldPlay) {
                player.loadPlaylist({ listType: 'playlist', list: playlistId });
              } else {
                player.cuePlaylist({ listType: 'playlist', list: playlistId });
              }
              try { player.setPlaybackRate(rate); } catch (e) {}
            };

            window.wtSync = function(targetSec, shouldPlay, rate, forceSeek) {
              if (!isReady || !player) return;
              markRemoteCommand();
              try {
                if (player.getPlaybackRate && Math.abs((player.getPlaybackRate() || 1.0) - rate) > 0.01) {
                  player.setPlaybackRate(rate);
                }
                if (forceSeek) {
                  player.seekTo(targetSec, true);
                }
                var st = player.getPlayerState ? player.getPlayerState() : -1;
                if (shouldPlay && st !== YT.PlayerState.PLAYING && st !== YT.PlayerState.BUFFERING) {
                  player.playVideo();
                } else if (!shouldPlay && st === YT.PlayerState.PLAYING) {
                  player.pauseVideo();
                }
              } catch (e) {}
            };

            window.wtPauseForBackground = function() {
              if (!isReady || !player) return;
              markRemoteCommand();
              try { player.pauseVideo(); } catch (e) {}
            };

            window.wtPollTelemetry = function() {
              if (!isReady || !player || !player.getCurrentTime) return;
              if (Date.now() < suppressEchoUntil) return;
              try {
                var sec = Number(player.getCurrentTime());
                if (window.WatchTogetherBridge && !isNaN(sec)) {
                  window.WatchTogetherBridge.onTelemetry(sec, getSafeVideoId(), getSafeDuration());
                }
              } catch (e) {}
            };
          </script>
        </body>
        </html>
    """.trimIndent()
}
