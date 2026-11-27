package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class YouTubeMetadata(
    val videoId: String,
    val title: String,
    val authorName: String,
    val thumbnailUrl: String
)

data class YouTubeSearchItem(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String
)

sealed interface YouTubeSearchOutcome {
    data class Success(
        val items: List<YouTubeSearchItem>,
        val fromCache: Boolean,
        val usingCuratedFallback: Boolean = false,
        val notice: String? = null
    ) : YouTubeSearchOutcome

    data class Error(val message: String) : YouTubeSearchOutcome
}

private data class CachedMetadataEntry(
    val metadata: YouTubeMetadata,
    val cachedAtMs: Long
)

class MediaLookupService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // In-memory TTL cache for keyless YouTube oEmbed lookups (15 min TTL)
    private val metadataCache = ConcurrentHashMap<String, CachedMetadataEntry>()

    /**
     * Keyless YouTube lookup (zero API key required):
     * - If the query is a YouTube link or 11-character ID, resolves official metadata via YouTube oEmbed.
     * - Otherwise filters curated starter picks locally; full YouTube search is handled via the
     *   in-app interactive YouTube browser picker without requiring any API key.
     */
    suspend fun searchYouTubeVideos(rawQuery: String): YouTubeSearchOutcome = withContext(Dispatchers.IO) {
        val query = rawQuery.trim().take(100)
        if (query.isEmpty()) {
            return@withContext YouTubeSearchOutcome.Success(
                items = CuratedContentCatalog.starterYouTubeVideos.map {
                    YouTubeSearchItem(it.videoId, it.title, it.channel, it.thumbnailUrl)
                },
                fromCache = true,
                usingCuratedFallback = true
            )
        }

        val directId = SyncEngine.parseYouTubeVideoId(query)
        if (directId != null) {
            val wasCached = metadataCache.containsKey(directId)
            val meta = fetchYouTubeMetadata(directId)
            return@withContext YouTubeSearchOutcome.Success(
                items = listOf(
                    YouTubeSearchItem(
                        videoId = meta.videoId,
                        title = meta.title,
                        channelTitle = meta.authorName,
                        thumbnailUrl = meta.thumbnailUrl
                    )
                ),
                fromCache = wasCached
            )
        }

        val filtered = CuratedContentCatalog.starterYouTubeVideos
            .filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.channel.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
            }
            .map { YouTubeSearchItem(it.videoId, it.title, it.channel, it.thumbnailUrl) }

        YouTubeSearchOutcome.Success(
            items = filtered,
            fromCache = true,
            usingCuratedFallback = true
        )
    }

    /**
     * Fetches official video title, channel name, and thumbnail via YouTube's public oEmbed API
     * (no API key required, no scraping), with in-memory caching.
     */
    suspend fun fetchYouTubeMetadata(videoId: String): YouTubeMetadata = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = metadataCache[videoId]
        if (cached != null && (now - cached.cachedAtMs) < METADATA_CACHE_TTL_MS) {
            return@withContext cached.metadata
        }

        val fallback = YouTubeMetadata(
            videoId = videoId,
            title = "YouTube Video ($videoId)",
            authorName = "YouTube",
            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        )
        try {
            val encodedWatchUrl = URLEncoder.encode(
                "https://www.youtube.com/watch?v=$videoId",
                "UTF-8"
            )
            val request = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encodedWatchUrl&format=json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext fallback
                val body = response.body?.string() ?: return@withContext fallback
                val json = JSONObject(body)
                val resolved = YouTubeMetadata(
                    videoId = videoId,
                    title = json.optString("title", fallback.title).take(120),
                    authorName = json.optString("author_name", "YouTube").take(60),
                    thumbnailUrl = json.optString("thumbnail_url", fallback.thumbnailUrl).take(500)
                )
                if (metadataCache.size >= MAX_CACHE_ENTRIES) {
                    metadataCache.clear()
                }
                metadataCache[videoId] = CachedMetadataEntry(resolved, now)
                resolved
            }
        } catch (_: Exception) {
            fallback
        }
    }

    /**
     * Performs a real network health check on a Live TV channel stream URL.
     * - For HLS streams: sends an HTTP GET probe checking status 2xx and latency.
     * - For YouTube Live IDs: checks official YouTube oEmbed reachability.
     */
    suspend fun checkChannelHealth(channel: LiveChannel): StreamHealth = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val probeUrl = when (channel.parsedStreamType) {
                StreamType.HLS -> channel.streamUrl
                StreamType.YOUTUBE_LIVE -> {
                    val ytId = SyncEngine.parseYouTubeVideoId(channel.streamUrl) ?: channel.streamUrl
                    val encoded = URLEncoder.encode("https://www.youtube.com/watch?v=$ytId", "UTF-8")
                    "https://www.youtube.com/oembed?url=$encoded&format=json"
                }
            }
            if (!probeUrl.startsWith("http://") && !probeUrl.startsWith("https://")) {
                return@withContext StreamHealth.OFFLINE
            }
            val request = Request.Builder()
                .url(probeUrl)
                .header("User-Agent", "WatchTogetherAndroid/1.0")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startMs
                when {
                    response.isSuccessful && elapsed < 2500L -> StreamHealth.ONLINE
                    response.isSuccessful -> StreamHealth.DEGRADED
                    response.code in 400..499 || response.code >= 500 -> StreamHealth.OFFLINE
                    else -> StreamHealth.DEGRADED
                }
            }
        } catch (_: Exception) {
            StreamHealth.OFFLINE
        }
    }

    /**
     * Performs a lightweight ping/pong round-trip against Google's public edge HTTP Date header
     * to compute an NTP-style clock offset sample (used every 30s by ClockOffsetEstimator).
     */
    suspend fun measureServerClockSample(): ClockSyncSample? = withContext(Dispatchers.IO) {
        try {
            val t0 = System.currentTimeMillis()
            val request = Request.Builder()
                .url("https://www.youtube.com/oembed?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Daqz-KE-bpKQ&format=json")
                .head()
                .build()
            client.newCall(request).execute().use { response ->
                val t3 = System.currentTimeMillis()
                val serverDate = response.headers.getDate("Date") ?: return@withContext null
                val serverTimeMs = serverDate.time
                val rtt = (t3 - t0).coerceAtLeast(0L)
                // HTTP Date has 1-second resolution; midpoint offset protects against large clock skew
                val offset = serverTimeMs - (t0 + rtt / 2L)
                ClockSyncSample(
                    offsetMs = offset,
                    roundTripMs = rtt,
                    measuredAtMs = t3
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val METADATA_CACHE_TTL_MS = 15 * 60 * 1000L
        private const val MAX_CACHE_ENTRIES = 100
    }
}
