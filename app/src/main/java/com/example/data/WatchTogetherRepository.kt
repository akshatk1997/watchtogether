package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WatchTogetherRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email == BOOTSTRAPPED_ADMIN_EMAIL
    }

    // --- USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val root = extractFirestoreRootError(error) ?: throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(root, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        avatarColorHex: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = SyncEngine.sanitizeDisplayName(displayName)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "avatarColorHex" to normalizeHex(avatarColorHex),
                    "darkThemeEnabled" to true,
                    "autoSyncEnabled" to true,
                    "hapticsEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        displayName: String,
        avatarColorHex: String,
        darkThemeEnabled: Boolean,
        autoSyncEnabled: Boolean,
        hapticsEnabled: Boolean,
        liveKitUrl: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            requireUserId()
            val safeName = SyncEngine.sanitizeDisplayName(displayName)
            val safeLiveKit = liveKitUrl.trim().take(300)
            val updates = mutableMapOf<String, Any>(
                "displayName" to safeName,
                "avatarColorHex" to normalizeHex(avatarColorHex),
                "darkThemeEnabled" to darkThemeEnabled,
                "autoSyncEnabled" to autoSyncEnabled,
                "hapticsEnabled" to hapticsEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (safeLiveKit.isNotEmpty()) {
                updates["liveKitUrl"] = safeLiveKit
            } else {
                updates["liveKitUrl"] = ""
            }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- ROOMS ---

    fun observeMyRooms(userId: String): Flow<List<WatchRoom>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "rooms"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(WatchRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val root = extractFirestoreRootError(error) ?: throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoom(roomCode: String): Flow<WatchRoom?> = flow {
        val normalized = SyncEngine.normalizeRoomCode(roomCode)
        val uid = requireUserId()
        if (!SyncEngine.isValidRoomCode(normalized)) {
            emit(null)
            return@flow
        }
        val path = "rooms/$normalized"
        emitAll(
            db.collection("rooms").document(normalized)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(WatchRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val root = extractFirestoreRootError(error) ?: throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createRoom(
        title: String,
        hostName: String,
        hostColorHex: String,
        sourceType: MediaSourceType = MediaSourceType.YOUTUBE,
        mediaUrl: String = CuratedContentCatalog.starterYouTubeVideos.first().videoId,
        mediaTitle: String = CuratedContentCatalog.starterYouTubeVideos.first().title,
        mediaThumbnail: String = CuratedContentCatalog.starterYouTubeVideos.first().thumbnailUrl
    ): Result<String> {
        var docPath = "rooms"
        return try {
            val uid = requireUserId()
            val safeTitle = title.trim().ifBlank { "Watch Party" }.take(80)
            val safeHostName = SyncEngine.sanitizeDisplayName(hostName)

            var chosenCode = SyncEngine.generateRoomCode()
            repeat(5) {
                val candidateRef = db.collection("rooms").document(chosenCode)
                val existing = candidateRef.get().await()
                if (!existing.exists()) return@repeat
                chosenCode = SyncEngine.generateRoomCode()
            }

            val roomRef = db.collection("rooms").document(chosenCode)
            docPath = roomRef.path

            val roomPayload = mutableMapOf<String, Any?>(
                "hostId" to uid,
                "hostName" to safeHostName,
                "title" to safeTitle,
                "sourceType" to sourceType.name,
                "mediaUrl" to mediaUrl.trim().take(1000),
                "mediaTitle" to mediaTitle.trim().take(120),
                "mediaThumbnail" to mediaThumbnail.trim().take(500).ifBlank { null },
                "playbackState" to PlaybackState.PAUSED.name,
                "positionMs" to 0L,
                "playbackRate" to 1.0,
                "everyoneCanControl" to false,
                "roomLocked" to false,
                "waitForEveryone" to false,
                "status" to RoomStatus.ACTIVE.name,
                "memberUids" to listOf(uid),
                "kickedUids" to emptyList<String>(),
                "mutedUids" to emptyList<String>(),
                "syncUpdatedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            roomRef.set(roomPayload).await()

            upsertParticipantDoc(
                roomCode = chosenCode,
                uid = uid,
                displayName = safeHostName,
                avatarColorHex = hostColorHex,
                role = ParticipantRole.HOST
            )

            sendSystemMessage(
                roomCode = chosenCode,
                senderName = safeHostName,
                senderColorHex = hostColorHex,
                text = "$safeHostName created the watch party",
                memberUids = listOf(uid)
            )

            Result.success(chosenCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun joinRoom(
        rawRoomCode: String,
        displayName: String,
        avatarColorHex: String
    ): Result<String> {
        val code = SyncEngine.normalizeRoomCode(rawRoomCode)
        if (!SyncEngine.isValidRoomCode(code)) {
            return Result.failure(IllegalArgumentException("Enter a valid 6-character room code."))
        }
        val docPath = "rooms/$code"
        return try {
            val uid = requireUserId()
            val safeName = SyncEngine.sanitizeDisplayName(displayName)
            val roomRef = db.collection("rooms").document(code)
            val snap = roomRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalArgumentException("Room $code was not found."))
            }
            val room = snap.toObject(WatchRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Could not load room $code."))

            if (room.parsedStatus == RoomStatus.ENDED) {
                return Result.failure(IllegalStateException("This watch party has ended."))
            }
            if (uid in room.kickedUids) {
                return Result.failure(IllegalStateException("You were removed from this room by the host."))
            }

            val updatedMembers = if (uid !in room.memberUids) {
                if (room.roomLocked) {
                    return Result.failure(IllegalStateException("This room is locked by the host."))
                }
                if (room.memberUids.size >= 30) {
                    return Result.failure(IllegalStateException("This room is full (max 30 viewers)."))
                }
                val newMembers = room.memberUids + uid
                roomRef.update(
                    mapOf(
                        "memberUids" to newMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                newMembers
            } else {
                room.memberUids
            }

            val role = if (room.hostId == uid) ParticipantRole.HOST else ParticipantRole.GUEST
            upsertParticipantDoc(
                roomCode = code,
                uid = uid,
                displayName = safeName,
                avatarColorHex = avatarColorHex,
                role = role
            )

            if (uid !in room.memberUids) {
                sendSystemMessage(
                    roomCode = code,
                    senderName = safeName,
                    senderColorHex = avatarColorHex,
                    text = "$safeName joined the party",
                    memberUids = updatedMembers
                )
            }

            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(room: WatchRoom): Result<Unit> {
        val code = room.roomCode
        val docPath = "rooms/$code"
        return try {
            val uid = requireUserId()
            if (room.hostId == uid || room.parsedStatus != RoomStatus.ACTIVE) {
                return Result.success(Unit)
            }
            if (uid in room.memberUids) {
                val remaining = room.memberUids.filter { it != uid }
                if (remaining.isNotEmpty()) {
                    db.collection("rooms").document(code).update(
                        mapOf(
                            "memberUids" to remaining,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            db.collection("rooms").document(code)
                .collection("participants").document(uid)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePlayback(
        roomCode: String,
        playbackState: PlaybackState,
        positionMs: Long,
        playbackRate: Double
    ): Result<Unit> {
        val docPath = "rooms/$roomCode"
        return try {
            requireUserId()
            db.collection("rooms").document(roomCode).update(
                mapOf(
                    "playbackState" to playbackState.name,
                    "positionMs" to positionMs.coerceIn(0L, SyncEngine.MAX_POSITION_MS),
                    "playbackRate" to playbackRate.coerceIn(0.25, 2.0),
                    "syncUpdatedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRoomMedia(
        roomCode: String,
        sourceType: MediaSourceType,
        mediaUrl: String,
        mediaTitle: String,
        mediaThumbnail: String = ""
    ): Result<Unit> {
        val docPath = "rooms/$roomCode"
        return try {
            requireUserId()
            val payload = mutableMapOf<String, Any>(
                "sourceType" to sourceType.name,
                "mediaUrl" to mediaUrl.trim().take(1000),
                "mediaTitle" to mediaTitle.trim().take(120),
                "playbackState" to PlaybackState.PLAYING.name,
                "positionMs" to 0L,
                "playbackRate" to 1.0,
                "syncUpdatedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (mediaThumbnail.isNotBlank()) {
                payload["mediaThumbnail"] = mediaThumbnail.trim().take(500)
            }
            db.collection("rooms").document(roomCode).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateHostSettings(
        roomCode: String,
        everyoneCanControl: Boolean,
        roomLocked: Boolean,
        waitForEveryone: Boolean = false
    ): Result<Unit> {
        val docPath = "rooms/$roomCode"
        return try {
            requireUserId()
            db.collection("rooms").document(roomCode).update(
                mapOf(
                    "everyoneCanControl" to everyoneCanControl,
                    "roomLocked" to roomLocked,
                    "waitForEveryone" to waitForEveryone,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleMuteParticipant(room: WatchRoom, targetUid: String): Result<Unit> {
        val docPath = "rooms/${room.roomCode}"
        return try {
            requireUserId()
            val currentlyMuted = targetUid in room.mutedUids
            val nextMuted = if (currentlyMuted) {
                room.mutedUids.filter { it != targetUid }
            } else {
                (room.mutedUids + targetUid).distinct().take(30)
            }
            db.collection("rooms").document(room.roomCode).update(
                mapOf(
                    "mutedUids" to nextMuted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (!currentlyMuted) {
                runCatching {
                    db.collection("rooms").document(room.roomCode)
                        .collection("participants").document(targetUid)
                        .update(
                            mapOf(
                                "micMuted" to true,
                                "speaking" to false,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun kickParticipant(room: WatchRoom, targetUid: String): Result<Unit> {
        val docPath = "rooms/${room.roomCode}"
        return try {
            requireUserId()
            if (targetUid == room.hostId) {
                return Result.failure(IllegalArgumentException("Host cannot kick themselves."))
            }
            val nextMembers = room.memberUids.filter { it != targetUid }.ifEmpty { listOf(room.hostId) }
            val nextKicked = (room.kickedUids + targetUid).distinct().take(30)
            val nextMuted = room.mutedUids.filter { it != targetUid }
            db.collection("rooms").document(room.roomCode).update(
                mapOf(
                    "memberUids" to nextMembers,
                    "kickedUids" to nextKicked,
                    "mutedUids" to nextMuted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                db.collection("rooms").document(room.roomCode)
                    .collection("participants").document(targetUid)
                    .delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun endRoom(roomCode: String): Result<Unit> {
        val docPath = "rooms/$roomCode"
        return try {
            requireUserId()
            db.collection("rooms").document(roomCode).update(
                mapOf(
                    "status" to RoomStatus.ENDED.name,
                    "playbackState" to PlaybackState.PAUSED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- PARTICIPANTS (PER-DOCUMENT GET OBSERVATION, ZERO LIST QUERIES) ---

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeParticipants(roomCode: String, memberUids: List<String>): Flow<List<RoomParticipant>> {
        if (roomCode.isBlank() || memberUids.isEmpty()) return flowOf(emptyList())
        val uniqueUids = memberUids.distinct().take(30)
        val flows = uniqueUids.map { memberUid ->
            val path = "rooms/$roomCode/participants/$memberUid"
            db.collection("rooms").document(roomCode)
                .collection("participants").document(memberUid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(RoomParticipant::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val root = extractFirestoreRootError(error) ?: throw error
                    handleFirestoreError(root, OperationType.GET, path)
                    emit(null)
                }
        }
        return combine(flows) { array ->
            array.filterNotNull().sortedWith(
                compareBy<RoomParticipant> { if (it.parsedRole == ParticipantRole.HOST) 0 else 1 }
                    .thenBy { it.joinedAt?.seconds ?: Long.MAX_VALUE }
            )
        }
    }

    private suspend fun upsertParticipantDoc(
        roomCode: String,
        uid: String,
        displayName: String,
        avatarColorHex: String,
        role: ParticipantRole
    ) {
        val ref = db.collection("rooms").document(roomCode)
            .collection("participants").document(uid)
        val snap = ref.get().await()
        if (!snap.exists()) {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "avatarColorHex" to normalizeHex(avatarColorHex),
                    "role" to role.name,
                    "typing" to false,
                    "micMuted" to true,
                    "speaking" to false,
                    "voiceJoined" to false,
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            ref.update(
                mapOf(
                    "displayName" to displayName,
                    "avatarColorHex" to normalizeHex(avatarColorHex),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun updateMyParticipantState(
        roomCode: String,
        typing: Boolean? = null,
        micMuted: Boolean? = null,
        speaking: Boolean? = null,
        voiceJoined: Boolean? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "rooms/$roomCode/participants/$uid"
        return try {
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (typing != null) updates["typing"] = typing
            if (micMuted != null) updates["micMuted"] = micMuted
            if (speaking != null) updates["speaking"] = speaking
            if (voiceJoined != null) updates["voiceJoined"] = voiceJoined
            db.collection("rooms").document(roomCode)
                .collection("participants").document(uid)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- MESSAGES & FLOATING REACTIONS ---

    fun observeMessages(roomCode: String, currentUserId: String): Flow<List<RoomMessage>> = flow {
        if (roomCode.isBlank() || currentUserId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "rooms/$roomCode/messages"
        emitAll(
            db.collection("rooms").document(roomCode)
                .collection("messages")
                .whereArrayContains("memberUids", currentUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(RoomMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val root = extractFirestoreRootError(error) ?: throw error
                    if (auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendMessage(
        roomCode: String,
        senderName: String,
        senderColorHex: String,
        rawText: String,
        messageType: MessageType,
        memberUids: List<String>
    ): Result<Unit> {
        val text = SyncEngine.sanitizeChatText(rawText)
        if (text.isEmpty()) return Result.failure(IllegalArgumentException("Message cannot be empty."))
        var docPath = "rooms/$roomCode/messages"
        return try {
            val uid = requireUserId()
            val safeMembers = (memberUids + uid).distinct().take(30)
            val docRef = db.collection("rooms").document(roomCode)
                .collection("messages").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "senderId" to uid,
                    "senderName" to SyncEngine.sanitizeDisplayName(senderName),
                    "senderColorHex" to normalizeHex(senderColorHex),
                    "text" to text,
                    "messageType" to messageType.name,
                    "memberUids" to safeMembers,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun sendSystemMessage(
        roomCode: String,
        senderName: String,
        senderColorHex: String,
        text: String,
        memberUids: List<String>
    ) {
        runCatching {
            sendMessage(
                roomCode = roomCode,
                senderName = senderName,
                senderColorHex = senderColorHex,
                rawText = text,
                messageType = MessageType.SYSTEM,
                memberUids = memberUids
            )
        }
    }

    // --- ROOM QUEUE (SUGGEST / APPROVE / REORDER / PLAY NEXT) ---

    fun observeQueue(roomCode: String, currentUserId: String): Flow<List<RoomQueueItem>> = flow {
        if (roomCode.isBlank() || currentUserId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "rooms/$roomCode/queue"
        emitAll(
            db.collection("rooms").document(roomCode)
                .collection("queue")
                .whereArrayContains("memberUids", currentUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(RoomQueueItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<RoomQueueItem> { it.approved }
                                .thenBy { it.orderIndex }
                                .thenBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                        )
                }
                .catch { error ->
                    val root = extractFirestoreRootError(error) ?: throw error
                    if (auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addQueueItem(
        room: WatchRoom,
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String,
        suggestedByName: String,
        orderIndex: Int
    ): Result<Unit> {
        var docPath = "rooms/${room.roomCode}/queue"
        return try {
            val uid = requireUserId()
            val isHost = room.hostId == uid
            val safeMembers = (room.memberUids + uid).distinct().take(30)
            val docRef = db.collection("rooms").document(room.roomCode)
                .collection("queue").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "videoId" to videoId.take(11),
                    "title" to title.trim().ifBlank { "YouTube Video" }.take(120),
                    "channelName" to channelName.trim().take(60),
                    "thumbnailUrl" to thumbnailUrl.trim().take(500),
                    "suggestedByUid" to uid,
                    "suggestedByName" to SyncEngine.sanitizeDisplayName(suggestedByName),
                    "approved" to isHost,
                    "orderIndex" to orderIndex.coerceIn(0, 1_000_000),
                    "memberUids" to safeMembers,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateQueueItem(
        roomCode: String,
        itemId: String,
        approved: Boolean? = null,
        orderIndex: Int? = null
    ): Result<Unit> {
        val docPath = "rooms/$roomCode/queue/$itemId"
        return try {
            requireUserId()
            val updates = mutableMapOf<String, Any>()
            if (approved != null) updates["approved"] = approved
            if (orderIndex != null) updates["orderIndex"] = orderIndex.coerceIn(0, 1_000_000)
            if (updates.isEmpty()) return Result.success(Unit)
            db.collection("rooms").document(roomCode)
                .collection("queue").document(itemId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeQueueItem(roomCode: String, itemId: String): Result<Unit> {
        val docPath = "rooms/$roomCode/queue/$itemId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomCode)
                .collection("queue").document(itemId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- LIVE TV CHANNELS ---

    fun observeLiveChannels(includeUnpublishedForAdmin: Boolean): Flow<List<LiveChannel>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "live_channels"
        val query = if (includeUnpublishedForAdmin && isCurrentUserAdmin()) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("published", true)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(LiveChannel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    val root = extractFirestoreRootError(error) ?: throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertLiveChannel(
        channelId: String?,
        name: String,
        category: ChannelCategory,
        language: String,
        streamType: StreamType,
        streamUrl: String,
        broadcaster: String,
        badgeColorHex: String,
        published: Boolean,
        healthStatus: StreamHealth
    ): Result<Unit> {
        var docPath = "live_channels"
        return try {
            val uid = requireUserId()
            val safeName = name.trim().take(60)
            val safeLang = language.trim().ifBlank { "Hindi" }.take(30)
            val safeUrl = streamUrl.trim().take(1000)
            val safeBroadcaster = broadcaster.trim().ifBlank { "Official Broadcaster" }.take(60)
            require(safeName.isNotEmpty() && safeUrl.isNotEmpty()) {
                "Channel name and stream URL are required."
            }

            val docRef = if (channelId.isNullOrBlank()) {
                db.collection("live_channels").document()
            } else {
                db.collection("live_channels").document(channelId)
            }
            docPath = docRef.path

            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "name" to safeName,
                        "category" to category.name,
                        "language" to safeLang,
                        "streamType" to streamType.name,
                        "streamUrl" to safeUrl,
                        "broadcaster" to safeBroadcaster,
                        "badgeColorHex" to normalizeHex(badgeColorHex),
                        "published" to published,
                        "healthStatus" to healthStatus.name,
                        "createdBy" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to safeName,
                        "category" to category.name,
                        "language" to safeLang,
                        "streamType" to streamType.name,
                        "streamUrl" to safeUrl,
                        "broadcaster" to safeBroadcaster,
                        "badgeColorHex" to normalizeHex(badgeColorHex),
                        "published" to published,
                        "healthStatus" to healthStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateChannelHealth(
        channelId: String,
        health: StreamHealth,
        autoHideOffline: Boolean = true
    ): Result<Unit> {
        val docPath = "live_channels/$channelId"
        return try {
            requireUserId()
            val updates = mutableMapOf<String, Any>(
                "healthStatus" to health.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (autoHideOffline) {
                updates["published"] = (health != StreamHealth.OFFLINE)
            }
            db.collection("live_channels").document(channelId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteLiveChannel(channelId: String): Result<Unit> {
        val docPath = "live_channels/$channelId"
        return try {
            requireUserId()
            db.collection("live_channels").document(channelId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedStarterLiveChannels(): Result<Int> {
        var inserted = 0
        return try {
            requireUserId()
            for (ch in CuratedContentCatalog.defaultIndianChannels) {
                val res = upsertLiveChannel(
                    channelId = ch.id,
                    name = ch.name,
                    category = ch.parsedCategory,
                    language = ch.language,
                    streamType = ch.parsedStreamType,
                    streamUrl = ch.streamUrl,
                    broadcaster = ch.broadcaster,
                    badgeColorHex = ch.badgeColorHex,
                    published = ch.published,
                    healthStatus = ch.parsedHealth
                )
                if (res.isSuccess) inserted++
            }
            Result.success(inserted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "live_channels")
            Result.failure(e)
        }
    }

    private fun normalizeHex(raw: String): String {
        val cleaned = raw.trim()
        return if (cleaned.matches(Regex("^#[0-9A-Fa-f]{6}$"))) cleaned else "#FF4D6D"
    }

    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "uljhasawal@gmail.com"
    }
}
