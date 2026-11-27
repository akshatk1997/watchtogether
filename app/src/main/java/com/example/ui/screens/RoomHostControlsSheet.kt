package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.ParticipantRole
import com.example.data.RoomParticipant
import com.example.data.WatchRoom
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatarBadge
import com.example.ui.theme.LocalWatchTogetherExtras

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomHostControlsSheet(
    room: WatchRoom,
    participants: List<RoomParticipant>,
    currentUserId: String,
    onUpdateSettings: (everyoneCanControl: Boolean, roomLocked: Boolean, waitForEveryone: Boolean) -> Unit,
    onToggleMuteGuest: (String) -> Unit,
    onKickGuest: (String) -> Unit,
    onEndRoom: () -> Unit,
    onDismiss: () -> Unit
) {
    val isHost = room.hostId == currentUserId
    val extras = LocalWatchTogetherExtras.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isHost) "Host Controls & Participants" else "Participants (${participants.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isHost) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Let everyone control", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Guests can play, pause, seek & switch media",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = room.everyoneCanControl,
                                onCheckedChange = { checked ->
                                    onUpdateSettings(checked, room.roomLocked, room.waitForEveryone)
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.HourglassTop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Wait for everyone", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Pause the room if a viewer buffers over 3s (off by default)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = room.waitForEveryone,
                                onCheckedChange = { checked ->
                                    onUpdateSettings(room.everyoneCanControl, room.roomLocked, checked)
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Lock room", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Block new guests from joining via code",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = room.roomLocked,
                                onCheckedChange = { checked ->
                                    onUpdateSettings(room.everyoneCanControl, checked, room.waitForEveryone)
                                }
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(participants, key = { it.userId }) { p ->
                    val isGuestMuted = p.userId in room.mutedUids
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatarBadge(
                                displayName = p.displayName,
                                colorHex = p.avatarColorHex,
                                size = 38.dp,
                                speaking = p.speaking && !p.micMuted
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = p.displayName + if (p.userId == currentUserId) " (You)" else "",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (p.parsedRole == ParticipantRole.HOST) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = "Host",
                                            tint = extras.hostGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = buildString {
                                        append(if (p.parsedRole == ParticipantRole.HOST) "Host" else "Guest")
                                        if (p.voiceJoined) {
                                            append(if (p.micMuted) " • Voice (Muted)" else " • Voice Live")
                                        }
                                        if (isGuestMuted) append(" • Chat Muted")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (p.voiceJoined) {
                                Icon(
                                    imageVector = if (p.micMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                    contentDescription = if (p.micMuted) "Mic muted" else "Mic active",
                                    tint = if (p.micMuted) MaterialTheme.colorScheme.onSurfaceVariant else extras.syncCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (isHost && p.userId != currentUserId) {
                                IconButton(onClick = { onToggleMuteGuest(p.userId) }) {
                                    Icon(
                                        imageVector = if (isGuestMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                        contentDescription = if (isGuestMuted) "Unmute guest" else "Mute guest",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                IconButton(onClick = { onKickGuest(p.userId) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Block,
                                        contentDescription = "Kick guest",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isHost) {
                Spacer(modifier = Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = {
                        onEndRoom()
                        onDismiss()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("End Watch Party for Everyone")
                }
            }
        }
    }
}
