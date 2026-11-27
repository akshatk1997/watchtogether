package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ChannelCategory
import com.example.data.LiveChannel
import com.example.data.StreamHealth
import com.example.data.StreamType
import com.example.data.UiState
import com.example.ui.components.GlassCard
import com.example.ui.components.StreamHealthBadge
import com.example.ui.theme.AvatarPaletteHex
import com.example.ui.theme.parseHexColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    channelsState: UiState<List<LiveChannel>>,
    localHealthOverrides: Map<String, StreamHealth>,
    healthCheckRunning: Boolean,
    isAdmin: Boolean,
    onWatchChannel: (LiveChannel) -> Unit,
    onRunHealthCheck: () -> Unit,
    onSeedChannels: () -> Unit,
    onSaveChannel: (
        channelId: String?,
        name: String,
        category: ChannelCategory,
        language: String,
        streamType: StreamType,
        streamUrl: String,
        broadcaster: String,
        badgeColorHex: String,
        published: Boolean
    ) -> Unit,
    onDeleteChannel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by rememberSaveable { mutableStateOf<String>("ALL") }
    var selectedLanguage by rememberSaveable { mutableStateOf<String>("All") }
    var showAddDialog by remember { mutableStateOf(false) }

    val languages = listOf("All", "Hindi", "English", "Marathi", "Tamil", "Bengali")

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live TV Hero Banner
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_livetv_banner_1795726991293),
                            contentDescription = "Indian Live TV banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xE80A0B10),
                                            Color(0xA60A0B10),
                                            Color(0x280A0B10)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "FREE • LEGAL • OFFICIAL STREAMS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Indian Live TV",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Prasar Bharati, Doordarshan & open HLS streams",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Stream Health & Admin Action Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onRunHealthCheck,
                        enabled = !healthCheckRunning,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("run_health_check_button")
                    ) {
                        if (healthCheckRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking…")
                        } else {
                            Icon(
                                imageVector = Icons.Filled.HealthAndSafety,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.check_stream_health))
                        }
                    }

                    if (isAdmin) {
                        FilledTonalButton(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("add_channel_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_channel))
                        }
                    }
                }
            }

            if (isAdmin) {
                item {
                    FilledTonalButton(
                        onClick = onSeedChannels,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("seed_channels_button")
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Admin: Sync Starter Official Channels to Firestore")
                    }
                }
            }

            // Category Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == "ALL",
                            onClick = { selectedCategory = "ALL" },
                            label = { Text("All Genres") },
                            modifier = Modifier.testTag("category_chip_all")
                        )
                    }
                    items(ChannelCategory.entries) { category ->
                        FilterChip(
                            selected = selectedCategory == category.name,
                            onClick = { selectedCategory = category.name },
                            label = { Text(category.label) },
                            modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                        )
                    }
                }
            }

            // Language Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(languages) { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { selectedLanguage = lang },
                            label = { Text(lang) }
                        )
                    }
                }
            }

            // Channel List
            when (channelsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = channelsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    val filtered = channelsState.data.filter { ch ->
                        val catMatch = selectedCategory == "ALL" || ch.category == selectedCategory
                        val langMatch = selectedLanguage == "All" ||
                            ch.language.equals(selectedLanguage, ignoreCase = true)
                        catMatch && langMatch
                    }

                    if (filtered.isEmpty()) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LiveTv,
                                        contentDescription = "No channels",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No channels match these filters",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Try switching to All Genres or All languages.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id.ifBlank { it.name } }) { channel ->
                            val effectiveHealth = localHealthOverrides[channel.id] ?: channel.parsedHealth
                            LiveChannelCard(
                                channel = channel,
                                effectiveHealth = effectiveHealth,
                                canDelete = isAdmin && channel.createdBy.isNotBlank(),
                                onWatch = { onWatchChannel(channel) },
                                onDelete = { onDeleteChannel(channel.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddChannelDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, cat, lang, type, url, broadcaster, published ->
                onSaveChannel(
                    null,
                    name,
                    cat,
                    lang,
                    type,
                    url,
                    broadcaster,
                    AvatarPaletteHex.random(),
                    published
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun LiveChannelCard(
    channel: LiveChannel,
    effectiveHealth: StreamHealth,
    canDelete: Boolean,
    onWatch: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = parseHexColor(channel.badgeColorHex)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("channel_card_${channel.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LiveTv,
                    contentDescription = channel.name,
                    tint = accent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    StreamHealthBadge(health = effectiveHealth)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${channel.broadcaster} • ${channel.language} • ${channel.parsedStreamType.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!channel.published) {
                    Text(
                        text = "Hidden (dead/unpublished)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove channel",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Surface(
                onClick = onWatch,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Watch ${channel.name} in party",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddChannelDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        category: ChannelCategory,
        language: String,
        streamType: StreamType,
        streamUrl: String,
        broadcaster: String,
        published: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var broadcaster by remember { mutableStateOf("Prasar Bharati") }
    var language by remember { mutableStateOf("Hindi") }
    var streamUrl by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ChannelCategory.NEWS) }
    var selectedType by remember { mutableStateOf(StreamType.HLS) }
    var published by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Free Legal Live TV Channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Channel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = broadcaster,
                    onValueChange = { broadcaster = it.take(60) },
                    label = { Text("Official Broadcaster") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it.take(30) },
                    label = { Text("Language") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StreamType.entries.forEach { st ->
                        FilterChip(
                            selected = selectedType == st,
                            onClick = { selectedType = st },
                            label = { Text(st.label) }
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ChannelCategory.entries) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it.take(1000) },
                    label = {
                        Text(
                            if (selectedType == StreamType.HLS) "Public HLS .m3u8 URL"
                            else "YouTube Live Video ID or URL"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Published to users", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = published, onCheckedChange = { published = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, selectedCategory, language, selectedType, streamUrl, broadcaster, published)
                },
                enabled = name.isNotBlank() && streamUrl.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
