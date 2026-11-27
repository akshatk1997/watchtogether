package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChannelCategory
import com.example.data.CuratedContentCatalog
import com.example.data.LiveChannel
import com.example.data.MediaLookupService
import com.example.data.MediaSourceType
import com.example.data.StreamHealth
import com.example.data.StreamType
import com.example.data.SyncEngine
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.WatchRoom
import com.example.data.WatchTogetherRepository
import com.example.data.extractFirestoreRootError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val repository: WatchTogetherRepository,
    private val mediaLookupService: MediaLookupService,
    val currentUserId: String,
    private val initialDisplayName: String
) : ViewModel() {

    val isAdmin: Boolean = repository.isCurrentUserAdmin()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _busyAction = MutableStateFlow(false)
    val busyAction: StateFlow<Boolean> = _busyAction.asStateFlow()

    private val _healthCheckRunning = MutableStateFlow(false)
    val healthCheckRunning: StateFlow<Boolean> = _healthCheckRunning.asStateFlow()

    private val _localHealthOverrides = MutableStateFlow<Map<String, StreamHealth>>(emptyMap())
    val localHealthOverrides: StateFlow<Map<String, StreamHealth>> = _localHealthOverrides.asStateFlow()

    val userProfile: StateFlow<UiState<UserProfile>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile>> { profile ->
            UiState.Success(
                profile ?: UserProfile(
                    id = currentUserId,
                    userId = currentUserId,
                    displayName = SyncEngine.sanitizeDisplayName(initialDisplayName),
                    avatarColorHex = "#FF4D6D"
                )
            )
        }
        .catch { error ->
            val root = extractFirestoreRootError(error) ?: throw error
            Log.w(TAG, "Error observing user profile", root)
            emit(UiState.Error(root.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val myRooms: StateFlow<UiState<List<WatchRoom>>> = repository.observeMyRooms(currentUserId)
        .map<List<WatchRoom>, UiState<List<WatchRoom>>> { UiState.Success(it) }
        .catch { error ->
            val root = extractFirestoreRootError(error) ?: throw error
            Log.w(TAG, "Error observing user rooms", root)
            emit(UiState.Error(root.message ?: "Failed to load rooms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val liveChannels: StateFlow<UiState<List<LiveChannel>>> = repository
        .observeLiveChannels(includeUnpublishedForAdmin = isAdmin)
        .map<List<LiveChannel>, UiState<List<LiveChannel>>> { remote ->
            val effective = if (remote.isEmpty()) {
                CuratedContentCatalog.defaultIndianChannels
            } else {
                remote
            }
            UiState.Success(effective)
        }
        .catch { error ->
            val root = extractFirestoreRootError(error) ?: throw error
            Log.w(TAG, "Error observing live channels", root)
            emit(UiState.Error(root.message ?: "Failed to load Live TV catalog"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private var lastRoomCreateMs = 0L

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(
                displayName = initialDisplayName,
                avatarColorHex = "#FF4D6D"
            )
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun currentProfileOrDefault(): UserProfile {
        val state = userProfile.value
        return if (state is UiState.Success) state.data else UserProfile(
            id = currentUserId,
            userId = currentUserId,
            displayName = SyncEngine.sanitizeDisplayName(initialDisplayName),
            avatarColorHex = "#FF4D6D"
        )
    }

    fun createWatchRoom(
        title: String,
        nicknameOverride: String?,
        sourceType: MediaSourceType = MediaSourceType.YOUTUBE,
        mediaUrl: String = CuratedContentCatalog.starterYouTubeVideos.first().videoId,
        mediaTitle: String = CuratedContentCatalog.starterYouTubeVideos.first().title,
        mediaThumbnail: String = CuratedContentCatalog.starterYouTubeVideos.first().thumbnailUrl,
        onCreated: (String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        if (now - lastRoomCreateMs < 2000L) {
            _statusBanner.value = "Please wait a moment before creating another party."
            return
        }
        val profile = currentProfileOrDefault()
        val chosenName = nicknameOverride?.takeIf { it.isNotBlank() } ?: profile.displayName
        viewModelScope.launch {
            _busyAction.value = true
            val result = repository.createRoom(
                title = title,
                hostName = chosenName,
                hostColorHex = profile.avatarColorHex,
                sourceType = sourceType,
                mediaUrl = mediaUrl,
                mediaTitle = mediaTitle,
                mediaThumbnail = mediaThumbnail
            )
            _busyAction.value = false
            result.onSuccess { code ->
                lastRoomCreateMs = System.currentTimeMillis()
                onCreated(code)
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Could not create watch party."
            }
        }
    }

    fun createRoomFromChannel(channel: LiveChannel, onCreated: (String) -> Unit) {
        val sourceType = if (channel.parsedStreamType == StreamType.YOUTUBE_LIVE) {
            MediaSourceType.YOUTUBE
        } else {
            MediaSourceType.LIVE_TV
        }
        val mediaUrl = if (sourceType == MediaSourceType.YOUTUBE) {
            SyncEngine.parseYouTubeVideoId(channel.streamUrl) ?: channel.streamUrl
        } else {
            channel.streamUrl
        }
        createWatchRoom(
            title = "${channel.name} Watch Party",
            nicknameOverride = null,
            sourceType = sourceType,
            mediaUrl = mediaUrl,
            mediaTitle = "${channel.name} • ${channel.broadcaster}",
            mediaThumbnail = "",
            onCreated = onCreated
        )
    }

    fun joinWatchRoom(
        rawCode: String,
        nicknameOverride: String?,
        onJoined: (String) -> Unit
    ) {
        val profile = currentProfileOrDefault()
        val chosenName = nicknameOverride?.takeIf { it.isNotBlank() } ?: profile.displayName
        viewModelScope.launch {
            _busyAction.value = true
            val result = repository.joinRoom(
                rawRoomCode = rawCode,
                displayName = chosenName,
                avatarColorHex = profile.avatarColorHex
            )
            _busyAction.value = false
            result.onSuccess { code ->
                onJoined(code)
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Could not join room."
            }
        }
    }

    fun updateProfile(
        displayName: String,
        avatarColorHex: String,
        darkThemeEnabled: Boolean,
        autoSyncEnabled: Boolean,
        hapticsEnabled: Boolean,
        liveKitUrl: String
    ) {
        viewModelScope.launch {
            val result = repository.updateUserProfile(
                displayName = displayName,
                avatarColorHex = avatarColorHex,
                darkThemeEnabled = darkThemeEnabled,
                autoSyncEnabled = autoSyncEnabled,
                hapticsEnabled = hapticsEnabled,
                liveKitUrl = liveKitUrl
            )
            result.onSuccess {
                _statusBanner.value = "Preferences saved."
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Could not save preferences."
            }
        }
    }

    // --- LIVE TV HEALTH CHECK & ADMIN MANAGEMENT ---

    fun runStreamHealthChecks() {
        if (_healthCheckRunning.value) return
        val channels = (liveChannels.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _healthCheckRunning.value = true
            var onlineCount = 0
            var offlineCount = 0
            val nextOverrides = _localHealthOverrides.value.toMutableMap()
            for (ch in channels) {
                val health = mediaLookupService.checkChannelHealth(ch)
                nextOverrides[ch.id] = health
                _localHealthOverrides.value = nextOverrides.toMap()
                if (health == StreamHealth.ONLINE || health == StreamHealth.DEGRADED) {
                    onlineCount++
                } else {
                    offlineCount++
                }
                if (isAdmin && ch.createdBy.isNotBlank()) {
                    repository.updateChannelHealth(
                        channelId = ch.id,
                        health = health,
                        autoHideOffline = true
                    )
                }
            }
            _healthCheckRunning.value = false
            _statusBanner.value = "Health check complete: $onlineCount live, $offlineCount unreachable."
        }
    }

    fun seedStarterChannels() {
        if (!isAdmin) return
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.seedStarterLiveChannels()
            _busyAction.value = false
            res.onSuccess { count ->
                _statusBanner.value = "Synced $count official Indian channels to Firestore."
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Failed to seed channels."
            }
        }
    }

    fun saveChannel(
        channelId: String?,
        name: String,
        category: ChannelCategory,
        language: String,
        streamType: StreamType,
        streamUrl: String,
        broadcaster: String,
        badgeColorHex: String,
        published: Boolean
    ) {
        if (!isAdmin) return
        viewModelScope.launch {
            _busyAction.value = true
            val res = repository.upsertLiveChannel(
                channelId = channelId,
                name = name,
                category = category,
                language = language,
                streamType = streamType,
                streamUrl = streamUrl,
                broadcaster = broadcaster,
                badgeColorHex = badgeColorHex,
                published = published,
                healthStatus = StreamHealth.UNKNOWN
            )
            _busyAction.value = false
            res.onSuccess {
                _statusBanner.value = "Channel \"$name\" saved."
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Failed to save channel."
            }
        }
    }

    fun deleteChannel(channelId: String) {
        if (!isAdmin) return
        viewModelScope.launch {
            val res = repository.deleteLiveChannel(channelId)
            res.onSuccess {
                _statusBanner.value = "Channel removed."
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Failed to delete channel."
            }
        }
    }

    private companion object {
        const val TAG = "AppViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
