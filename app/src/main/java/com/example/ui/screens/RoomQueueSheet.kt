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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.RoomQueueItem
import com.example.data.WatchRoom
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalWatchTogetherExtras

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomQueueSheet(
    room: WatchRoom,
    queueItems: List<RoomQueueItem>,
    currentUserId: String,
    isResolving: Boolean,
    onSuggestVideo: (String) -> Unit,
    onApprove: (RoomQueueItem) -> Unit,
    onMoveItem: (RoomQueueItem, Boolean) -> Unit,
    onPlayNow: (RoomQueueItem) -> Unit,
    onRemove: (RoomQueueItem) -> Unit,
    onDismiss: () -> Unit
) {
    val isHost = room.hostId == currentUserId
    val extras = LocalWatchTogetherExtras.current
    var urlInput by remember { mutableStateOf("") }

    val approvedItems = remember(queueItems) {
        queueItems.filter { it.approved }.sortedBy { it.orderIndex }
    }
    val pendingItems = remember(queueItems) {
        queueItems.filter { !it.approved }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Room Queue (${approvedItems.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (pendingItems.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = extras.hostGold.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${pendingItems.size} pending",
                            style = MaterialTheme.typography.labelSmall,
                            color = extras.hostGold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Any participant can suggest a YouTube link; host additions are auto-approved
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text(if (isHost) "Add YouTube link to queue" else "Suggest a YouTube link") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("queue_suggest_input")
                )
                Button(
                    onClick = {
                        onSuggestVideo(urlInput)
                        urlInput = ""
                    },
                    enabled = urlInput.isNotBlank() && !isResolving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("queue_suggest_button")
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Add, contentDescription = "Add to queue")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pendingItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "Guest Suggestions (Awaiting Host Approval)",
                            style = MaterialTheme.typography.labelLarge,
                            color = extras.hostGold
                        )
                    }
                    itemsIndexed(pendingItems, key = { _, item -> "pending_${item.id}" }) { _, item ->
                        QueueItemCard(
                            item = item,
                            isHost = isHost,
                            canMoveUp = false,
                            canMoveDown = false,
                            isOwnSuggestion = item.suggestedByUid == currentUserId,
                            onApprove = { onApprove(item) },
                            onMoveUp = {},
                            onMoveDown = {},
                            onPlayNow = { onPlayNow(item) },
                            onRemove = { onRemove(item) }
                        )
                    }
                }

                item {
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (approvedItems.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Queue is empty. Paste a YouTube link above to line up the next video for everyone.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(approvedItems, key = { _, item -> "approved_${item.id}" }) { index, item ->
                        QueueItemCard(
                            item = item,
                            isHost = isHost,
                            canMoveUp = index > 0,
                            canMoveDown = index < approvedItems.lastIndex,
                            isOwnSuggestion = item.suggestedByUid == currentUserId,
                            onApprove = {},
                            onMoveUp = { onMoveItem(item, true) },
                            onMoveDown = { onMoveItem(item, false) },
                            onPlayNow = {
                                onPlayNow(item)
                                onDismiss()
                            },
                            onRemove = { onRemove(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    item: RoomQueueItem,
    isHost: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isOwnSuggestion: Boolean,
    onApprove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPlayNow: () -> Unit,
    onRemove: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 76.dp, height = 46.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.channelName.ifBlank { "YouTube" }} • by ${item.suggestedByName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isHost && !item.approved) {
                IconButton(onClick = onApprove) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Approve suggestion",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (isHost && item.approved) {
                if (canMoveUp) {
                    IconButton(onClick = onMoveUp) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                    }
                }
                if (canMoveDown) {
                    IconButton(onClick = onMoveDown) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onPlayNow) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play next now",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (isHost || isOwnSuggestion) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove from queue",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
