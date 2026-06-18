package com.prplegryn.movio.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MovioStore(context: Context) {
    private val prefs = context.getSharedPreferences("movio_store", Context.MODE_PRIVATE)

    fun loadSettings(): MovioSettings {
        return MovioSettings(
            guangya = GuangyaSession(
                accessToken = prefs.getString("gy_access", "").orEmpty(),
                refreshToken = prefs.getString("gy_refresh", "").orEmpty(),
                deviceId = prefs.getString("gy_device", "").orEmpty().ifBlank { newDeviceId() },
                phone = prefs.getString("gy_phone", "").orEmpty(),
            ),
            rootId = prefs.getString("root_id", "*").orEmpty().ifBlank { "*" },
            tmdbToken = prefs.getString("tmdb_token", "").orEmpty(),
        )
    }

    fun saveSettings(settings: MovioSettings) {
        prefs.edit()
            .putString("gy_access", settings.guangya.accessToken)
            .putString("gy_refresh", settings.guangya.refreshToken)
            .putString("gy_device", settings.guangya.deviceId.ifBlank { newDeviceId() })
            .putString("gy_phone", settings.guangya.phone)
            .putString("root_id", settings.rootId.ifBlank { "*" })
            .putString("tmdb_token", settings.tmdbToken)
            .apply()
    }

    fun progress(fileId: String): Long {
        val data = JSONObject(prefs.getString("progress", "{}").orEmpty().ifBlank { "{}" })
        return data.optLong(fileId, 0L)
    }

    fun saveProgress(fileId: String, positionMs: Long) {
        val data = JSONObject(prefs.getString("progress", "{}").orEmpty().ifBlank { "{}" })
        data.put(fileId, positionMs.coerceAtLeast(0L))
        prefs.edit().putString("progress", data.toString()).apply()
    }

    fun loadLibraryCache(rootId: String, fingerprint: String): List<MediaGroup>? {
        val raw = prefs.getString(libraryCacheKey(rootId), "").orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.optString("fingerprint") != fingerprint) return null
            if (json.optInt("schemaVersion") != LIBRARY_SCHEMA_VERSION) return null
            val groups = json.optJSONArray("groups") ?: return null
            (0 until groups.length())
                .mapNotNull { groups.optJSONObject(it)?.toMediaGroup() }
                .map { it.withSavedProgress() }
        }.getOrNull()
    }

    fun loadSavedLibraryCache(rootId: String): List<MediaGroup>? {
        val raw = prefs.getString(libraryCacheKey(rootId), "").orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            val groups = JSONObject(raw).optJSONArray("groups") ?: return null
            (0 until groups.length())
                .mapNotNull { groups.optJSONObject(it)?.toMediaGroup() }
                .map { it.withSavedProgress() }
        }.getOrNull()
    }

    fun saveLibraryCache(rootId: String, fingerprint: String, groups: List<MediaGroup>) {
        val json = JSONObject()
            .put("fingerprint", fingerprint)
            .put("schemaVersion", LIBRARY_SCHEMA_VERSION)
            .put(
                "groups",
                JSONArray().also { array ->
                    groups.forEach { array.put(it.toJson()) }
                },
            )
        prefs.edit().putString(libraryCacheKey(rootId), json.toString()).apply()
    }

    private fun newDeviceId(): String =
        UUID.randomUUID().toString().replace("-", "")

    private fun libraryCacheKey(rootId: String): String =
        "library_cache:${rootId.ifBlank { "*" }}"

    private fun MediaGroup.withSavedProgress(): MediaGroup =
        copy(
            episodes = episodes.map { episode -> episode.copy(video = episode.video.withSavedProgress()) },
            movieFile = movieFile?.withSavedProgress(),
            movieFiles = movieFiles.map { it.withSavedProgress() },
            unmatchedFiles = unmatchedFiles.map { it.withSavedProgress() },
        )

    private fun CloudVideo.withSavedProgress(): CloudVideo {
        val saved = progress(id)
        return if (saved > 0L) copy(playProgressMs = saved) else this
    }

    private fun MediaGroup.toJson(): JSONObject =
        JSONObject()
            .put("localTitle", localTitle)
            .put("kind", kind.name)
            .put("tmdb", tmdb?.toJson())
            .putArray("seasons", seasons) { it.toJson() }
            .putArray("episodes", episodes) { it.toJson() }
            .put("movieFile", movieFile?.toJson())
            .putArray("movieFiles", movieFiles) { it.toJson() }
            .putArray("unmatchedFiles", unmatchedFiles) { it.toJson() }

    private fun JSONObject.toMediaGroup(): MediaGroup =
        MediaGroup(
            localTitle = optString("localTitle"),
            kind = optMediaKind("kind"),
            tmdb = optJSONObject("tmdb")?.toTmdbSearchHit(),
            seasons = optJsonArray("seasons") { it.toTmdbSeason() },
            episodes = optJsonArray("episodes") { it.toLibraryEpisode() },
            movieFile = optJSONObject("movieFile")?.toCloudVideo(),
            movieFiles = optJsonArray("movieFiles") { it.toCloudVideo() },
            unmatchedFiles = optJsonArray("unmatchedFiles") { it.toCloudVideo() },
        )

    private fun CloudVideo.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("parentId", parentId)
            .put("folderPath", folderPath)
            .put("size", size)
            .put("durationMs", durationMs)
            .put("playProgressMs", playProgressMs)
            .put("rawCoverUrl", rawCoverUrl)

    private fun JSONObject.toCloudVideo(): CloudVideo =
        CloudVideo(
            id = optString("id"),
            name = optString("name"),
            parentId = optString("parentId"),
            folderPath = optString("folderPath"),
            size = optLong("size"),
            durationMs = optLong("durationMs"),
            playProgressMs = optLong("playProgressMs"),
            rawCoverUrl = optString("rawCoverUrl"),
        )

    private fun ParsedVideoName.toJson(): JSONObject =
        JSONObject()
            .put("title", title)
            .put("seasonNumber", seasonNumber)
            .put("episodeNumber", episodeNumber)
            .put("year", year)
            .put("tmdbId", tmdbId)
            .put("imdbId", imdbId)
            .put("aliases", JSONArray().also { array -> aliases.forEach { array.put(it) } })

    private fun JSONObject.toParsedVideoName(): ParsedVideoName =
        ParsedVideoName(
            title = optString("title"),
            seasonNumber = optNullableInt("seasonNumber"),
            episodeNumber = optNullableInt("episodeNumber"),
            year = optNullableInt("year"),
            tmdbId = optNullableInt("tmdbId"),
            imdbId = optString("imdbId"),
            aliases = optStringArray("aliases"),
        )

    private fun TmdbSearchHit.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("kind", kind.name)
            .put("title", title)
            .put("originalTitle", originalTitle)
            .put("tagline", tagline)
            .put("overview", overview)
            .put("posterPath", posterPath)
            .put("backdropPath", backdropPath)
            .put("releaseDate", releaseDate)
            .put("voteAverage", voteAverage)
            .put("runtime", runtime)
            .put("genreIds", JSONArray().also { array -> genreIds.forEach { array.put(it) } })
            .put("genres", JSONArray().also { array -> genres.forEach { array.put(it) } })
            .putArray("cast", cast) { it.toJson() }

    private fun JSONObject.toTmdbSearchHit(): TmdbSearchHit =
        TmdbSearchHit(
            id = optInt("id"),
            kind = optMediaKind("kind"),
            title = optString("title"),
            originalTitle = optString("originalTitle"),
            tagline = optString("tagline"),
            overview = optString("overview"),
            posterPath = optString("posterPath"),
            backdropPath = optString("backdropPath"),
            releaseDate = optString("releaseDate"),
            voteAverage = optDouble("voteAverage"),
            runtime = optInt("runtime"),
            genreIds = optIntArray("genreIds"),
            genres = optStringArray("genres"),
            cast = optJsonArray("cast") { it.toTmdbCastMember() },
        )

    private fun TmdbCastMember.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("character", character)
            .put("profilePath", profilePath)

    private fun JSONObject.toTmdbCastMember(): TmdbCastMember =
        TmdbCastMember(
            id = optInt("id"),
            name = optString("name"),
            character = optString("character"),
            profilePath = optString("profilePath"),
        )

    private fun TmdbSeason.toJson(): JSONObject =
        JSONObject()
            .put("seasonNumber", seasonNumber)
            .put("name", name)
            .put("posterPath", posterPath)
            .put("overview", overview)

    private fun JSONObject.toTmdbSeason(): TmdbSeason =
        TmdbSeason(
            seasonNumber = optInt("seasonNumber"),
            name = optString("name"),
            posterPath = optString("posterPath"),
            overview = optString("overview"),
        )

    private fun TmdbEpisode.toJson(): JSONObject =
        JSONObject()
            .put("seasonNumber", seasonNumber)
            .put("episodeNumber", episodeNumber)
            .put("title", title)
            .put("overview", overview)
            .put("stillPath", stillPath)
            .put("runtime", runtime)

    private fun JSONObject.toTmdbEpisode(): TmdbEpisode =
        TmdbEpisode(
            seasonNumber = optInt("seasonNumber"),
            episodeNumber = optInt("episodeNumber"),
            title = optString("title"),
            overview = optString("overview"),
            stillPath = optString("stillPath"),
            runtime = optInt("runtime"),
        )

    private fun LibraryEpisode.toJson(): JSONObject =
        JSONObject()
            .put("video", video.toJson())
            .put("parsed", parsed.toJson())
            .put("tmdb", tmdb?.toJson())

    private fun JSONObject.toLibraryEpisode(): LibraryEpisode =
        LibraryEpisode(
            video = optJSONObject("video")?.toCloudVideo() ?: CloudVideo("", ""),
            parsed = optJSONObject("parsed")?.toParsedVideoName() ?: ParsedVideoName(""),
            tmdb = optJSONObject("tmdb")?.toTmdbEpisode(),
        )

    private fun JSONObject.optMediaKind(key: String): MediaKind =
        runCatching { MediaKind.valueOf(optString(key, MediaKind.Unknown.name)) }.getOrDefault(MediaKind.Unknown)

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optStringArray(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }

    private fun JSONObject.optIntArray(key: String): List<Int> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optInt(index).takeIf { it > 0 }
        }
    }

    private fun <T> JSONObject.putArray(key: String, values: List<T>, toJson: (T) -> JSONObject): JSONObject =
        put(
            key,
            JSONArray().also { array ->
                values.forEach { array.put(toJson(it)) }
            },
        )

    private fun <T> JSONObject.optJsonArray(key: String, mapper: (JSONObject) -> T): List<T> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(mapper)
        }
    }

    companion object {
        private const val LIBRARY_SCHEMA_VERSION = 2
    }
}
