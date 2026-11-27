package com.example.ui.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.MediaSourceType
import com.example.data.PlaybackState
import com.example.data.SyncEngine
import com.example.data.WatchRoom
import kotlinx.coroutines.delay

/**
 * Hardware-accelerated Jetpack Media3 ExoPlayer for free legal HLS (.m3u8) Live TV
 * and direct MP4/HLS streams, synced with WatchRoom state, error recovery, and lifecycle handling.
 */
@OptIn(UnstableApi::class)
@Composable
fun HlsExoSyncPlayer(
    room: WatchRoom,
    autoSyncEnabled: Boolean,
    resyncNonce: Int,
    onPositionReported: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPosition by rememberUpdatedState(onPositionReported)

    var isBuffering by remember { mutableStateOf(true) }
    var streamError by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    streamError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                streamError = "Stream temporarily unavailable (${error.errorCodeName}). Tap retry or pick another channel."
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_START -> {
                    if (room.parsedPlaybackState == PlaybackState.PLAYING) {
                        exoPlayer.play()
                    }
                }
                else -> Unit
            }
        }
        exoPlayer.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        }
    }

    fun loadCurrentMedia() {
        val url = room.mediaUrl.trim()
        if (url.isNotEmpty()) {
            streamError = null
            isBuffering = true
            val builder = MediaItem.Builder().setUri(url)
            if (url.contains(".m3u8", ignoreCase = true) || room.parsedSourceType == MediaSourceType.LIVE_TV) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            exoPlayer.setMediaItem(builder.build())
            exoPlayer.prepare()
            exoPlayer.playWhenReady = room.parsedPlaybackState == PlaybackState.PLAYING
        }
    }

    // Load media item when URL changes
    LaunchedEffect(room.mediaUrl, room.sourceType) {
        loadCurrentMedia()
    }

    // Synchronize playback state, speed, and drift
    LaunchedEffect(
        room.playbackState,
        room.positionMs,
        room.playbackRate,
        room.syncUpdatedAt,
        resyncNonce,
        autoSyncEnabled
    ) {
        val isLive = room.parsedSourceType == MediaSourceType.LIVE_TV
        exoPlayer.playWhenReady = room.parsedPlaybackState == PlaybackState.PLAYING
        if (!isLive) {
            val safeRate = room.playbackRate.toFloat().coerceIn(0.25f, 2.0f)
            exoPlayer.playbackParameters = PlaybackParameters(safeRate)

            val syncAtMs = room.syncUpdatedAt?.toDate()?.time ?: System.currentTimeMillis()
            val expectedMs = SyncEngine.computeExpectedPositionMs(
                roomPositionMs = room.positionMs,
                syncUpdatedAtMs = syncAtMs,
                nowMs = System.currentTimeMillis(),
                playbackState = room.parsedPlaybackState,
                playbackRate = room.playbackRate
            )
            val localMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (resyncNonce > 0 || (autoSyncEnabled && SyncEngine.shouldCorrectDrift(localMs, expectedMs))) {
                exoPlayer.seekTo(expectedMs)
            }
        }
    }

    // Report live position to ViewModel every second
    LaunchedEffect(exoPlayer, room.roomCode) {
        while (true) {
            delay(1000L)
            latestOnPosition(exoPlayer.currentPosition.coerceAtLeast(0L))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
            }
        )

        if (isBuffering && streamError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x660A0B10)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        if (streamError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE60A0B10))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = streamError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(onClick = { loadCurrentMedia() }) {
                        Text("Retry Stream")
                    }
                }
            }
        }
    }
}
