package com.prplegryn.movio.data

enum class MediaKind {
    Movie,
    Tv,
    Anime,
    Unknown,
}

data class GuangyaSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val deviceId: String = "",
    val phone: String = "",
)

data class MovioSettings(
    val guangya: GuangyaSession = GuangyaSession(),
    val rootId: String = "*",
    val tmdbToken: String = "",
)

data class CloudVideo(
    val id: String,
    val name: String,
    val parentId: String = "",
    val folderPath: String = "",
    val size: Long = 0L,
    val durationMs: Long = 0L,
    val playProgressMs: Long = 0L,
    val rawCoverUrl: String = "",
)

data class CloudFolder(
    val id: String,
    val name: String,
    val parentId: String = "",
)

data class ParsedVideoName(
    val title: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val year: Int? = null,
    val tmdbId: Int? = null,
    val imdbId: String = "",
    val aliases: List<String> = emptyList(),
)

data class TmdbSearchHit(
    val id: Int,
    val kind: MediaKind,
    val title: String,
    val originalTitle: String = "",
    val overview: String = "",
    val posterPath: String = "",
    val backdropPath: String = "",
    val releaseDate: String = "",
    val voteAverage: Double = 0.0,
    val genreIds: List<Int> = emptyList(),
)

data class TmdbSeason(
    val seasonNumber: Int,
    val name: String,
    val posterPath: String = "",
    val overview: String = "",
)

data class TmdbEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String = "",
    val stillPath: String = "",
    val runtime: Int = 0,
)

data class LibraryEpisode(
    val video: CloudVideo,
    val parsed: ParsedVideoName,
    val tmdb: TmdbEpisode? = null,
)

data class MediaGroup(
    val localTitle: String,
    val kind: MediaKind,
    val tmdb: TmdbSearchHit? = null,
    val seasons: List<TmdbSeason> = emptyList(),
    val episodes: List<LibraryEpisode> = emptyList(),
    val movieFile: CloudVideo? = null,
    val movieFiles: List<CloudVideo> = emptyList(),
    val unmatchedFiles: List<CloudVideo> = emptyList(),
) {
    val displayTitle: String
        get() = tmdb?.title?.takeIf { it.isNotBlank() } ?: localTitle

    val primaryPosterPath: String
        get() = tmdb?.posterPath.orEmpty()

    val primaryBackdropPath: String
        get() = tmdb?.backdropPath.orEmpty()

    val isAnimation: Boolean
        get() = kind == MediaKind.Anime || (kind == MediaKind.Tv && tmdb?.genreIds?.contains(16) == true)
}

data class SubtitleTrackInfo(
    val index: Int,
    val label: String,
    val language: String,
    val selected: Boolean,
)
