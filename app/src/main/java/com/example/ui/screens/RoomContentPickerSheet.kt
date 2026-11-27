package com.example.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.CuratedContentCatalog
import com.example.data.LiveChannel
import com.example.data.SyncEngine
import com.example.data.YouTubeSearchItem
import com.example.ui.components.GlassCard
import com.example.ui.components.SkeletonCard
import com.example.ui.components.StreamHealthBadge
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomContentPickerSheet(
    liveChannels: List<LiveChannel>,
    isResolvingMedia: Boolean,
    youtubeSearching: Boolean,
    youtubeSearchResults: List<YouTubeSearchItem>,
    youtubeSearchNotice: String?,
    youtubeSearchFromCache: Boolean,
    onSearchYouTube: (String) -> Unit,
    onSelectYouTubeItem: (YouTubeSearchItem) -> Unit,
    onSelectYouTube: (String) -> Unit,
    onSelectDrive: (url: String, title: String) -> Unit,
    onSelectDirectStream: (url: String, title: String) -> Unit,
    onSelectLiveChannel: (LiveChannel) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var youtubeInput by remember { mutableStateOf("") }
    var browsingYouTubeQuery by remember { mutableStateOf<String?>(null) }
    var driveInput by remember { mutableStateOf("") }
    var driveTitle by remember { mutableStateOf("") }
    var directUrl by remember { mutableStateOf("") }
    var directTitle by remember { mutableStateOf("") }

    val isDirectLink = remember(youtubeInput) {
        SyncEngine.parseYouTubeVideoId(youtubeInput) != null ||
            SyncEngine.parseYouTubePlaylistId(youtubeInput) != null
    }

    // Zero-permission Android Photo/Video Picker (Play Policy compliant)
    val localVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectDirectStream(uri.toString(), "Local Device Video Preview")
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Pick Content to Watch Together",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("YouTube") },
                    icon = { Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        browsingYouTubeQuery = null
                        selectedTab = 1
                    },
                    text = { Text("Drive / HLS") },
                    icon = { Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = {
                        browsingYouTubeQuery = null
                        selectedTab = 2
                    },
                    text = { Text("Live TV") },
                    icon = { Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    val activeBrowseQuery = browsingYouTubeQuery
                    if (activeBrowseQuery != null) {
                        KeylessYouTubeBrowsePicker(
                            query = activeBrowseQuery,
                            onBack = { browsingYouTubeQuery = null },
                            onVideoPicked = { videoId ->
                                browsingYouTubeQuery = null
                                onSelectYouTube(videoId)
                                onDismiss()
                            }
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = youtubeInput,
                                onValueChange = {
                                    youtubeInput = it
                                    onSearchYouTube(it)
                                },
                                label = { Text("Search YouTube or paste a link / ID") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        if (isDirectLink) {
                                            onSelectYouTube(youtubeInput)
                                            onDismiss()
                                        } else if (youtubeInput.isNotBlank()) {
                                            browsingYouTubeQuery = youtubeInput.trim()
                                        }
                                    }
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (isDirectLink) {
                                                onSelectYouTube(youtubeInput)
                                                onDismiss()
                                            } else {
                                                browsingYouTubeQuery = youtubeInput.trim()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isDirectLink) Icons.Filled.PlayArrow else Icons.Filled.Search,
                                            contentDescription = if (isDirectLink) "Play link" else "Search YouTube"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("youtube_url_input")
                            )

                            Button(
                                onClick = {
                                    if (isDirectLink) {
                                        onSelectYouTube(youtubeInput)
                                        onDismiss()
                                    } else {
                                        browsingYouTubeQuery = youtubeInput.trim()
                                    }
                                },
                                enabled = !isResolvingMedia,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("load_youtube_button")
                            ) {
                                if (isResolvingMedia) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = if (isDirectLink) Icons.Filled.PlayArrow else Icons.Filled.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            isDirectLink -> "Play on YouTube IFrame"
                                            youtubeInput.isNotBlank() -> "Search YouTube for \"${youtubeInput.trim().take(24)}\""
                                            else -> "Browse YouTube & Tap to Play"
                                        }
                                    )
                                }
                            }

                            if (!youtubeSearchNotice.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = youtubeSearchNotice,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (youtubeSearchResults.isNotEmpty() && youtubeInput.isNotBlank()) {
                                        "Quick Matches"
                                    } else {
                                        "Curated Free Picks"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (youtubeSearchResults.isNotEmpty() && youtubeSearchFromCache && isDirectLink) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = "CACHED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            if (youtubeSearching) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    repeat(3) {
                                        SkeletonCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(74.dp),
                                            cornerRadius = 16.dp
                                        )
                                    }
                                }
                            } else if (youtubeSearchResults.isNotEmpty() && youtubeInput.isNotBlank()) {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 260.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(youtubeSearchResults, key = { it.videoId }) { video ->
                                        YouTubeVideoRowCard(
                                            title = video.title,
                                            subtitle = video.channelTitle,
                                            thumbnailUrl = video.thumbnailUrl,
                                            onClick = {
                                                onSelectYouTubeItem(video)
                                                onDismiss()
                                            }
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 260.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(CuratedContentCatalog.starterYouTubeVideos, key = { it.videoId }) { video ->
                                        YouTubeVideoRowCard(
                                            title = video.title,
                                            subtitle = "${video.channel} • ${video.durationLabel}",
                                            thumbnailUrl = video.thumbnailUrl,
                                            onClick = {
                                                onSelectYouTube(video.videoId)
                                                onDismiss()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Google Drive Video Link",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Paste a Google Drive video link shared as \"Anyone with the link\" to embed and watch in the room.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = driveInput,
                                        onValueChange = { driveInput = it },
                                        label = { Text("https://drive.google.com/file/d/.../view") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = driveTitle,
                                        onValueChange = { driveTitle = it.take(80) },
                                        label = { Text("Video title (optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            onSelectDrive(driveInput, driveTitle)
                                            onDismiss()
                                        },
                                        enabled = driveInput.isNotBlank(),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Load Google Drive Video")
                                    }
                                }
                            }
                        }

                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Direct HLS (.m3u8) / MP4 Stream",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    OutlinedTextField(
                                        value = directUrl,
                                        onValueChange = { directUrl = it },
                                        label = { Text("https://.../stream.m3u8 or .mp4") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = directTitle,
                                        onValueChange = { directTitle = it.take(80) },
                                        label = { Text("Stream title") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                onSelectDirectStream(directUrl, directTitle)
                                                onDismiss()
                                            },
                                            enabled = directUrl.startsWith("http"),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Load Stream")
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                localVideoPicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                )
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Device")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(liveChannels, key = { it.id.ifBlank { it.name } }) { ch ->
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectLiveChannel(ch)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ch.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${ch.broadcaster} • ${ch.language}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StreamHealthBadge(health = ch.parsedHealth)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive keyless YouTube search & video picker.
 * Opens official m.youtube.com search results inside the sheet and captures the 11-char videoId
 * as soon as the user taps any video—requiring zero API keys and zero scraping.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KeylessYouTubeBrowsePicker(
    query: String,
    onBack: () -> Unit,
    onVideoPicked: (String) -> Unit
) {
    val latestOnVideoPicked by rememberUpdatedState(onVideoPicked)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pickedOnce by remember { mutableStateOf(false) }

    val targetUrl = remember(query) {
        if (query.isBlank()) {
            "https://m.youtube.com/"
        } else {
            val encoded = URLEncoder.encode(query, "UTF-8")
            "https://m.youtube.com/results?search_query=$encoded"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back")
            }
            Text(
                text = "Tap any video to sync for room",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(410.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(0xFF0A0B10.toInt())
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        }
                        webViewClient = object : WebViewClient() {
                            private fun maybePickVideo(url: String?): Boolean {
                                if (url.isNullOrBlank() || pickedOnce) return false
                                val id = SyncEngine.parseYouTubeVideoId(url) ?: return false
                                pickedOnce = true
                                stopLoading()
                                latestOnVideoPicked(id)
                                return true
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return maybePickVideo(request?.url?.toString())
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                url: String?,
                                isReload: Boolean
                            ) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                maybePickVideo(url)
                            }
                        }
                        loadUrl(targetUrl)
                        webViewRef = this
                    }
                }
            )
        }
    }
}

@Composable
private fun YouTubeVideoRowCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 92.dp, height = 56.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play video",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
