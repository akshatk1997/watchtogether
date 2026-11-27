package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.VoiceActivityMonitor
import com.example.data.ClockOffsetEstimator
import com.example.data.DriftCorrectionMode
import com.example.data.FloatingReactionEvent
import com.example.data.LiveChannel
import com.example.data.MediaLookupService
import com.example.data.MediaSourceType
import com.example.data.MessageType
import com.example.data.PlaybackState
import com.example.data.RoomMessage
import com.example.data.RoomParticipant
import com.example.data.RoomQueueItem
import com.example.data.SearchProvider
import com.example.data.SearchProviderFactory
import com.example.data.StreamType
import com.example.data.SyncEngine
import com.example.data.UiState
import com.example.data.WatchRoom
import com.example.data.WatchTogetherRepository
import com.example.data.YouTubeSearchItem
import com.example.data.YouTubeSearchOutcome
import com.example.data.extractFirestoreRootError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class RoomViewModel(
    private val repository: WatchTogetherRepository,
    private val mediaLookupService: MediaLookupService,
    val roomCode: String,
    val currentUserId: String
) : ViewModel() {

    private val voiceMonitor = VoiceActivityMonitor()
    private val clockOffsetEstimator = ClockOffsetEstimator()
    private val searchProvider: SearchProvider = SearchProviderFactory.create(mediaLookupService)

    private val _roomActionMessage = MutableStateFlow<String?>(null)
    val roomActionMessage: StateFlow<String?> = _roomActionMessage.asStateFlow()

    private val _localPositionMs = MutableStateFlow(0L)
    val localPositionMs: StateFlow<Long> = _localPositionMs.asStateFlow()

    private val _driftMs = MutableStateFlow(0L)
    val driftMs: StateFlow<Long> = _driftMs.asStateFlow()

    private val _clockOffsetMs = MutableStateFlow(0L)
    val clockOffsetMs: StateFlow<Long> = _clockOffsetMs.asStateFlow()

    private val _activeSyncMode = MutableStateFlow(DriftCorrectionMode.NONE)
    val activeSyncMode: StateFlow<DriftCorrectionMode> = _activeSyncMode.asStateFlow()

    private val _isCatchingUp = MutableStateFlow(false)
    val isCatchingUp: StateFlow<Boolean> = _isCatchingUp.asStateFlow()

    private val _isAdPlaying = MutableStateFlow(false)
    val isAdPlaying: StateFlow<Boolean> = _isAdPlaying.asStateFlow()

    private val _voiceJoined = MutableStateFlow(false)
    val voiceJoined: StateFlow<Boolean> = _voiceJoined.asStateFlow()

    private val _micMuted = MutableStateFlow(true)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _floatingReactions = MutableStateFlow<List<FloatingReactionEvent>>(emptyList())
    val floatingReactions: StateFlow<List<FloatingReactionEvent>> = _floatingReactions.asStateFlow()

    private val _resolvingMedia = MutableStateFlow(false)
    val resolvingMedia: StateFlow<Boolean> = _resolvingMedia.asStateFlow()

    private val _youtubeSearching = MutableStateFlow(false)
    val youtubeSearching: StateFlow<Boolean> = _youtubeSearching.asStateFlow()

    private val _youtubeSearchResults = MutableStateFlow<List<YouTubeSearchItem>>(emptyList())
    val youtubeSearchResults: StateFlow<List<YouTubeSearchItem>> = _youtubeSearchResults.asStateFlow()

    private val _youtubeSearchNotice = MutableStateFlow<String?>(null)
    val youtubeSearchNotice: StateFlow<String?> = _youtubeSearchNotice.asStateFlow()

    private val _youtubeSearchFromCache = MutableStateFlow(false)
    val youtubeSearchFromCache: StateFlow<Boolean> = _youtubeSearchFromCache.asStateFlow()

    val searchProviderName: String = searchProvider.providerName
    val supportsKeywordApiSearch: Boolean get() = searchProvider.supportsKeywordSearch

    private val seenReactionIds = mutableSetOf<String>()
    private var lastChatSentMs = 0L
    private var typingClearJob: Job? = null
    private var searchJob: Job? = null

    val roomState: StateFlow<UiState<WatchRoom>> = repository.observeRoom(roomCode)
        .map<WatchRoom?, UiState<WatchRoom>> { room ->
            if (room == null) {
                UiState.Error("Room $roomCode no longer exists.")
            } else {
                UiState.Success(room)
            }
        }
        .catch { error ->
            val root = extractFirestoreRootError(error) ?: throw error
            Log.w(TAG, "Error observing room $roomCode", root)
            emit(UiState.Error(root.message ?: "Unable to sync room"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val participants: StateFlow<List<RoomParticipant>> = roomState
        .flatMapLatest { state ->
            val members = (state as? UiState.Success)?.data?.memberUids.orEmpty()
            if (members.isEmpty()) flowOf(emptyList())
            else repository.observeParticipants(roomCode, members)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val messages: StateFlow<List<RoomMessage>> = repository.observeMessages(roomCode, currentUserId)
        .catch { error ->
            val root = extractFirestoreRootError(error) ?: throw error
            Log.w(TAG, "Error observing messages", root)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val queueItems: StateFlow<List<RoomQueueItem>> = repository.observeQueue(roomCode, currentUserId)
        .catch { error ->
            val root = extractFirestoreRootError(error) ?: throw error
            Log.w(TAG, "Error observing room queue", root)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            messages.collect { list ->
                val recentCutoffMs = System.currentTimeMillis() - 12_000L
                list.filter { it.parsedMessageType == MessageType.REACTION }
                    .forEach { msg ->
                        val msgTime = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                        if (msg.id.isNotBlank() && msg.id !in seenReactionIds && msgTime >= recentCutoffMs) {
                            seenReactionIds.add(msg.id)
                            enqueueFloatingReaction(msg.id, msg.text, msg.senderName)
                        }
                    }
            }
        }

        // Measure client-server clock offset immediately and recompute every 30 seconds
        viewModelScope.launch {
            while (true) {
                val sample = mediaLookupService.measureServerClockSample()
                if (sample != null) {
                    val serverMid = sample.measuredAtMs - sample.roundTripMs / 2L + sample.offsetMs
                    clockOffsetEstimator.recordPingPong(
                        t0ClientSendMs = sample.measuredAtMs - sample.roundTripMs,
                        t1ServerReceiveMs = serverMid,
                        t2ServerSendMs = serverMid,
                        t3ClientReceiveMs = sample.measuredAtMs
                    )
                    _clockOffsetMs.value = clockOffsetEstimator.currentOffsetMs()
                }
                delay(SyncEngine.CLOCK_SYNC_INTERVAL_MS)
            }
        }
    }

    fun clearMessage() {
        _roomActionMessage.value = null
    }

    fun reportSyncMode(mode: DriftCorrectionMode) {
        _activeSyncMode.value = mode
    }

    fun reportBufferingCatchingUp(catchingUp: Boolean) {
        _isCatchingUp.value = catchingUp
    }

    fun reportAdPlayback(adPlaying: Boolean) {
        _isAdPlaying.value = adPlaying
    }

    fun reportLocalPlaybackPosition(positionMs: Long, room: WatchRoom) {
        _localPositionMs.value = positionMs
        val serverNow = clockOffsetEstimator.estimatedServerNowMs(System.currentTimeMillis())
        val syncAtMs = room.syncUpdatedAt?.toDate()?.time ?: serverNow
        val expected = SyncEngine.computeExpectedPositionMs(
            roomPositionMs = room.positionMs,
            syncUpdatedAtMs = syncAtMs,
            nowMs = serverNow,
            playbackState = room.parsedPlaybackState,
            playbackRate = room.playbackRate
        )
        _driftMs.value = SyncEngine.computeDriftMs(positionMs, expected)
    }

    fun togglePlayPause(room: WatchRoom) {
        if (!room.canUserControl(currentUserId)) {
            _roomActionMessage.value = "Only the host can control playback right now."
            return
        }
        val nextState = if (room.parsedPlaybackState == PlaybackState.PLAYING) {
            PlaybackState.PAUSED
        } else {
            PlaybackState.PLAYING
        }
        val syncAtMs = room.syncUpdatedAt?.toDate()?.time ?: System.currentTimeMillis()
        val currentPos = if (_localPositionMs.value > 0L) {
            _localPositionMs.value
        } else {
            SyncEngine.computeExpectedPositionMs(
                roomPositionMs = room.positionMs,
                syncUpdatedAtMs = syncAtMs,
                nowMs = System.currentTimeMillis(),
                playbackState = room.parsedPlaybackState,
                playbackRate = room.playbackRate
            )
        }
        viewModelScope.launch {
            repository.updatePlayback(
                roomCode = room.roomCode,
                playbackState = nextState,
                positionMs = currentPos,
                playbackRate = room.playbackRate
            ).onFailure { err ->
                _roomActionMessage.value = err.message ?: "Failed to sync playback."
            }
        }
    }

    fun seekByDelta(room: WatchRoom, deltaMs: Long) {
        if (!room.canUserControl(currentUserId)) {
            _roomActionMessage.value = "Only the host can seek right now."
            return
        }
        val syncAtMs = room.syncUpdatedAt?.toDate()?.time ?: System.currentTimeMillis()
        val basePos = if (_localPositionMs.value > 0L) {
            _localPositionMs.value
        } else {
            SyncEngine.computeExpectedPositionMs(
                roomPositionMs = room.positionMs,
                syncUpdatedAtMs = syncAtMs,
                nowMs = System.currentTimeMillis(),
                playbackState = room.parsedPlaybackState,
                playbackRate = room.playbackRate
            )
        }
        val targetPos = (basePos + deltaMs).coerceIn(0L, SyncEngine.MAX_POSITION_MS)
        _localPositionMs.value = targetPos
        viewModelScope.launch {
            repository.updatePlayback(
                roomCode = room.roomCode,
                playbackState = room.parsedPlaybackState,
                positionMs = targetPos,
                playbackRate = room.playbackRate
            ).onFailure { err ->
                _roomActionMessage.value = err.message ?: "Failed to sync seek."
            }
        }
    }

    fun changePlaybackRate(room: WatchRoom, rate: Double) {
        if (!room.canUserControl(currentUserId)) {
            _roomActionMessage.value = "Only the host can change speed right now."
            return
        }
        val pos = _localPositionMs.value.coerceAtLeast(room.positionMs)
        viewModelScope.launch {
            repository.updatePlayback(
                roomCode = room.roomCode,
                playbackState = room.parsedPlaybackState,
                positionMs = pos,
                playbackRate = rate
            ).onFailure { err ->
                _roomActionMessage.value = err.message ?: "Failed to change playback speed."
            }
        }
    }

    // --- MEDIA SELECTION, SEARCH PROVIDER & ROOM QUEUE ---

    fun searchYouTube(rawQuery: String) {
        val query = rawQuery.trim()
        searchJob?.cancel()
        if (query.isEmpty()) {
            _youtubeSearching.value = false
            _youtubeSearchResults.value = emptyList()
            _youtubeSearchNotice.value = null
            _youtubeSearchFromCache.value = false
            return
        }
        searchJob = viewModelScope.launch {
            // Debounce queries to save quota and prevent redundant lookups
            delay(300L)
            _youtubeSearching.value = true
            _youtubeSearchNotice.value = null
            when (val outcome = searchProvider.search(query)) {
                is YouTubeSearchOutcome.Success -> {
                    _youtubeSearchResults.value = outcome.items
                    _youtubeSearchFromCache.value = outcome.fromCache
                    _youtubeSearchNotice.value = outcome.notice
                }
                is YouTubeSearchOutcome.Error -> {
                    _youtubeSearchResults.value = emptyList()
                    _youtubeSearchFromCache.value = false
                    _youtubeSearchNotice.value = outcome.message
                }
            }
            _youtubeSearching.value = false
        }
    }

    fun selectYouTubeSearchItem(room: WatchRoom, item: YouTubeSearchItem) {
        if (!room.canUserControl(currentUserId)) {
            _roomActionMessage.value = "Only the host can switch videos directly — suggest it to the queue instead!"
            return
        }
        viewModelScope.launch {
            _resolvingMedia.value = true
            val res = repository.updateRoomMedia(
                roomCode = room.roomCode,
                sourceType = MediaSourceType.YOUTUBE,
                mediaUrl = item.videoId,
                mediaTitle = item.title,
                mediaThumbnail = item.thumbnailUrl
            )
            _resolvingMedia.value = false
            res.onSuccess {
                _localPositionMs.value = 0L
                _roomActionMessage.value = "Now playing: ${item.title}"
            }.onFailure { err ->
                _roomActionMessage.value = err.message ?: "Could not load YouTube video."
            }
        }
    }

    fun selectYouTubeLinkOrId(room: WatchRoom, rawInput: String) {
        if (!room.canUserControl(currentUserId)) {
            _roomActionMessage.value = "Only the host can switch videos directly — suggest it to the queue instead!"
            return
        }
        val playlistId = SyncEngine.parseYouTubePlaylistId(rawInput)
        val videoId = SyncEngine.parseYouTubeVideoId(rawInput)

        // Playlist URL without single video -> load official YouTube playlist into room player
        if (playlistId != null && videoId == null) {
            viewModelScope.launch {
                _resolvingMedia.value = true
                val res = repository.updateRoomMedia(
                    roomCode = room.roomCode,
                    sourceType = MediaSourceType.YOUTUBE,
                    mediaUrl = "playlist:$playlistId",
                    mediaTitle = "YouTube Playlist ($playlistId)",
                    mediaThumbnail = ""
                )
                _resolvingMedia.value = false
                res.onSuccess {
                    _localPositionMs.value = 0L
                    _roomActionMessage.value = "Loaded YouTube playlist for the room."
                }.onFailure { err ->
                    _roomActionMessage.value = err.message ?: "Could not load playlist."
                }
            }
            return
        }

        if (videoId == null) {
            _roomActionMessage.value = "Please paste a valid YouTube link, playlist URL, or 11-character video ID."
            return
        }
        viewModelScope.launch {
            _resolvingMedia.value = true
            val meta = mediaLookupService.fetchYouTubeMetadata(videoId)
            val res = repository.updateRoomMedia(
                roomCode = room.roomCode,
                sourceType = MediaSourceType.YOUTUBE,
                mediaUrl = meta.videoId,
                mediaTitle = meta.title,
                mediaThumbnail = meta.thumbnailUrl
            )
            _resolvingMedia.value = false
            res.onSuccess {
                _localPositionMs.value = 0L
                _roomActionMessage.value = "Now playing: ${meta.title}"
            }.onFailure { err ->
                _roomActionMessage.value = err.message ?: "Could not load YouTube video."
            }
        }
    }

    fun suggestToQueue(room: WatchRoom, rawInput: String, suggestedByName: String) {
        if (currentUserId in room.mutedUids) {
            _roomActionMessage.value = "You are muted by the host."
            return
        }
        val videoId = SyncEngine.parseYouTubeVideoId(rawInput)
        if (videoId == null) {
            _roomActionMessage.value = "Paste a valid YouTube video URL or 11-character ID to add to the queue."
            return
        }
        viewModelScope.launch {
            _resolvingMedia.value = true
            val meta = mediaLookupService.fetchYouTubeMetadata(videoId)
            val nextOrder = (queueItems.value.maxOfOrNull { it.orderIndex } ?: 0) + 1
            val res = repository.addQueueItem(
                room = room,
                videoId = meta.videoId,
                title = meta.title,
                channelName = meta.authorName,
                thumbnailUrl = meta.thumbnailUrl,
                suggestedByName = suggestedByName,
                orderIndex = nextOrder
            )
            _resolvingMedia.value = false
            res.onSuccess {
                val isHost = room.hostId == currentUserId
                _roomActionMessage.value = if (isHost) {
                    "Added \"${meta.title}\" to Up Next queue."
                } else {
                    "Suggested \"${meta.title}\" for host approval."
                }
            }.onFailure { err ->
                _roomActionMessage.value = err.message ?: "Could not add to queue."
            }
        }
    }

    fun approveQueueItem(room: WatchRoom, item: RoomQueueItem) {
        if (room.hostId != currentUserId) return
        viewModelScope.launch {
            repository.updateQueueItem(room.roomCode, item.id, approved = true)
                .onSuccess { _roomActionMessage.value = "Approved \"${item.title}\" in queue." }
                .onFailure { err -> _roomActionMessage.value = err.message ?: "Failed to approve item." }
        }
    }

    fun moveQueueItem(room: WatchRoom, item: RoomQueueItem, moveUp: Boolean) {
        if (room.hostId != currentUserId) return
        val approved = queueItems.value.filter { it.approved }.sortedBy { it.orderIndex }
        val index = approved.indexOfFirst { it.id == item.id }
        if (index < 0) return
        val swapIndex = if (moveUp) index - 1 else index + 1
        if (swapIndex !in approved.indices) return
        val other = approved[swapIndex]
        viewModelScope.launch {
            repository.updateQueueItem(room.roomCode, item.id, orderIndex = other.orderIndex)
            repository.updateQueueItem(room.roomCode, other.id, orderIndex = item.orderIndex)
        }
    }

    fun playQueueItemNow(room: WatchRoom, item: RoomQueueItem) {
        if (!room.canUserControl(currentUserId)) {
            _roomActionMessage.value = "Only the host can start queued videos."
            return
        }
        viewModelScope.launch {
            repository.updateRoomMedia(
                roomCode = room.roomCode,
                sourceType = MediaSourceType.YOUTUBE,
                mediaUrl = item.videoId,
                mediaTitle = item.title,
                mediaThumbnail = item.thumbnailUrl
            ).onSuccess {
                _localPositionMs.value = 0L
                repository.removeQueueItem(room.roomCode, item.id)
                _roomActionMessage.value = "Playing next: ${item.title}"
            }.onFailure { err ->
                _roomActionMessage.value = err.message ?: "Failed to play queued video."
            }
        }
    }

    fun playNextInQueue(room: WatchRoom) {
        if (room.hostId != currentUserId) return
        val nextApproved = queueItems.value.filter { it.approved }.minByOrNull { it.orderIndex } ?: return
        playQueueItemNow(room, nextApproved)
    }

    fun removeQueueItem(room: WatchRoom, item: RoomQueueItem) {
        viewModelScope.launch {
            repository.removeQueueItem(room.roomCode, item.id)
        }
    }

    fun selectGoogleDriveLink(room: WatchRoom, rawLink: String, customTitle: String) {
        if (!room.canUserControl(currentUserId)) {
            _roomActionMessage.value = "Only the host can switch videos."
            return
        }
        val fileId = SyncEngine.parseGoogleDriveFileId(rawLink)
        if (fileId == null) {
            _roomActionMessage.value = "Paste a Google Drive share link (drive.google.com/file/d/...)."
            return
        }
        val previewUrl = SyncEngine.buildDrivePreviewUrl(fileId)
        val title = customTitle.trim().ifBlank { "Google Drive Video" }
        viewModelScope.launch {
            repository.updateRoomMedia(
                roomCode = room.roomCode,
                sourceType = MediaSourceType.DRIVE,
                mediaUrl = previewUrl,
                mediaTitle = title
            ).onSuccess {
                _localPositionMs.value = 0L
                _roomActionMessage.value = "Loaded Google Drive video for the room."
            }.onFailure { err ->
                _roomActionMessage.value = err.message ?: "Could not load Drive video."
            }
        }
    }

    fun selectDirectStreamUrl(room: WatchRoom, streamUrl: String, title: String) {
        if (!room.canUserControl(currentUserId)) {
            _roomActionMessage.value = "Only the host can switch videos."
            return
        }
        val trimmed = streamUrl.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            _roomActionMessage.value = "Enter a valid HTTP/HTTPS HLS (.m3u8) or MP4 URL."
            return
        }
        viewModelScope.launch {
            repository.updateRoomMedia(
                roomCode = room.roomCode,
                sourceType = MediaSourceType.DIRECT_VIDEO,
                mediaUrl = trimmed,
                mediaTitle = title.trim().ifBlank { "Direct Video Stream" }
            ).onSuccess {
                _localPositionMs.value = 0L
                _roomActionMessage.value = "Switched to direct stream."
            }.onFailure { err ->
                _roomActionMessage.value = err.message ?: "Failed to load stream."
            }
        }
    }

    fun selectLiveChannel(room: WatchRoom, channel: LiveChannel) {
        if (!room.canUserControl(currentUserId)) {
            _roomActionMessage.value = "Only the host can switch channels."
            return
        }
        val sourceType = if (channel.parsedStreamType == StreamType.YOUTUBE_LIVE) {
            MediaSourceType.YOUTUBE
        } else {
            MediaSourceType.LIVE_TV
        }
        val url = if (sourceType == MediaSourceType.YOUTUBE) {
            SyncEngine.parseYouTubeVideoId(channel.streamUrl) ?: channel.streamUrl
        } else {
            channel.streamUrl
        }
        viewModelScope.launch {
            repository.updateRoomMedia(
                roomCode = room.roomCode,
                sourceType = sourceType,
                mediaUrl = url,
                mediaTitle = "${channel.name} • ${channel.broadcaster}"
            ).onSuccess {
                _localPositionMs.value = 0L
                _roomActionMessage.value = "Tuned into ${channel.name}"
            }.onFailure { err ->
                _roomActionMessage.value = err.message ?: "Failed to switch channel."
            }
        }
    }

    // --- CHAT, TYPING & REACTIONS ---

    fun onChatInputChanged(roomCode: String, text: String) {
        val isTyping = text.isNotBlank()
        typingClearJob?.cancel()
        viewModelScope.launch {
            repository.updateMyParticipantState(roomCode = roomCode, typing = isTyping)
        }
        if (isTyping) {
            typingClearJob = viewModelScope.launch {
                delay(3500L)
                repository.updateMyParticipantState(roomCode = roomCode, typing = false)
            }
        }
    }

    fun sendChatMessage(
        room: WatchRoom,
        senderName: String,
        senderColorHex: String,
        rawText: String
    ) {
        val now = System.currentTimeMillis()
        if (now - lastChatSentMs < 600L) {
            _roomActionMessage.value = "You're sending messages too fast."
            return
        }
        if (currentUserId in room.mutedUids) {
            _roomActionMessage.value = "You are muted by the host."
            return
        }
        val sanitized = SyncEngine.sanitizeChatText(rawText)
        if (sanitized.isEmpty()) return
        lastChatSentMs = now
        typingClearJob?.cancel()
        viewModelScope.launch {
            repository.updateMyParticipantState(roomCode = room.roomCode, typing = false)
            repository.sendMessage(
                roomCode = room.roomCode,
                senderName = senderName,
                senderColorHex = senderColorHex,
                rawText = sanitized,
                messageType = MessageType.TEXT,
                memberUids = room.memberUids
            ).onFailure { err ->
                _roomActionMessage.value = err.message ?: "Could not send message."
            }
        }
    }

    fun sendReaction(
        room: WatchRoom,
        senderName: String,
        senderColorHex: String,
        emoji: String
    ) {
        if (currentUserId in room.mutedUids) {
            _roomActionMessage.value = "You are muted by the host."
            return
        }
        val localId = "local_${System.currentTimeMillis()}_${emoji.hashCode()}"
        enqueueFloatingReaction(localId, emoji, senderName)
        viewModelScope.launch {
            repository.sendMessage(
                roomCode = room.roomCode,
                senderName = senderName,
                senderColorHex = senderColorHex,
                rawText = emoji,
                messageType = MessageType.REACTION,
                memberUids = room.memberUids
            )
        }
    }

    private fun enqueueFloatingReaction(id: String, emoji: String, senderName: String) {
        val lane = (abs(id.hashCode()) % 70 + 15) / 100f
        val event = FloatingReactionEvent(id, emoji, senderName, lane)
        _floatingReactions.value = (_floatingReactions.value + event).takeLast(14)
        viewModelScope.launch {
            delay(2600L)
            _floatingReactions.value = _floatingReactions.value.filterNot { it.id == id }
        }
    }

    // --- VOICE CHAT & MICROPHONE ACTIVITY ---

    fun joinVoiceChannel(hasAudioPermission: Boolean) {
        _voiceJoined.value = true
        val startMuted = !hasAudioPermission
        _micMuted.value = startMuted
        viewModelScope.launch {
            repository.updateMyParticipantState(
                roomCode = roomCode,
                voiceJoined = true,
                micMuted = startMuted,
                speaking = false
            )
        }
        if (hasAudioPermission) {
            startVoiceMonitoring()
        }
    }

    fun leaveVoiceChannel() {
        voiceMonitor.stop()
        _voiceJoined.value = false
        _micMuted.value = true
        _micLevel.value = 0f
        viewModelScope.launch {
            repository.updateMyParticipantState(
                roomCode = roomCode,
                voiceJoined = false,
                micMuted = true,
                speaking = false
            )
        }
    }

    fun toggleMicMute(hasAudioPermission: Boolean) {
        if (!_voiceJoined.value) {
            joinVoiceChannel(hasAudioPermission)
            return
        }
        val nextMuted = !_micMuted.value
        if (!nextMuted && !hasAudioPermission) {
            _roomActionMessage.value = "Microphone permission is required to unmute."
            return
        }
        _micMuted.value = nextMuted
        if (nextMuted) {
            voiceMonitor.stop()
            _micLevel.value = 0f
            viewModelScope.launch {
                repository.updateMyParticipantState(
                    roomCode = roomCode,
                    micMuted = true,
                    speaking = false
                )
            }
        } else {
            viewModelScope.launch {
                repository.updateMyParticipantState(roomCode = roomCode, micMuted = false)
            }
            startVoiceMonitoring()
        }
    }

    private fun startVoiceMonitoring() {
        voiceMonitor.start(
            scope = viewModelScope,
            onLevel = { level -> _micLevel.value = level },
            onSpeakingChanged = { speaking ->
                viewModelScope.launch {
                    repository.updateMyParticipantState(
                        roomCode = roomCode,
                        speaking = speaking && !_micMuted.value
                    )
                }
            }
        )
    }

    // --- HOST CONTROLS ---

    fun updateHostSettings(
        room: WatchRoom,
        everyoneCanControl: Boolean,
        roomLocked: Boolean,
        waitForEveryone: Boolean = room.waitForEveryone
    ) {
        if (room.hostId != currentUserId) return
        viewModelScope.launch {
            repository.updateHostSettings(room.roomCode, everyoneCanControl, roomLocked, waitForEveryone)
                .onFailure { err ->
                    _roomActionMessage.value = err.message ?: "Could not update room settings."
                }
        }
    }

    fun toggleMuteGuest(room: WatchRoom, targetUid: String) {
        if (room.hostId != currentUserId) return
        viewModelScope.launch {
            repository.toggleMuteParticipant(room, targetUid)
                .onSuccess {
                    val nowMuted = targetUid !in room.mutedUids
                    _roomActionMessage.value = if (nowMuted) "Participant muted." else "Participant unmuted."
                }
                .onFailure { err ->
                    _roomActionMessage.value = err.message ?: "Failed to update mute state."
                }
        }
    }

    fun kickGuest(room: WatchRoom, targetUid: String) {
        if (room.hostId != currentUserId) return
        viewModelScope.launch {
            repository.kickParticipant(room, targetUid)
                .onSuccess { _roomActionMessage.value = "Participant removed from room." }
                .onFailure { err -> _roomActionMessage.value = err.message ?: "Failed to remove participant." }
        }
    }

    fun leaveOrEndRoom(room: WatchRoom, endForEveryone: Boolean, onDone: () -> Unit) {
        voiceMonitor.stop()
        viewModelScope.launch {
            if (endForEveryone && room.hostId == currentUserId) {
                repository.endRoom(room.roomCode)
            } else {
                repository.leaveRoom(room)
            }
            onDone()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceMonitor.stop()
    }

    private companion object {
        const val TAG = "RoomViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
