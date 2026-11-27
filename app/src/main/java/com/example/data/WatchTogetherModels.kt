package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class MediaSourceType(val label: String) {
    YOUTUBE("YouTube"),
    LIVE_TV("Live TV"),
    DRIVE("Google Drive"),
    DIRECT_VIDEO("Direct Stream");

    companion object {
        fun fromRaw(raw: String): MediaSourceType =
            entries.firstOrNull { it.name == raw } ?: YOUTUBE
    }
}

enum class PlaybackState {
    PLAYING,
    PAUSED,
    BUFFERING,
    AD;

    companion object {
        fun fromRaw(raw: String): PlaybackState =
            entries.firstOrNull { it.name == raw } ?: PAUSED
    }
}

enum class RoomStatus {
    ACTIVE,
    ENDED;

    companion object {
        fun fromRaw(raw: String): RoomStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class ParticipantRole {
    HOST,
    GUEST;

    companion object {
        fun fromRaw(raw: String): ParticipantRole =
            entries.firstOrNull { it.name == raw } ?: GUEST
    }
}

enum class MessageType {
    TEXT,
    SYSTEM,
    REACTION;

    companion object {
        fun fromRaw(raw: String): MessageType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class ChannelCategory(val label: String) {
    NEWS("News"),
    ENTERTAINMENT("Entertainment"),
    MUSIC("Music"),
    REGIONAL("Regional"),
    KIDS("Kids"),
    DEVOTIONAL("Devotional");

    companion object {
        fun fromRaw(raw: String): ChannelCategory =
            entries.firstOrNull { it.name == raw } ?: NEWS
    }
}

enum class StreamType(val label: String) {
    HLS("HLS Live"),
    YOUTUBE_LIVE("YouTube Live");

    companion object {
        fun fromRaw(raw: String): StreamType =
            entries.firstOrNull { it.name == raw } ?: YOUTUBE_LIVE
    }
}

enum class StreamHealth(val label: String) {
    ONLINE("Live"),
    DEGRADED("Slow"),
    OFFLINE("Offline"),
    UNKNOWN("Unchecked");

    companion object {
        fun fromRaw(raw: String): StreamHealth =
            entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Cinema Fan",
    val avatarColorHex: String = "#FF4D6D",
    val darkThemeEnabled: Boolean = true,
    val autoSyncEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val liveKitUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WatchRoom(
    @DocumentId val roomCode: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val title: String = "Watch Party",
    val sourceType: String = MediaSourceType.YOUTUBE.name,
    val mediaUrl: String = "",
    val mediaTitle: String = "",
    val mediaThumbnail: String = "",
    val playbackState: String = PlaybackState.PAUSED.name,
    val positionMs: Long = 0L,
    val playbackRate: Double = 1.0,
    val everyoneCanControl: Boolean = false,
    val roomLocked: Boolean = false,
    val waitForEveryone: Boolean = false,
    val status: String = RoomStatus.ACTIVE.name,
    val memberUids: List<String> = emptyList(),
    val kickedUids: List<String> = emptyList(),
    val mutedUids: List<String> = emptyList(),
    @ServerTimestamp val syncUpdatedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedSourceType: MediaSourceType get() = MediaSourceType.fromRaw(sourceType)
    val parsedPlaybackState: PlaybackState get() = PlaybackState.fromRaw(playbackState)
    val parsedStatus: RoomStatus get() = RoomStatus.fromRaw(status)

    fun canUserControl(uid: String): Boolean =
        parsedStatus == RoomStatus.ACTIVE &&
            (hostId == uid || (everyoneCanControl && uid in memberUids && uid !in kickedUids))
}

@Keep
@IgnoreExtraProperties
data class RoomParticipant(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Guest",
    val avatarColorHex: String = "#2EC4B6",
    val role: String = ParticipantRole.GUEST.name,
    val typing: Boolean = false,
    val micMuted: Boolean = true,
    val speaking: Boolean = false,
    val voiceJoined: Boolean = false,
    @ServerTimestamp val joinedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedRole: ParticipantRole get() = ParticipantRole.fromRaw(role)
}

@Keep
@IgnoreExtraProperties
data class RoomMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderColorHex: String = "#FF4D6D",
    val text: String = "",
    val messageType: String = MessageType.TEXT.name,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedMessageType: MessageType get() = MessageType.fromRaw(messageType)
}

@Keep
@IgnoreExtraProperties
data class LiveChannel(
    @DocumentId val id: String = "",
    val name: String = "",
    val category: String = ChannelCategory.NEWS.name,
    val language: String = "Hindi",
    val streamType: String = StreamType.YOUTUBE_LIVE.name,
    val streamUrl: String = "",
    val broadcaster: String = "",
    val badgeColorHex: String = "#FFB703",
    val published: Boolean = true,
    val healthStatus: String = StreamHealth.UNKNOWN.name,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedCategory: ChannelCategory get() = ChannelCategory.fromRaw(category)
    val parsedStreamType: StreamType get() = StreamType.fromRaw(streamType)
    val parsedHealth: StreamHealth get() = StreamHealth.fromRaw(healthStatus)
}

data class FloatingReactionEvent(
    val id: String,
    val emoji: String,
    val senderName: String,
    val laneFraction: Float
)

@Keep
@IgnoreExtraProperties
data class RoomQueueItem(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val suggestedByUid: String = "",
    val suggestedByName: String = "",
    val approved: Boolean = false,
    val orderIndex: Int = 0,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

