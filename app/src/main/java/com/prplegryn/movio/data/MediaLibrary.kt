package com.prplegryn.movio.data

class MediaLibrary(
    private val guangya: GuangyaService,
    private val tmdb: TmdbService,
    private val store: MovioStore,
    private val imageCache: ImageCache? = null,
) {
    suspend fun load(
        rootId: String,
        onProgress: suspend (percent: Int, label: String) -> Unit = { _, _ -> },
    ): List<MediaGroup> {
        onProgress(1, "读取资源列表")
        val videos = guangya.listVideos(rootId).map {
            val saved = store.progress(it.id)
            if (saved > 0L) it.copy(playProgressMs = saved) else it
        }
        val fingerprint = videos.mediaFingerprint()
        store.loadLibraryCache(rootId, fingerprint)?.let {
            prefetchImages(it, 90, 9, onProgress)
            onProgress(100, "已使用本地缓存")
            return it
        }
        if (videos.isEmpty()) return emptyList()

        onProgress(8, "解析媒体文件")
        val parsed = videos.map { it to parseVideoName(it.name, it.folderPath) }
        val grouped = parsed.groupBy { (_, name) -> initialGroupKey(name) }

        val initialGroups = grouped.values.toList()
        val scraped = initialGroups.mapIndexed { index, entries ->
            onProgress(10 + ((index.toFloat() / initialGroups.size.toFloat()) * 68f).toInt(), "搜刮 ${index + 1}/${initialGroups.size}")
            val first = entries.first()
            val firstParsed = first.second
            val hit = tmdb.searchBest(firstParsed)
            ScrapedBucket(
                localTitle = firstParsed.title,
                hit = hit,
                entries = entries,
            )
        }

        onProgress(82, "合并季度与版本")
        val merged = scraped.groupBy { bucket ->
            val hit = bucket.hit
            when {
                hit != null && hit.kind != MediaKind.Unknown -> "${hit.kind}:tmdb:${hit.id}"
                else -> "unknown:${initialGroupKey(bucket.entries.first().second)}"
            }
        }
        val result = merged.values.map { buckets ->
            val hit = buckets.firstNotNullOfOrNull { it.hit }
            val entries = buckets.flatMap { it.entries }
            val title = hit?.title?.takeIf { it.isNotBlank() } ?: buckets.first().localTitle
            when {
                hit?.kind == MediaKind.Tv -> buildTvGroup(title, hit, entries)
                hit?.kind == MediaKind.Movie -> buildMovieGroup(title, hit, entries.map { it.first })
                entries.isLikelyLocalAnime() -> buildAnimeGroup(title, entries)
                else -> buildUnknownGroup(title, entries)
            }
        }.sortedBy { it.displayTitle }
        prefetchImages(result, 86, 13, onProgress)
        store.saveLibraryCache(rootId, fingerprint, result)
        onProgress(100, "同步完成")
        return result
    }

    private suspend fun prefetchImages(
        groups: List<MediaGroup>,
        basePercent: Int,
        span: Int,
        onProgress: suspend (percent: Int, label: String) -> Unit,
    ) {
        val cache = imageCache ?: return
        onProgress(basePercent, "缓存图片资源")
        cache.prefetch(groups) { done, total ->
            val percent = basePercent + ((done.toFloat() / total.toFloat()) * span.toFloat()).toInt()
            onProgress(percent.coerceIn(basePercent, basePercent + span), "缓存图片 $done/$total")
        }
    }

    private fun buildMovieGroup(
        title: String,
        hit: TmdbSearchHit?,
        videos: List<CloudVideo>,
    ): MediaGroup {
        val detailed = hit?.let { tmdb.movieDetails(it) }
        val sortedVideos = videos.sortedWith(compareByDescending<CloudVideo> { dynamicRangeScore(it.name) }.thenByDescending { it.size })
        return MediaGroup(
            localTitle = title,
            kind = MediaKind.Movie,
            tmdb = detailed,
            movieFile = bestDynamicRangeVideo(sortedVideos),
            movieFiles = sortedVideos,
        )
    }

    private fun buildUnknownGroup(
        title: String,
        entries: List<Pair<CloudVideo, ParsedVideoName>>,
    ): MediaGroup {
        return MediaGroup(
            localTitle = title,
            kind = MediaKind.Unknown,
            unmatchedFiles = entries.map { it.first }.sortedBy { it.name },
        )
    }

    private fun buildAnimeGroup(
        title: String,
        entries: List<Pair<CloudVideo, ParsedVideoName>>,
    ): MediaGroup {
        return buildTvGroup(title, null, entries).copy(kind = MediaKind.Anime)
    }

    private fun buildTvGroup(
        title: String,
        hit: TmdbSearchHit?,
        entries: List<Pair<CloudVideo, ParsedVideoName>>,
    ): MediaGroup {
        val details = hit?.let { tmdb.tvDetails(it) }
        val detailedHit = details?.first ?: hit
        val seasons = details?.second.orEmpty()
        val seasonCache = mutableMapOf<Int, List<TmdbEpisode>>()

        val bestEntries = entries
            .groupBy { (video, parsed) ->
                parsed.episodeNumber?.let { "${parsed.seasonNumber ?: 1}:$it" } ?: "file:${video.id}"
            }
            .values
            .map { episodeEntries ->
                episodeEntries.maxWith(compareBy<Pair<CloudVideo, ParsedVideoName>> { dynamicRangeScore(it.first.name) }.thenBy { it.first.size })
            }

        val episodes = bestEntries.map { (video, parsed) ->
            val seasonNumber = parsed.seasonNumber ?: 1
            val episodeNumber = parsed.episodeNumber
            val tmdbEpisode =
                episodeNumber?.let { number ->
                    detailedHit?.takeIf { it.kind == MediaKind.Tv }?.let {
                        seasonCache.getOrPut(seasonNumber) {
                            tmdb.seasonEpisodes(it.id, seasonNumber)
                        }.firstOrNull { ep -> ep.episodeNumber == number }
                    }
                }
            LibraryEpisode(video = video, parsed = parsed, tmdb = tmdbEpisode)
        }.sortedWith(compareBy({ it.parsed.seasonNumber ?: 1 }, { it.parsed.episodeNumber ?: Int.MAX_VALUE }, { it.video.name }))

        return MediaGroup(
            localTitle = title,
            kind = MediaKind.Tv,
            tmdb = detailedHit,
            seasons = seasons,
            episodes = episodes,
        )
    }
}

private fun List<Pair<CloudVideo, ParsedVideoName>>.isLikelyLocalAnime(): Boolean {
    if (none { it.second.episodeNumber != null }) return false
    val text = joinToString(" ") { (video, parsed) ->
        (listOf(parsed.title) + parsed.aliases + listOf(video.name, video.folderPath)).joinToString(" ")
    }.lowercase()
    return listOf("anime", "animation", "donghua", "folktales", "mukashi", "neko", "动漫", "动画", "番剧", "猫")
        .any { text.contains(it) }
}

private fun List<CloudVideo>.mediaFingerprint(): String =
    sortedBy { it.id }
        .joinToString("|") { "${it.id}:${it.name}:${it.size}:${it.durationMs}:${it.folderPath}" }

private data class ScrapedBucket(
    val localTitle: String,
    val hit: TmdbSearchHit?,
    val entries: List<Pair<CloudVideo, ParsedVideoName>>,
)

private fun initialGroupKey(name: ParsedVideoName): String {
    val isTv = name.seasonNumber != null || name.episodeNumber != null
    val providerKey = name.tmdbId?.let { "tmdb:$it" }
        ?: name.imdbId.takeIf { it.isNotBlank() }?.let { "imdb:$it" }
    val normalizedTitle = name.title.lowercase()
        .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "")
    return "${if (isTv) "tv" else "movie"}:${providerKey ?: "$normalizedTitle:${name.year ?: ""}"}"
}
