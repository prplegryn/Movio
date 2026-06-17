package com.prplegryn.movio.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TmdbService(
    private val token: String,
) {
    private val normalizedToken = token.cleanTmdbCredential()
    private val useApiKey = Regex("^[a-fA-F0-9]{32}$").matches(normalizedToken)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val configured: Boolean
        get() = normalizedToken.isNotBlank()

    fun searchBest(parsed: ParsedVideoName): TmdbSearchHit? {
        if (!configured || normalizeTitle(parsed.title).length < 2) return null
        val urlBuilder = "https://api.themoviedb.org/3/search/multi".toHttpUrl().newBuilder()
            .addQueryParameter("query", parsed.title)
            .addQueryParameter("language", "zh-CN")
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("page", "1")
        applyKey(urlBuilder)
        val json = getJson(urlBuilder.build().toString())
        val results = json.optJSONArray("results") ?: JSONArray()
        val hits = (0 until results.length())
            .mapNotNull { parseSearchHit(results.optJSONObject(it)) }
            .filter { it.kind == MediaKind.Movie || it.kind == MediaKind.Tv }
        if (hits.isEmpty()) return null
        val preferredKind = if (parsed.seasonNumber != null || parsed.episodeNumber != null) MediaKind.Tv else null
        val candidates = preferredKind?.let { kind ->
            hits.filter { it.kind == kind }.ifEmpty { hits }
        } ?: hits
        val scored = candidates.map { hit -> hit to matchScore(parsed, hit, preferredKind) }
        val best = scored.maxByOrNull { it.second } ?: return null
        return best.first.takeIf { best.second >= 5.5 }
    }

    fun movieDetails(hit: TmdbSearchHit): TmdbSearchHit {
        if (!configured) return hit
        val urlBuilder = "https://api.themoviedb.org/3/movie/${hit.id}".toHttpUrl().newBuilder()
            .addQueryParameter("language", "zh-CN")
        applyKey(urlBuilder)
        val json = getJson(urlBuilder.build().toString())
        return hit.copy(
            title = json.optString("title", hit.title),
            originalTitle = json.optString("original_title", hit.originalTitle),
            overview = json.optString("overview", hit.overview),
            posterPath = json.optString("poster_path", hit.posterPath),
            backdropPath = json.optString("backdrop_path", hit.backdropPath),
            releaseDate = json.optString("release_date", hit.releaseDate),
            voteAverage = json.optDouble("vote_average", hit.voteAverage),
        )
    }

    fun tvDetails(hit: TmdbSearchHit): Pair<TmdbSearchHit, List<TmdbSeason>> {
        if (!configured) return hit to emptyList()
        val urlBuilder = "https://api.themoviedb.org/3/tv/${hit.id}".toHttpUrl().newBuilder()
            .addQueryParameter("language", "zh-CN")
        applyKey(urlBuilder)
        val json = getJson(urlBuilder.build().toString())
        val seasons = json.optJSONArray("seasons") ?: JSONArray()
        return hit.copy(
            title = json.optString("name", hit.title),
            originalTitle = json.optString("original_name", hit.originalTitle),
            overview = json.optString("overview", hit.overview),
            posterPath = json.optString("poster_path", hit.posterPath),
            backdropPath = json.optString("backdrop_path", hit.backdropPath),
            releaseDate = json.optString("first_air_date", hit.releaseDate),
            voteAverage = json.optDouble("vote_average", hit.voteAverage),
        ) to (0 until seasons.length()).mapNotNull { parseSeason(seasons.optJSONObject(it)) }
            .filter { it.seasonNumber > 0 }
    }

    fun seasonEpisodes(seriesId: Int, seasonNumber: Int): List<TmdbEpisode> {
        if (!configured) return emptyList()
        val urlBuilder = "https://api.themoviedb.org/3/tv/$seriesId/season/$seasonNumber".toHttpUrl().newBuilder()
            .addQueryParameter("language", "zh-CN")
        applyKey(urlBuilder)
        val json = getJson(urlBuilder.build().toString())
        val episodes = json.optJSONArray("episodes") ?: JSONArray()
        return (0 until episodes.length()).mapNotNull { parseEpisode(seasonNumber, episodes.optJSONObject(it)) }
    }

    fun imageUrl(path: String, size: String = "w500"): String =
        if (path.isBlank()) "" else "https://image.tmdb.org/t/p/$size$path"

    private fun parseSearchHit(json: JSONObject?): TmdbSearchHit? {
        if (json == null) return null
        val mediaType = json.optString("media_type")
        val kind = when (mediaType) {
            "movie" -> MediaKind.Movie
            "tv" -> MediaKind.Tv
            else -> return null
        }
        return TmdbSearchHit(
            id = json.optInt("id"),
            kind = kind,
            title = if (kind == MediaKind.Movie) json.optString("title") else json.optString("name"),
            originalTitle = if (kind == MediaKind.Movie) json.optString("original_title") else json.optString("original_name"),
            overview = json.optString("overview"),
            posterPath = json.optString("poster_path"),
            backdropPath = json.optString("backdrop_path"),
            releaseDate = if (kind == MediaKind.Movie) json.optString("release_date") else json.optString("first_air_date"),
            voteAverage = json.optDouble("vote_average"),
        )
    }

    private fun parseSeason(json: JSONObject?): TmdbSeason? {
        if (json == null) return null
        return TmdbSeason(
            seasonNumber = json.optInt("season_number"),
            name = json.optString("name").ifBlank { "第 ${json.optInt("season_number")} 季" },
            posterPath = json.optString("poster_path"),
            overview = json.optString("overview"),
        )
    }

    private fun parseEpisode(seasonNumber: Int, json: JSONObject?): TmdbEpisode? {
        if (json == null) return null
        return TmdbEpisode(
            seasonNumber = seasonNumber,
            episodeNumber = json.optInt("episode_number"),
            title = json.optString("name").ifBlank { "第 ${json.optInt("episode_number")} 集" },
            overview = json.optString("overview"),
            stillPath = json.optString("still_path"),
            runtime = json.optInt("runtime"),
        )
    }

    private fun getJson(url: String): JSONObject {
        val builder = Request.Builder().url(url)
            .header("accept", "application/json")
        if (configured && !useApiKey) {
            builder.header("Authorization", "Bearer $normalizedToken")
        }
        client.newCall(builder.get().build()).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                error("TMDb 请求失败 ${response.code}: $text")
            }
            return JSONObject(text.ifBlank { "{}" })
        }
    }

    private fun applyKey(builder: okhttp3.HttpUrl.Builder) {
        if (configured && useApiKey) {
            builder.addQueryParameter("api_key", normalizedToken)
        }
    }

    private fun matchScore(
        parsed: ParsedVideoName,
        hit: TmdbSearchHit,
        preferredKind: MediaKind?,
    ): Double {
        val query = normalizeTitle(parsed.title)
        val localized = normalizeTitle(hit.title)
        val original = normalizeTitle(hit.originalTitle)
        var score = hit.voteAverage / 10.0
        if (localized == query || original == query) score += 10.0
        if ((localized.isNotBlank() && localized.contains(query)) ||
            (original.isNotBlank() && original.contains(query))
        ) {
            score += 6.0
        }
        if ((localized.isNotBlank() && query.contains(localized)) ||
            (original.isNotBlank() && query.contains(original))
        ) {
            score += 4.0
        }
        if (parsed.year != null && hit.releaseDate.startsWith(parsed.year.toString())) score += 4.0
        if (preferredKind != null && hit.kind == preferredKind) score += 3.0
        return score
    }
}

private fun normalizeTitle(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "")

private fun String.cleanTmdbCredential(): String {
    return trim()
        .removePrefix("Authorization:")
        .removePrefix("authorization:")
        .trim()
        .removePrefix("Bearer")
        .removePrefix("bearer")
        .trim()
        .trim('"', '\'')
        .replace(Regex("\\s+"), "")
}
