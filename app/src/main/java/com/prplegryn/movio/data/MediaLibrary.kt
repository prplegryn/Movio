package com.prplegryn.movio.data

class MediaLibrary(
    private val guangya: GuangyaService,
    private val tmdb: TmdbService,
    private val store: MovioStore,
) {
    fun load(rootId: String): List<MediaGroup> {
        val videos = guangya.listVideos(rootId).map {
            val saved = store.progress(it.id)
            if (saved > 0L) it.copy(playProgressMs = saved) else it
        }
        if (videos.isEmpty()) return emptyList()

        val parsed = videos.map { it to parseVideoName(it.name) }
        val grouped = parsed.groupBy { (_, name) ->
            val isTv = name.seasonNumber != null || name.episodeNumber != null
            "${if (isTv) "tv" else "movie"}:${name.title.lowercase()}"
        }

        return grouped.values.map { entries ->
            val first = entries.first()
            val firstParsed = first.second
            val hit = tmdb.searchBest(firstParsed)
            val inferredKind =
                if (firstParsed.seasonNumber != null || firstParsed.episodeNumber != null) {
                    MediaKind.Tv
                } else {
                    MediaKind.Movie
                }
            when (hit?.kind ?: inferredKind) {
                MediaKind.Tv -> buildTvGroup(firstParsed.title, hit, entries)
                MediaKind.Movie -> buildMovieGroup(firstParsed.title, hit, entries.first().first)
                MediaKind.Unknown -> MediaGroup(localTitle = firstParsed.title, kind = MediaKind.Unknown)
            }
        }.sortedBy { it.displayTitle }
    }

    private fun buildMovieGroup(
        title: String,
        hit: TmdbSearchHit?,
        video: CloudVideo,
    ): MediaGroup {
        val detailed = hit?.let { tmdb.movieDetails(it) }
        return MediaGroup(
            localTitle = title,
            kind = MediaKind.Movie,
            tmdb = detailed,
            movieFile = video,
        )
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

        val episodes = entries.map { (video, parsed) ->
            val seasonNumber = parsed.seasonNumber ?: 1
            val episodeNumber = parsed.episodeNumber ?: 1
            val tmdbEpisode =
                detailedHit?.takeIf { it.kind == MediaKind.Tv }?.let {
                    seasonCache.getOrPut(seasonNumber) {
                        tmdb.seasonEpisodes(it.id, seasonNumber)
                    }.firstOrNull { ep -> ep.episodeNumber == episodeNumber }
                }
            LibraryEpisode(video = video, parsed = parsed, tmdb = tmdbEpisode)
        }.sortedWith(compareBy({ it.parsed.seasonNumber ?: 1 }, { it.parsed.episodeNumber ?: 1 }, { it.video.name }))

        return MediaGroup(
            localTitle = title,
            kind = MediaKind.Tv,
            tmdb = detailedHit,
            seasons = seasons,
            episodes = episodes,
        )
    }
}
