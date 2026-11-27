package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Pluggable YouTube content search interface.
 * - Default: [PasteOnlyProvider] (zero API key required, keyless oEmbed + curated catalog).
 * - Optional: [YouTubeDataApiProvider] (enabled only when YOUTUBE_API_KEY is supplied, with 24h cache
 *   and automatic fallback on missing key or quota exhaustion).
 */
interface SearchProvider {
    val providerName: String
    val supportsKeywordSearch: Boolean
    suspend fun search(query: String): YouTubeSearchOutcome
}

/**
 * Default zero-key SearchProvider:
 * Resolves any pasted YouTube URL/ID via keyless oEmbed, filters curated embeddable picks,
 * and clearly reports when keyword search requires pasting a link or using the in-app YouTube browser.
 */
class PasteOnlyProvider(
    private val mediaLookupService: MediaLookupService
) : SearchProvider {

    override val providerName: String = "PasteOnlyProvider"
    override val supportsKeywordSearch: Boolean = false

    override suspend fun search(query: String): YouTubeSearchOutcome {
        val trimmed = query.trim().take(120)
        if (trimmed.isEmpty()) {
            return YouTubeSearchOutcome.Success(
                items = CuratedContentCatalog.starterYouTubeVideos.map {
                    YouTubeSearchItem(it.videoId, it.title, it.channel, it.thumbnailUrl)
                },
                fromCache = true,
                usingCuratedFallback = true
            )
        }

        val directId = SyncEngine.parseYouTubeVideoId(trimmed)
        if (directId != null) {
            val meta = mediaLookupService.fetchYouTubeMetadata(directId)
            return YouTubeSearchOutcome.Success(
                items = listOf(
                    YouTubeSearchItem(
                        videoId = meta.videoId,
                        title = meta.title,
                        channelTitle = meta.authorName,
                        thumbnailUrl = meta.thumbnailUrl
                    )
                ),
                fromCache = false
            )
        }

        val filtered = CuratedContentCatalog.starterYouTubeVideos
            .filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.channel.contains(trimmed, ignoreCase = true) ||
                    it.category.contains(trimmed, ignoreCase = true)
            }
            .map { YouTubeSearchItem(it.videoId, it.title, it.channel, it.thumbnailUrl) }

        return YouTubeSearchOutcome.Success(
            items = filtered,
            fromCache = true,
            usingCuratedFallback = true,
            notice = "Keyword API search unavailable (no API key) — paste a YouTube link or tap Browse YouTube."
        )
    }
}

private data class CachedProviderSearch(
    val items: List<YouTubeSearchItem>,
    val cachedAtMs: Long
)

/**
 * Optional YouTube Data API v3 SearchProvider.
 * Enabled ONLY when a non-empty YOUTUBE_API_KEY is configured.
 * Caches search results for 24 hours to minimize quota usage, and falls back gracefully
 * to [PasteOnlyProvider] whenever the key is missing or quota is exceeded.
 */
class YouTubeDataApiProvider(
    private val apiKey: String,
    private val mediaLookupService: MediaLookupService,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val remoteFetcher: (suspend (query: String, key: String) -> YouTubeApiFetchResult)? = null
) : SearchProvider {

    private val fallbackProvider = PasteOnlyProvider(mediaLookupService)
    private val cache = ConcurrentHashMap<String, CachedProviderSearch>()
    @Volatile
    private var quotaExceededUntilMs: Long = 0L

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    override val providerName: String = "YouTubeDataApiProvider"
    override val supportsKeywordSearch: Boolean
        get() = apiKey.isNotBlank() && clock() >= quotaExceededUntilMs

    override suspend fun search(query: String): YouTubeSearchOutcome {
        val trimmed = query.trim().take(120)
        if (trimmed.isEmpty() || SyncEngine.parseYouTubeVideoId(trimmed) != null) {
            return fallbackProvider.search(trimmed)
        }

        if (apiKey.isBlank()) {
            return fallbackProvider.search(trimmed)
        }

        val now = clock()
        val normalizedKey = trimmed.lowercase()
        val cached = cache[normalizedKey]
        if (cached != null && (now - cached.cachedAtMs) < CACHE_TTL_24H_MS) {
            return YouTubeSearchOutcome.Success(
                items = cached.items,
                fromCache = true
            )
        }

        if (now < quotaExceededUntilMs) {
            return fallbackWithQuotaNotice(trimmed)
        }

        val result = if (remoteFetcher != null) {
            remoteFetcher.invoke(trimmed, apiKey)
        } else {
            fetchFromYouTubeDataApi(trimmed, apiKey)
        }

        return when (result) {
            is YouTubeApiFetchResult.Items -> {
                if (cache.size >= MAX_CACHE_ENTRIES) {
                    cache.clear()
                }
                cache[normalizedKey] = CachedProviderSearch(result.items, now)
                YouTubeSearchOutcome.Success(items = result.items, fromCache = false)
            }
            is YouTubeApiFetchResult.QuotaExceeded -> {
                quotaExceededUntilMs = now + QUOTA_COOLDOWN_MS
                fallbackWithQuotaNotice(trimmed)
            }
            is YouTubeApiFetchResult.Failure -> {
                YouTubeSearchOutcome.Error(result.message)
            }
        }
    }

    private suspend fun fallbackWithQuotaNotice(query: String): YouTubeSearchOutcome {
        val fb = fallbackProvider.search(query)
        return if (fb is YouTubeSearchOutcome.Success) {
            fb.copy(notice = "Search unavailable (YouTube API quota exceeded) — paste a YouTube link or browse YouTube.")
        } else {
            YouTubeSearchOutcome.Error("Search unavailable — paste a YouTube link.")
        }
    }

    private suspend fun fetchFromYouTubeDataApi(query: String, key: String): YouTubeApiFetchResult =
        withContext(Dispatchers.IO) {
            try {
                val encodedQ = URLEncoder.encode(query, "UTF-8")
                val encodedK = URLEncoder.encode(key, "UTF-8")
                val url =
                    "https://www.googleapis.com/youtube/v3/search" +
                        "?part=snippet&type=video&videoEmbeddable=true&safeSearch=moderate&maxResults=10" +
                        "&q=$encodedQ&key=$encodedK"
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(req).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.code == 403 || response.code == 429) {
                        return@withContext YouTubeApiFetchResult.QuotaExceeded
                    }
                    if (!response.isSuccessful) {
                        return@withContext YouTubeApiFetchResult.Failure(
                            "Search unavailable (${response.code}) — paste a YouTube link."
                        )
                    }
                    val root = JSONObject(body)
                    val itemsArray = root.optJSONArray("items")
                    val results = mutableListOf<YouTubeSearchItem>()
                    if (itemsArray != null) {
                        for (i in 0 until itemsArray.length()) {
                            val item = itemsArray.optJSONObject(i) ?: continue
                            val idObj = item.optJSONObject("id") ?: continue
                            val videoId = idObj.optString("videoId").trim()
                            if (videoId.length != 11) continue
                            val snippet = item.optJSONObject("snippet") ?: continue
                            val title = decodeEntities(snippet.optString("title", "YouTube Video")).take(120)
                            val channel = decodeEntities(snippet.optString("channelTitle", "YouTube")).take(60)
                            val thumb = snippet.optJSONObject("thumbnails")
                                ?.optJSONObject("medium")
                                ?.optString("url")
                                ?.takeIf { it.isNotBlank() }
                                ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                            results.add(YouTubeSearchItem(videoId, title, channel, thumb))
                        }
                    }
                    YouTubeApiFetchResult.Items(results)
                }
            } catch (_: Exception) {
                YouTubeApiFetchResult.Failure("Search unavailable — check your connection or paste a YouTube link.")
            }
        }

    private fun decodeEntities(raw: String): String =
        raw.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

    companion object {
        const val CACHE_TTL_24H_MS = 24 * 60 * 60 * 1000L
        const val QUOTA_COOLDOWN_MS = 60 * 60 * 1000L
        private const val MAX_CACHE_ENTRIES = 120
    }
}

sealed interface YouTubeApiFetchResult {
    data class Items(val items: List<YouTubeSearchItem>) : YouTubeApiFetchResult
    data object QuotaExceeded : YouTubeApiFetchResult
    data class Failure(val message: String) : YouTubeApiFetchResult
}

object SearchProviderFactory {
    fun create(
        mediaLookupService: MediaLookupService,
        optionalApiKey: String = BuildConfig.OPTIONAL_YOUTUBE_API_KEY
    ): SearchProvider {
        val cleanKey = optionalApiKey.trim()
        return if (cleanKey.isNotEmpty() && cleanKey != "MY_YOUTUBE_API_KEY") {
            YouTubeDataApiProvider(apiKey = cleanKey, mediaLookupService = mediaLookupService)
        } else {
            PasteOnlyProvider(mediaLookupService = mediaLookupService)
        }
    }
}
