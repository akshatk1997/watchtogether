package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CuratedContentCatalog
import com.example.data.DriftCorrectionMode
import com.example.data.FloatingReactionEvent
import com.example.data.LiveChannel
import com.example.data.MediaSourceType
import com.example.data.MessageType
import com.example.data.PlaybackState
import com.example.data.RoomMessage
import com.example.data.RoomParticipant
import com.example.data.RoomStatus
import com.example.data.SyncEngine
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.WatchRoom
import com.example.ui.components.CinematicBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.RoomCodePill
import com.example.ui.components.SkeletonCard
import com.example.ui.components.UserAvatarBadge
import com.example.ui.player.DriveEmbedPlayer
import com.example.ui.player.FloatingReactionsOverlay
import com.example.ui.player.HlsExoSyncPlayer
import com.example.ui.player.YouTubeSyncPlayer
import com.example.ui.theme.LocalWatchTogetherExtras
import com.example.viewmodel.RoomViewModel
import kotlin.math.abs

@Composable
fun RoomScreen(
    viewModel: RoomViewModel,
    profile: UserProfile,
    liveChannels: List<LiveChannel>,
    isInPipMode: Boolean,
    onEnterPip: () -> Unit,
    onExitRoom: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    val roomState by viewModel.roomState.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val queueItems by viewModel.queueItems.collectAsStateWithLifecycle()
    val floatingReactions by viewModel.floatingReactions.collectAsStateWithLifecycle()
    val localPositionMs by viewModel.localPositionMs.collectAsStateWithLifecycle()
    val driftMs by viewModel.driftMs.collectAsStateWithLifecycle()
    val clockOffsetMs by viewModel.clockOffsetMs.collectAsStateWithLifecycle()
    val activeSyncMode by viewModel.activeSyncMode.collectAsStateWithLifecycle()
    val isCatchingUp by viewModel.isCatchingUp.collectAsStateWithLifecycle()
    val isAdPlaying by viewModel.isAdPlaying.collectAsStateWithLifecycle()
    val voiceJoined by viewModel.voiceJoined.collectAsStateWithLifecycle()
    val micMuted by viewModel.micMuted.collectAsStateWithLifecycle()
    val micLevel by viewModel.micLevel.collectAsStateWithLifecycle()
    val resolvingMedia by viewModel.resolvingMedia.collectAsStateWithLifecycle()
    val youtubeSearching by viewModel.youtubeSearching.collectAsStateWithLifecycle()
    val youtubeSearchResults by viewModel.youtubeSearchResults.collectAsStateWithLifecycle()
    val youtubeSearchNotice by viewModel.youtubeSearchNotice.collectAsStateWithLifecycle()
    val youtubeSearchFromCache by viewModel.youtubeSearchFromCache.collectAsStateWithLifecycle()
    val actionMessage by viewModel.roomActionMessage.collectAsStateWithLifecycle()

    var showContentPicker by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showHostControls by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var resyncNonce by remember { mutableIntStateOf(0) }
    var speedMenuExpanded by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (!voiceJoined) viewModel.joinVoiceChannel(hasAudioPermission = true)
            else viewModel.toggleMicMute(hasAudioPermission = true)
        } else {
            if (!voiceJoined) viewModel.joinVoiceChannel(hasAudioPermission = false)
        }
    }

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    CinematicBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            when (val state = roomState) {
                is UiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .statusBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Syncing room ${viewModel.roomCode}…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SkeletonCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            cornerRadius = 18.dp
                        )
                        SkeletonCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            cornerRadius = 14.dp
                        )
                        SkeletonCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            cornerRadius = 18.dp
                        )
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                TextButton(onClick = onExitRoom) {
                                    Text("Back to Rooms")
                                }
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    val room = state.data
                    val isKicked = viewModel.currentUserId in room.kickedUids
                    val isEnded = room.parsedStatus == RoomStatus.ENDED

                    if (isKicked || isEnded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (isKicked) "Removed from Watch Party" else "Watch Party Ended",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isKicked) "The host removed you from room ${room.roomCode}."
                                        else "The host ended this watch party session.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    TextButton(onClick = onExitRoom) {
                                        Text("Return Home")
                                    }
                                }
                            }
                        }
                        return@Scaffold
                    }

                    // Picture-in-Picture (non-YouTube only) or Fullscreen pure video layout
                    val effectivePip = isInPipMode && room.parsedSourceType != MediaSourceType.YOUTUBE
                    val canControl = room.canUserControl(viewModel.currentUserId)

                    if (effectivePip || isFullscreen) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                            PlayerSurfaceWithReactions(
                                room = room,
                                autoSyncEnabled = profile.autoSyncEnabled,
                                clockOffsetMs = clockOffsetMs,
                                resyncNonce = resyncNonce,
                                canControl = canControl,
                                isFullscreen = isFullscreen,
                                reactions = floatingReactions,
                                onPositionReported = { pos -> viewModel.reportLocalPlaybackPosition(pos, room) },
                                onSyncModeChanged = { mode -> viewModel.reportSyncMode(mode) },
                                onBufferingCatchingUpChanged = { catching -> viewModel.reportBufferingCatchingUp(catching) },
                                onAdPlaybackChanged = { ad -> viewModel.reportAdPlayback(ad) },
                                onVideoEnded = { viewModel.playNextInQueue(room) },
                                onRequestPickAnother = {
                                    isFullscreen = false
                                    showContentPicker = true
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (!effectivePip) {
                                IconButton(
                                    onClick = { isFullscreen = false },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .statusBarsPadding()
                                        .padding(12.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FullscreenExit,
                                        contentDescription = "Exit fullscreen",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                        return@Scaffold
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        // Top Room Bar
                        RoomTopBar(
                            room = room,
                            participantCount = participants.size.coerceAtLeast(room.memberUids.size),
                            queueCount = queueItems.size,
                            canControl = canControl,
                            allowPip = room.parsedSourceType != MediaSourceType.YOUTUBE,
                            onBack = {
                                viewModel.leaveOrEndRoom(room, endForEveryone = false, onDone = onExitRoom)
                            },
                            onShareInvite = {
                                val inviteText =
                                    "Join my WatchTogether party \"${room.title}\"!\nRoom Code: ${room.roomCode}\nLink: ${SyncEngine.buildInviteDeepLink(room.roomCode)}"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, inviteText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Watch Party Invite"))
                            },
                            onOpenPicker = { showContentPicker = true },
                            onOpenQueue = { showQueueSheet = true },
                            onOpenParticipants = { showHostControls = true },
                            onEnterPip = onEnterPip
                        )

                        // Adaptive Body: Side-by-side on Tablets/Landscape, Stacked on Compact Phones
                        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            val isWide = maxWidth >= 700.dp

                            if (isWide) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1.45f)
                                            .fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .widthIn(min = 480.dp)
                                                .heightIn(min = 270.dp)
                                                .weight(1f)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(Color.Black)
                                        ) {
                                            PlayerSurfaceWithReactions(
                                                room = room,
                                                autoSyncEnabled = profile.autoSyncEnabled,
                                                clockOffsetMs = clockOffsetMs,
                                                resyncNonce = resyncNonce,
                                                canControl = canControl,
                                                isFullscreen = false,
                                                reactions = floatingReactions,
                                                onPositionReported = { pos ->
                                                    viewModel.reportLocalPlaybackPosition(pos, room)
                                                },
                                                onSyncModeChanged = { mode -> viewModel.reportSyncMode(mode) },
                                                onBufferingCatchingUpChanged = { catching -> viewModel.reportBufferingCatchingUp(catching) },
                                                onAdPlaybackChanged = { ad -> viewModel.reportAdPlayback(ad) },
                                                onVideoEnded = { viewModel.playNextInQueue(room) },
                                                onRequestPickAnother = { showContentPicker = true }
                                            )
                                        }
                                        YouTubeComplianceAndStatusStrip(
                                            room = room,
                                            isCatchingUp = isCatchingUp,
                                            isAdPlaying = isAdPlaying,
                                            activeSyncMode = activeSyncMode,
                                            recentReactions = floatingReactions,
                                            onOpenQueue = { showQueueSheet = true }
                                        )
                                        PlaybackSyncBar(
                                            room = room,
                                            localPositionMs = localPositionMs,
                                            driftMs = driftMs,
                                            canControl = canControl,
                                            speedMenuExpanded = speedMenuExpanded,
                                            onSpeedMenuToggle = { speedMenuExpanded = it },
                                            onTogglePlayPause = { viewModel.togglePlayPause(room) },
                                            onSeekDelta = { delta -> viewModel.seekByDelta(room, delta) },
                                            onChangeRate = { rate -> viewModel.changePlaybackRate(room, rate) },
                                            onResync = { resyncNonce++ },
                                            onFullscreen = { isFullscreen = true }
                                        )
                                        ReactionAndVoiceBar(
                                            voiceJoined = voiceJoined,
                                            micMuted = micMuted,
                                            micLevel = micLevel,
                                            liveKitConfigured = profile.liveKitUrl.isNotBlank(),
                                            onReaction = { emoji ->
                                                if (profile.hapticsEnabled) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                viewModel.sendReaction(
                                                    room = room,
                                                    senderName = profile.displayName,
                                                    senderColorHex = profile.avatarColorHex,
                                                    emoji = emoji
                                                )
                                            },
                                            onJoinOrLeaveVoice = {
                                                if (voiceJoined) {
                                                    viewModel.leaveVoiceChannel()
                                                } else if (hasAudioPermission()) {
                                                    viewModel.joinVoiceChannel(true)
                                                } else {
                                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            },
                                            onToggleMic = {
                                                if (hasAudioPermission()) {
                                                    viewModel.toggleMicMute(true)
                                                } else {
                                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                        )
                                    }

                                    RoomChatPanel(
                                        room = room,
                                        messages = messages,
                                        participants = participants,
                                        currentUserId = viewModel.currentUserId,
                                        onTypingChanged = { text ->
                                            viewModel.onChatInputChanged(room.roomCode, text)
                                        },
                                        onSendMessage = { text ->
                                            viewModel.sendChatMessage(
                                                room = room,
                                                senderName = profile.displayName,
                                                senderColorHex = profile.avatarColorHex,
                                                rawText = text
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Color.Black)
                                    ) {
                                        PlayerSurfaceWithReactions(
                                            room = room,
                                            autoSyncEnabled = profile.autoSyncEnabled,
                                            clockOffsetMs = clockOffsetMs,
                                            resyncNonce = resyncNonce,
                                            canControl = canControl,
                                            isFullscreen = false,
                                            reactions = floatingReactions,
                                            onPositionReported = { pos ->
                                                viewModel.reportLocalPlaybackPosition(pos, room)
                                            },
                                            onSyncModeChanged = { mode -> viewModel.reportSyncMode(mode) },
                                            onBufferingCatchingUpChanged = { catching -> viewModel.reportBufferingCatchingUp(catching) },
                                            onAdPlaybackChanged = { ad -> viewModel.reportAdPlayback(ad) },
                                            onVideoEnded = { viewModel.playNextInQueue(room) },
                                            onRequestPickAnother = { showContentPicker = true }
                                        )
                                    }

                                    YouTubeComplianceAndStatusStrip(
                                        room = room,
                                        isCatchingUp = isCatchingUp,
                                        isAdPlaying = isAdPlaying,
                                        activeSyncMode = activeSyncMode,
                                        recentReactions = floatingReactions,
                                        onOpenQueue = { showQueueSheet = true }
                                    )

                                    PlaybackSyncBar(
                                        room = room,
                                        localPositionMs = localPositionMs,
                                        driftMs = driftMs,
                                        canControl = canControl,
                                        speedMenuExpanded = speedMenuExpanded,
                                        onSpeedMenuToggle = { speedMenuExpanded = it },
                                        onTogglePlayPause = { viewModel.togglePlayPause(room) },
                                        onSeekDelta = { delta -> viewModel.seekByDelta(room, delta) },
                                        onChangeRate = { rate -> viewModel.changePlaybackRate(room, rate) },
                                        onResync = { resyncNonce++ },
                                        onFullscreen = { isFullscreen = true }
                                    )

                                    ReactionAndVoiceBar(
                                        voiceJoined = voiceJoined,
                                        micMuted = micMuted,
                                        micLevel = micLevel,
                                        liveKitConfigured = profile.liveKitUrl.isNotBlank(),
                                        onReaction = { emoji ->
                                            if (profile.hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            viewModel.sendReaction(
                                                room = room,
                                                senderName = profile.displayName,
                                                senderColorHex = profile.avatarColorHex,
                                                emoji = emoji
                                            )
                                        },
                                        onJoinOrLeaveVoice = {
                                            if (voiceJoined) {
                                                viewModel.leaveVoiceChannel()
                                            } else if (hasAudioPermission()) {
                                                viewModel.joinVoiceChannel(true)
                                            } else {
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        onToggleMic = {
                                            if (hasAudioPermission()) {
                                                viewModel.toggleMicMute(true)
                                            } else {
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    )

                                    RoomChatPanel(
                                        room = room,
                                        messages = messages,
                                        participants = participants,
                                        currentUserId = viewModel.currentUserId,
                                        onTypingChanged = { text ->
                                            viewModel.onChatInputChanged(room.roomCode, text)
                                        },
                                        onSendMessage = { text ->
                                            viewModel.sendChatMessage(
                                                room = room,
                                                senderName = profile.displayName,
                                                senderColorHex = profile.avatarColorHex,
                                                rawText = text
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    if (showContentPicker) {
                        RoomContentPickerSheet(
                            liveChannels = liveChannels,
                            isResolvingMedia = resolvingMedia,
                            youtubeSearching = youtubeSearching,
                            youtubeSearchResults = youtubeSearchResults,
                            youtubeSearchNotice = youtubeSearchNotice,
                            youtubeSearchFromCache = youtubeSearchFromCache,
                            onSearchYouTube = { q -> viewModel.searchYouTube(q) },
                            onSelectYouTubeItem = { item -> viewModel.selectYouTubeSearchItem(room, item) },
                            onSelectYouTube = { input -> viewModel.selectYouTubeLinkOrId(room, input) },
                            onSelectDrive = { url, title -> viewModel.selectGoogleDriveLink(room, url, title) },
                            onSelectDirectStream = { url, title ->
                                viewModel.selectDirectStreamUrl(room, url, title)
                            },
                            onSelectLiveChannel = { ch -> viewModel.selectLiveChannel(room, ch) },
                            onDismiss = { showContentPicker = false }
                        )
                    }

                    if (showQueueSheet) {
                        RoomQueueSheet(
                            room = room,
                            queueItems = queueItems,
                            currentUserId = viewModel.currentUserId,
                            isResolving = resolvingMedia,
                            onSuggestVideo = { rawInput ->
                                viewModel.suggestToQueue(room, rawInput, profile.displayName)
                            },
                            onApprove = { item -> viewModel.approveQueueItem(room, item) },
                            onMoveItem = { item, up -> viewModel.moveQueueItem(room, item, up) },
                            onPlayNow = { item -> viewModel.playQueueItemNow(room, item) },
                            onRemove = { item -> viewModel.removeQueueItem(room, item) },
                            onDismiss = { showQueueSheet = false }
                        )
                    }

                    if (showHostControls) {
                        RoomHostControlsSheet(
                            room = room,
                            participants = participants,
                            currentUserId = viewModel.currentUserId,
                            onUpdateSettings = { everyone, locked, waitForAll ->
                                viewModel.updateHostSettings(room, everyone, locked, waitForAll)
                            },
                            onToggleMuteGuest = { target -> viewModel.toggleMuteGuest(room, target) },
                            onKickGuest = { target -> viewModel.kickGuest(room, target) },
                            onEndRoom = {
                                viewModel.leaveOrEndRoom(room, endForEveryone = true, onDone = onExitRoom)
                            },
                            onDismiss = { showHostControls = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSurfaceWithReactions(
    room: WatchRoom,
    autoSyncEnabled: Boolean,
    clockOffsetMs: Long,
    resyncNonce: Int,
    canControl: Boolean,
    isFullscreen: Boolean,
    reactions: List<FloatingReactionEvent>,
    onPositionReported: (Long) -> Unit,
    onSyncModeChanged: (DriftCorrectionMode) -> Unit,
    onBufferingCatchingUpChanged: (Boolean) -> Unit,
    onAdPlaybackChanged: (Boolean) -> Unit,
    onVideoEnded: () -> Unit,
    onRequestPickAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (room.parsedSourceType) {
            MediaSourceType.YOUTUBE -> YouTubeSyncPlayer(
                room = room,
                autoSyncEnabled = autoSyncEnabled,
                clockOffsetMs = clockOffsetMs,
                resyncNonce = resyncNonce,
                canControl = canControl,
                onPositionReported = onPositionReported,
                onSyncModeChanged = onSyncModeChanged,
                onBufferingCatchingUpChanged = onBufferingCatchingUpChanged,
                onAdPlaybackChanged = onAdPlaybackChanged,
                onVideoEnded = onVideoEnded,
                onRequestPickAnother = onRequestPickAnother
            )
            MediaSourceType.LIVE_TV, MediaSourceType.DIRECT_VIDEO -> HlsExoSyncPlayer(
                room = room,
                autoSyncEnabled = autoSyncEnabled,
                resyncNonce = resyncNonce,
                onPositionReported = onPositionReported
            )
            MediaSourceType.DRIVE -> DriveEmbedPlayer(
                previewUrl = room.mediaUrl
            )
        }
        // Keep YouTube player strictly unobstructed outside fullscreen per YouTube IFrame policy
        if (room.parsedSourceType != MediaSourceType.YOUTUBE || isFullscreen) {
            FloatingReactionsOverlay(reactions = reactions)
        }
    }
}

@Composable
private fun YouTubeComplianceAndStatusStrip(
    room: WatchRoom,
    isCatchingUp: Boolean,
    isAdPlaying: Boolean,
    activeSyncMode: DriftCorrectionMode,
    recentReactions: List<FloatingReactionEvent>,
    onOpenQueue: () -> Unit
) {
    val context = LocalContext.current
    val extras = LocalWatchTogetherExtras.current
    val isYouTube = room.parsedSourceType == MediaSourceType.YOUTUBE
    val videoId = remember(room.mediaUrl) { SyncEngine.parseYouTubeVideoId(room.mediaUrl) }

    if (!isYouTube && !isCatchingUp && !isAdPlaying) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (isYouTube) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "YouTube",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (isAdPlaying) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = extras.degradedAmber.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Ad • Resyncs after",
                        style = MaterialTheme.typography.labelSmall,
                        color = extras.degradedAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (isCatchingUp) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = extras.syncCyan.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Catching up…",
                        style = MaterialTheme.typography.labelSmall,
                        color = extras.syncCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (activeSyncMode == DriftCorrectionMode.RATE_NUDGE) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = extras.syncCyan.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "Smooth sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = extras.syncCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (isYouTube && recentReactions.isNotEmpty()) {
                Text(
                    text = recentReactions.takeLast(4).joinToString(" ") { it.emoji },
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (isYouTube && !videoId.isNullOrBlank()) {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(SyncEngine.buildYouTubeWatchUrl(videoId)))
                            )
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open on YouTube", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("YouTube", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(SyncEngine.buildYouTubeReportUrl()))
                            )
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.testTag("report_youtube_video_button")
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = "Report video", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Report", style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(
                onClick = onOpenQueue,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.testTag("open_queue_strip_button")
            ) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Queue", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RoomTopBar(
    room: WatchRoom,
    participantCount: Int,
    queueCount: Int,
    canControl: Boolean,
    allowPip: Boolean,
    onBack: () -> Unit,
    onShareInvite: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenParticipants: () -> Unit,
    onEnterPip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("room_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Leave room"
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = room.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                val ctx = LocalContext.current
                RoomCodePill(
                    roomCode = room.roomCode,
                    modifier = Modifier
                        .clickable {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(room.roomCode))
                            android.widget.Toast.makeText(
                                ctx,
                                "Room code ${room.roomCode} copied!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        .testTag("copy_room_code_pill")
                )
            }
            Text(
                text = room.mediaTitle.ifBlank { room.parsedSourceType.label },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (canControl) {
            IconButton(
                onClick = onOpenPicker,
                modifier = Modifier.testTag("open_content_picker_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.LibraryAdd,
                    contentDescription = stringResource(R.string.pick_content),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(onClick = onOpenQueue, modifier = Modifier.testTag("room_queue_button")) {
            Icon(
                imageVector = Icons.Filled.QueueMusic,
                contentDescription = "Room queue ($queueCount)"
            )
        }
        IconButton(onClick = onShareInvite, modifier = Modifier.testTag("share_invite_button")) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_invite))
        }
        if (allowPip) {
            IconButton(onClick = onEnterPip, modifier = Modifier.testTag("pip_button")) {
                Icon(Icons.Filled.PictureInPictureAlt, contentDescription = stringResource(R.string.enter_pip))
            }
        }
        Surface(
            onClick = onOpenParticipants,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.testTag("participants_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = "Participants",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$participantCount",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun PlaybackSyncBar(
    room: WatchRoom,
    localPositionMs: Long,
    driftMs: Long,
    canControl: Boolean,
    speedMenuExpanded: Boolean,
    onSpeedMenuToggle: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekDelta: (Long) -> Unit,
    onChangeRate: (Double) -> Unit,
    onResync: () -> Unit,
    onFullscreen: () -> Unit
) {
    val extras = LocalWatchTogetherExtras.current
    val absDrift = abs(driftMs)
    val syncLabel = when {
        room.parsedSourceType == MediaSourceType.LIVE_TV -> "LIVE"
        absDrift <= SyncEngine.DEFAULT_DRIFT_THRESHOLD_MS -> "SYNCED"
        else -> "DRIFT ${String.format("%.1fs", absDrift / 1000.0)}"
    }
    val syncColor = if (absDrift <= SyncEngine.DEFAULT_DRIFT_THRESHOLD_MS) extras.syncCyan else extras.degradedAmber

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onSeekDelta(-10_000L) },
                    enabled = canControl && room.parsedSourceType != MediaSourceType.LIVE_TV
                ) {
                    Icon(Icons.Filled.FastRewind, contentDescription = "Back 10 seconds")
                }
                FilledTonalIconButton(
                    onClick = onTogglePlayPause,
                    enabled = canControl,
                    modifier = Modifier.testTag("play_pause_sync_button")
                ) {
                    Icon(
                        imageVector = if (room.parsedPlaybackState == PlaybackState.PLAYING) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = "Play or pause"
                    )
                }
                IconButton(
                    onClick = { onSeekDelta(10_000L) },
                    enabled = canControl && room.parsedSourceType != MediaSourceType.LIVE_TV
                ) {
                    Icon(Icons.Filled.FastForward, contentDescription = "Forward 10 seconds")
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = syncLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = syncColor
                )
                if (room.parsedSourceType != MediaSourceType.LIVE_TV) {
                    Text(
                        text = SyncEngine.formatDurationMs(localPositionMs.coerceAtLeast(room.positionMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (room.parsedSourceType != MediaSourceType.LIVE_TV) {
                    Box {
                        TextButton(
                            onClick = { onSpeedMenuToggle(true) },
                            enabled = canControl,
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                text = "${room.playbackRate}x",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        DropdownMenu(
                            expanded = speedMenuExpanded,
                            onDismissRequest = { onSpeedMenuToggle(false) }
                        ) {
                            CuratedContentCatalog.playbackSpeeds.forEach { spd ->
                                DropdownMenuItem(
                                    text = { Text("${spd}x") },
                                    onClick = {
                                        onChangeRate(spd)
                                        onSpeedMenuToggle(false)
                                    }
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onResync, modifier = Modifier.testTag("resync_button")) {
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = stringResource(R.string.resync_now),
                        tint = extras.syncCyan
                    )
                }
                IconButton(onClick = onFullscreen, modifier = Modifier.testTag("fullscreen_button")) {
                    Icon(
                        imageVector = Icons.Filled.Fullscreen,
                        contentDescription = stringResource(R.string.toggle_fullscreen)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionAndVoiceBar(
    voiceJoined: Boolean,
    micMuted: Boolean,
    micLevel: Float,
    liveKitConfigured: Boolean,
    onReaction: (String) -> Unit,
    onJoinOrLeaveVoice: () -> Unit,
    onToggleMic: () -> Unit
) {
    val extras = LocalWatchTogetherExtras.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Voice Controls Pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (voiceJoined) extras.syncCyan.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                1.dp,
                if (voiceJoined) extras.syncCyan.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = onJoinOrLeaveVoice,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("voice_join_toggle_button")
                ) {
                    Icon(
                        imageVector = if (voiceJoined) Icons.Filled.CallEnd else Icons.Filled.Headphones,
                        contentDescription = if (voiceJoined) "Leave voice" else "Join voice",
                        tint = if (voiceJoined) MaterialTheme.colorScheme.error else extras.syncCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (voiceJoined) {
                    IconButton(
                        onClick = onToggleMic,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("voice_mic_mute_button")
                    ) {
                        Icon(
                            imageVector = if (micMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (micMuted) "Unmute microphone" else "Mute microphone",
                            tint = if (micMuted) MaterialTheme.colorScheme.onSurfaceVariant else extras.syncCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Real microphone audio meter bars
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        val bars = listOf(0.2f, 0.45f, 0.7f)
                        bars.forEach { threshold ->
                            val active = !micMuted && micLevel >= threshold * 0.6f
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(if (active) 16.dp else 6.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (active) extras.syncCyan
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    )
                            )
                        }
                    }
                } else {
                    Text(
                        text = if (liveKitConfigured) "Voice (LK)" else "Voice",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }

        // Quick Floating Reactions Strip
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(CuratedContentCatalog.quickReactions) { emoji ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { onReaction(emoji) }
                        .testTag("reaction_btn_$emoji")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = emoji, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomChatPanel(
    room: WatchRoom,
    messages: List<RoomMessage>,
    participants: List<RoomParticipant>,
    currentUserId: String,
    onTypingChanged: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val visibleMessages = remember(messages) {
        messages.filter { it.parsedMessageType != MessageType.REACTION }
    }
    val typingNames = remember(participants, currentUserId) {
        participants.filter { it.typing && it.userId != currentUserId }.map { it.displayName }
    }
    val isMuted = currentUserId in room.mutedUids

    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    GlassCard(
        modifier = modifier.padding(bottom = 8.dp),
        cornerRadius = 20.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Party Chat",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (typingNames.isNotEmpty()) {
                    Text(
                        text = "${typingNames.take(2).joinToString(", ")} typing…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (visibleMessages.isEmpty()) {
                    item {
                        Text(
                            text = "Share the room code ${room.roomCode} with friends and say hello!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(visibleMessages, key = { it.id.ifBlank { "${it.senderId}_${it.text.hashCode()}" } }) { msg ->
                        if (msg.parsedMessageType == MessageType.SYSTEM) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                UserAvatarBadge(
                                    displayName = msg.senderName,
                                    colorHex = msg.senderColorHex,
                                    size = 30.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = msg.senderName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isMuted) {
                Text(
                    text = stringResource(R.string.muted_chat_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { next ->
                            draft = next.take(400)
                            onTypingChanged(draft)
                        },
                        placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )
                    Surface(
                        onClick = {
                            if (draft.isNotBlank()) {
                                val outgoing = draft
                                draft = ""
                                onSendMessage(outgoing)
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_chat_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send chat message",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
