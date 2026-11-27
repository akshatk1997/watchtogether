package com.example.data

data class CuratedVideo(
    val videoId: String,
    val title: String,
    val channel: String,
    val category: String,
    val durationLabel: String
) {
    val thumbnailUrl: String
        get() = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
}

object CuratedContentCatalog {

    val quickReactions = listOf("🔥", "😂", "❤️", "👏", "😮", "🎉")

    val playbackSpeeds = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0)

    /**
     * Curated free, official, legal Indian Live TV channels & public broadcaster streams
     * (Prasar Bharati / Doordarshan, Sansad TV, official free YouTube Live channels & public HLS).
     * Never includes pirated or paywalled streams.
     */
    val defaultIndianChannels: List<LiveChannel> = listOf(
        LiveChannel(
            id = "dd_news_hindi",
            name = "DD News",
            category = ChannelCategory.NEWS.name,
            language = "Hindi",
            streamType = StreamType.HLS.name,
            streamUrl = "https://d3eyhgoylams0m.cloudfront.net/v1/master/93ce20f0f52760bf38be911ff4c91ed02aa2fd92/ed7bd2c7-8d10-4051-b397-2f6b90f99acb/0aebd013-8696-4579-9620-5435525d1858/0/stream.m3u8",
            broadcaster = "Prasar Bharati • Doordarshan",
            badgeColorHex = "#FF4D6D",
            published = true,
            healthStatus = StreamHealth.UNKNOWN.name
        ),
        LiveChannel(
            id = "dd_india_english",
            name = "DD India",
            category = ChannelCategory.NEWS.name,
            language = "English",
            streamType = StreamType.HLS.name,
            streamUrl = "https://d3eyhgoylams0m.cloudfront.net/v1/master/93ce20f0f52760bf38be911ff4c91ed02aa2fd92/ed7bd2c7-8d10-4051-b397-2f6b90f99acb/2e9e32a4-c4f7-49c3-96d6-c4e3660c7e3f/2/stream.m3u8",
            broadcaster = "Prasar Bharati • International",
            badgeColorHex = "#2EC4B6",
            published = true,
            healthStatus = StreamHealth.UNKNOWN.name
        ),
        LiveChannel(
            id = "sansad_tv_1",
            name = "Sansad TV 1",
            category = ChannelCategory.NEWS.name,
            language = "Hindi",
            streamType = StreamType.YOUTUBE_LIVE.name,
            streamUrl = "jfKfPfyJRdk",
            broadcaster = "Parliament of India • Official",
            badgeColorHex = "#FFB703",
            published = true,
            healthStatus = StreamHealth.ONLINE.name
        ),
        LiveChannel(
            id = "dd_national",
            name = "DD National",
            category = ChannelCategory.ENTERTAINMENT.name,
            language = "Hindi",
            streamType = StreamType.HLS.name,
            streamUrl = "https://d3eyhgoylams0m.cloudfront.net/v1/master/93ce20f0f52760bf38be911ff4c91ed02aa2fd92/ed7bd2c7-8d10-4051-b397-2f6b90f99acb/2e9e32a4-c4f7-49c3-96d6-c4e3660c7e3f/0/stream.m3u8",
            broadcaster = "Prasar Bharati • Doordarshan",
            badgeColorHex = "#F97316",
            published = true,
            healthStatus = StreamHealth.UNKNOWN.name
        ),
        LiveChannel(
            id = "dd_bharati",
            name = "DD Bharati",
            category = ChannelCategory.ENTERTAINMENT.name,
            language = "Hindi",
            streamType = StreamType.HLS.name,
            streamUrl = "https://d3eyhgoylams0m.cloudfront.net/v1/master/93ce20f0f52760bf38be911ff4c91ed02aa2fd92/ed7bd2c7-8d10-4051-b397-2f6b90f99acb/2e9e32a4-c4f7-49c3-96d6-c4e3660c7e3f/1/stream.m3u8",
            broadcaster = "Prasar Bharati • Arts & Culture",
            badgeColorHex = "#6366F1",
            published = true,
            healthStatus = StreamHealth.UNKNOWN.name
        ),
        LiveChannel(
            id = "akashvani_music",
            name = "Lofi & Indie Radio Live",
            category = ChannelCategory.MUSIC.name,
            language = "English",
            streamType = StreamType.YOUTUBE_LIVE.name,
            streamUrl = "jfKfPfyJRdk",
            broadcaster = "Free YouTube Live Stream",
            badgeColorHex = "#EC4899",
            published = true,
            healthStatus = StreamHealth.ONLINE.name
        ),
        LiveChannel(
            id = "dd_sahyadri",
            name = "DD Sahyadri",
            category = ChannelCategory.REGIONAL.name,
            language = "Marathi",
            streamType = StreamType.HLS.name,
            streamUrl = "https://d3eyhgoylams0m.cloudfront.net/v1/master/93ce20f0f52760bf38be911ff4c91ed02aa2fd92/ed7bd2c7-8d10-4051-b397-2f6b90f99acb/534632b8-7627-436b-9f9b-317a48a40180/0/stream.m3u8",
            broadcaster = "Doordarshan Maharashtra",
            badgeColorHex = "#10B981",
            published = true,
            healthStatus = StreamHealth.UNKNOWN.name
        ),
        LiveChannel(
            id = "dd_podhigai",
            name = "DD Tamil (Podhigai)",
            category = ChannelCategory.REGIONAL.name,
            language = "Tamil",
            streamType = StreamType.HLS.name,
            streamUrl = "https://d3eyhgoylams0m.cloudfront.net/v1/master/93ce20f0f52760bf38be911ff4c91ed02aa2fd92/ed7bd2c7-8d10-4051-b397-2f6b90f99acb/0aebd013-8696-4579-9620-5435525d1858/1/stream.m3u8",
            broadcaster = "Doordarshan Tamil Nadu",
            badgeColorHex = "#06B6D4",
            published = true,
            healthStatus = StreamHealth.UNKNOWN.name
        ),
        LiveChannel(
            id = "dd_bangla",
            name = "DD Bangla",
            category = ChannelCategory.REGIONAL.name,
            language = "Bengali",
            streamType = StreamType.HLS.name,
            streamUrl = "https://d3eyhgoylams0m.cloudfront.net/v1/master/93ce20f0f52760bf38be911ff4c91ed02aa2fd92/ed7bd2c7-8d10-4051-b397-2f6b90f99acb/534632b8-7627-436b-9f9b-317a48a40180/1/stream.m3u8",
            broadcaster = "Doordarshan Kolkata",
            badgeColorHex = "#8B5CF6",
            published = true,
            healthStatus = StreamHealth.UNKNOWN.name
        ),
        LiveChannel(
            id = "pm_evidya_kids",
            name = "Open Cinema & Kids HLS",
            category = ChannelCategory.KIDS.name,
            language = "English",
            streamType = StreamType.HLS.name,
            streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            broadcaster = "Open Creative Commons Stream",
            badgeColorHex = "#22C55E",
            published = true,
            healthStatus = StreamHealth.ONLINE.name
        ),
        LiveChannel(
            id = "prasar_heritage_devotional",
            name = "Peaceful Ragas & Devotional",
            category = ChannelCategory.DEVOTIONAL.name,
            language = "Hindi",
            streamType = StreamType.YOUTUBE_LIVE.name,
            streamUrl = "4xDzrJKXOOY",
            broadcaster = "Official Live Ambient Channel",
            badgeColorHex = "#F59E0B",
            published = true,
            healthStatus = StreamHealth.ONLINE.name
        )
    )

    val starterYouTubeVideos: List<CuratedVideo> = listOf(
        CuratedVideo(
            videoId = "jfKfPfyJRdk",
            title = "Lofi Hip Hop Radio — Beats to Relax / Study",
            channel = "Lofi Girl",
            category = "Music",
            durationLabel = "LIVE"
        ),
        CuratedVideo(
            videoId = "aqz-KE-bpKQ",
            title = "Big Buck Bunny 60fps 4K — Open Movie",
            channel = "Blender Foundation",
            category = "Cinema",
            durationLabel = "10:34"
        ),
        CuratedVideo(
            videoId = "LXb3EKWsInQ",
            title = "COSTA RICA IN 4K 60fps HDR (Ultra HD)",
            channel = "Jacob + Katie Schwarz",
            category = "Nature 4K",
            durationLabel = "05:13"
        ),
        CuratedVideo(
            videoId = "4xDzrJKXOOY",
            title = "Synthwave Radio — Chill / Retro Cinema",
            channel = "Lofi Girl",
            category = "Music",
            durationLabel = "LIVE"
        ),
        CuratedVideo(
            videoId = "YE7VzlLtp-4",
            title = "Sintel — Open Source Animated Short Film",
            channel = "Blender Studio",
            category = "Cinema",
            durationLabel = "14:48"
        )
    )
}
