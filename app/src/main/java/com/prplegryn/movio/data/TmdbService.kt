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
    private val searchCache = mutableMapOf<String, TmdbSearchHit?>()
    private val movieDetailsCache = mutableMapOf<Int, TmdbSearchHit>()
    private val tvDetailsCache = mutableMapOf<Int, Pair<TmdbSearchHit, List<TmdbSeason>>>()
    private val seasonEpisodesCache = mutableMapOf<String, List<TmdbEpisode>>()

    val configured: Boolean
        get() = normalizedToken.isNotBlank()

    fun searchBest(parsed: ParsedVideoName): TmdbSearchHit? {
        val cacheKey = "${parsed.title}|${parsed.aliases.joinToString(";")}|${parsed.year}|${parsed.seasonNumber}|${parsed.episodeNumber}|${parsed.tmdbId}|${parsed.imdbId}"
        if (searchCache.containsKey(cacheKey)) return searchCache[cacheKey]
        val hit = searchBestUncached(parsed)
        searchCache[cacheKey] = hit
        return hit
    }

    private fun searchBestUncached(parsed: ParsedVideoName): TmdbSearchHit? {
        if (!configured) return null
        val preferredKind = if (parsed.seasonNumber != null || parsed.episodeNumber != null) MediaKind.Tv else null

        parsed.tmdbId?.let { id ->
            directHit(id, preferredKind ?: MediaKind.Movie)?.let { return it }
            directHit(id, MediaKind.Tv)?.let { return it }
        }
        parsed.imdbId.takeIf { it.isNotBlank() }?.let { imdbId ->
            findByExternalId(imdbId, preferredKind)?.let { return it }
        }

        val queries = parsed.searchTitles().flatMap(::searchQueries).distinct()
        if (queries.isEmpty()) return null
        val hits = queries.flatMap { query ->
            val primary = when (preferredKind) {
                MediaKind.Tv -> searchTv(query)
                else -> searchMovie(query, parsed.year) + searchMovie(query, null)
            }
            val secondary = when (preferredKind) {
                MediaKind.Tv -> searchMovie(query, parsed.year)
                else -> searchTv(query)
            }
            primary + secondary
        }.distinctBy { "${it.kind}:${it.id}" }
        if (hits.isEmpty()) return null
        val candidates = preferredKind?.let { kind -> hits.filter { it.kind == kind }.ifEmpty { hits } } ?: hits
        val scored = candidates.map { hit -> hit to matchScore(parsed, hit, preferredKind) }
        val best = scored.maxByOrNull { it.second } ?: return null
        return best.first.takeIf { best.second >= 5.0 }
    }

    fun movieDetails(hit: TmdbSearchHit): TmdbSearchHit {
        if (!configured || hit.id <= 0) return hit
        movieDetailsCache[hit.id]?.let { return it }
        val detailed = ignoreMissing(hit) {
            val urlBuilder = "https://api.themoviedb.org/3/movie/${hit.id}".toHttpUrl().newBuilder()
                .addQueryParameter("language", "zh-CN")
            applyKey(urlBuilder)
            val json = getJson(urlBuilder.build().toString())
            hit.copy(
                title = json.optString("title", hit.title),
                originalTitle = json.optString("original_title", hit.originalTitle),
                overview = json.optString("overview", hit.overview),
                posterPath = json.optString("poster_path", hit.posterPath),
                backdropPath = json.optString("backdrop_path", hit.backdropPath),
                releaseDate = json.optString("release_date", hit.releaseDate),
                voteAverage = json.optDouble("vote_average", hit.voteAverage),
                genreIds = parseGenreIds(json).ifEmpty { hit.genreIds },
            )
        }
        movieDetailsCache[hit.id] = detailed
        return detailed
    }

    fun tvDetails(hit: TmdbSearchHit): Pair<TmdbSearchHit, List<TmdbSeason>> {
        if (!configured || hit.id <= 0) return hit to emptyList()
        tvDetailsCache[hit.id]?.let { return it }
        val detailed = ignoreMissing(hit to emptyList()) {
            val urlBuilder = "https://api.themoviedb.org/3/tv/${hit.id}".toHttpUrl().newBuilder()
                .addQueryParameter("language", "zh-CN")
            applyKey(urlBuilder)
            val json = getJson(urlBuilder.build().toString())
            val seasons = json.optJSONArray("seasons") ?: JSONArray()
            hit.copy(
                title = json.optString("name", hit.title),
                originalTitle = json.optString("original_name", hit.originalTitle),
                overview = json.optString("overview", hit.overview),
                posterPath = json.optString("poster_path", hit.posterPath),
                backdropPath = json.optString("backdrop_path", hit.backdropPath),
                releaseDate = json.optString("first_air_date", hit.releaseDate),
                voteAverage = json.optDouble("vote_average", hit.voteAverage),
                genreIds = parseGenreIds(json).ifEmpty { hit.genreIds },
            ) to (0 until seasons.length()).mapNotNull { parseSeason(seasons.optJSONObject(it)) }
                .filter { it.seasonNumber > 0 }
        }
        tvDetailsCache[hit.id] = detailed
        return detailed
    }

    fun seasonEpisodes(seriesId: Int, seasonNumber: Int): List<TmdbEpisode> {
        if (!configured || seriesId <= 0 || seasonNumber <= 0) return emptyList()
        val cacheKey = "$seriesId:$seasonNumber"
        seasonEpisodesCache[cacheKey]?.let { return it }
        val parsedEpisodes = ignoreMissing(emptyList()) {
            val urlBuilder = "https://api.themoviedb.org/3/tv/$seriesId/season/$seasonNumber".toHttpUrl().newBuilder()
                .addQueryParameter("language", "zh-CN")
            applyKey(urlBuilder)
            val json = getJson(urlBuilder.build().toString())
            val episodes = json.optJSONArray("episodes") ?: JSONArray()
            (0 until episodes.length()).mapNotNull { parseEpisode(seasonNumber, episodes.optJSONObject(it)) }
        }
        seasonEpisodesCache[cacheKey] = parsedEpisodes
        return parsedEpisodes
    }

    fun imageUrl(path: String, size: String = "w500"): String =
        if (path.isBlank()) "" else "https://image.tmdb.org/t/p/$size$path"

    private fun searchMovie(query: String, year: Int?): List<TmdbSearchHit> {
        val urlBuilder = "https://api.themoviedb.org/3/search/movie".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("language", "zh-CN")
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("page", "1")
        if (year != null) {
            urlBuilder
                .addQueryParameter("year", year.toString())
                .addQueryParameter("primary_release_year", year.toString())
        }
        applyKey(urlBuilder)
        val json = getJson(urlBuilder.build().toString())
        val results = json.optJSONArray("results") ?: JSONArray()
        return (0 until results.length()).mapNotNull { parseMovieHit(results.optJSONObject(it)) }
    }

    private fun searchTv(query: String): List<TmdbSearchHit> {
        val urlBuilder = "https://api.themoviedb.org/3/search/tv".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("language", "zh-CN")
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("page", "1")
        applyKey(urlBuilder)
        val json = getJson(urlBuilder.build().toString())
        val results = json.optJSONArray("results") ?: JSONArray()
        return (0 until results.length()).mapNotNull { parseTvHit(results.optJSONObject(it)) }
    }

    private fun directHit(id: Int, kind: MediaKind): TmdbSearchHit? =
        runCatching {
            when (kind) {
                MediaKind.Movie -> {
                    val urlBuilder = "https://api.themoviedb.org/3/movie/$id".toHttpUrl().newBuilder()
                        .addQueryParameter("language", "zh-CN")
                    applyKey(urlBuilder)
                    parseMovieHit(getJson(urlBuilder.build().toString()))
                }
                MediaKind.Tv -> {
                    val urlBuilder = "https://api.themoviedb.org/3/tv/$id".toHttpUrl().newBuilder()
                        .addQueryParameter("language", "zh-CN")
                    applyKey(urlBuilder)
                    parseTvHit(getJson(urlBuilder.build().toString()))
                }
                MediaKind.Anime -> null
                MediaKind.Unknown -> null
            }
        }.getOrNull()

    private fun findByExternalId(externalId: String, preferredKind: MediaKind?): TmdbSearchHit? {
        val urlBuilder = "https://api.themoviedb.org/3/find/$externalId".toHttpUrl().newBuilder()
            .addQueryParameter("external_source", "imdb_id")
            .addQueryParameter("language", "zh-CN")
        applyKey(urlBuilder)
        val json = getJson(urlBuilder.build().toString())
        val movieHits = json.optJSONArray("movie_results") ?: JSONArray()
        val tvHits = json.optJSONArray("tv_results") ?: JSONArray()
        val hits = (0 until movieHits.length()).mapNotNull { parseMovieHit(movieHits.optJSONObject(it)) } +
            (0 until tvHits.length()).mapNotNull { parseTvHit(tvHits.optJSONObject(it)) }
        return preferredKind?.let { kind -> hits.firstOrNull { it.kind == kind } } ?: hits.firstOrNull()
    }

    private fun parseMovieHit(json: JSONObject?): TmdbSearchHit? {
        if (json == null) return null
        val id = json.optInt("id")
        if (id <= 0) return null
        return TmdbSearchHit(
            id = id,
            kind = MediaKind.Movie,
            title = json.optString("title"),
            originalTitle = json.optString("original_title"),
            overview = json.optString("overview"),
            posterPath = json.optString("poster_path"),
            backdropPath = json.optString("backdrop_path"),
            releaseDate = json.optString("release_date"),
            voteAverage = json.optDouble("vote_average"),
            genreIds = parseGenreIds(json),
        )
    }

    private fun parseTvHit(json: JSONObject?): TmdbSearchHit? {
        if (json == null) return null
        val id = json.optInt("id")
        if (id <= 0) return null
        return TmdbSearchHit(
            id = id,
            kind = MediaKind.Tv,
            title = json.optString("name"),
            originalTitle = json.optString("original_name"),
            overview = json.optString("overview"),
            posterPath = json.optString("poster_path"),
            backdropPath = json.optString("backdrop_path"),
            releaseDate = json.optString("first_air_date"),
            voteAverage = json.optDouble("vote_average"),
            genreIds = parseGenreIds(json),
        )
    }

    private fun parseGenreIds(json: JSONObject): List<Int> {
        val ids = json.optJSONArray("genre_ids")?.let { array ->
            (0 until array.length()).mapNotNull { array.optInt(it).takeIf { id -> id > 0 } }
        }.orEmpty()
        if (ids.isNotEmpty()) return ids
        val genres = json.optJSONArray("genres") ?: return emptyList()
        return (0 until genres.length()).mapNotNull { index ->
            genres.optJSONObject(index)?.optInt("id")?.takeIf { it > 0 }
        }
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
                throw TmdbHttpException(response.code, text)
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
        val queries = parsed.searchTitles().map(::normalizeTitle).filter { it.isNotBlank() }
        val localized = normalizeTitle(hit.title)
        val original = normalizeTitle(hit.originalTitle)
        var score = hit.voteAverage / 10.0
        if (queries.any { localized == it || original == it }) score += 10.0
        if (queries.any {
                it.isNotBlank() &&
                    ((localized.isNotBlank() && localized.contains(it)) || (original.isNotBlank() && original.contains(it)))
            }
        ) {
            score += 6.0
        }
        if (queries.any {
                (localized.isNotBlank() && it.contains(localized)) || (original.isNotBlank() && it.contains(original))
            }
        ) {
            score += 4.0
        }
        score += queries.maxOfOrNull { maxOf(titleSimilarity(it, localized), titleSimilarity(it, original)) }?.times(5.0) ?: 0.0
        if (parsed.year != null && hit.kind == MediaKind.Movie) {
            if (hit.releaseDate.startsWith(parsed.year.toString())) {
                score += 4.0
            } else if (hit.releaseDate.isNotBlank()) {
                score -= 4.0
            }
        }
        val documentaryPattern = Regex("(?i)(makingof|behindthescenes|documentary|featurette)")
        val documentaryQuery = queries.any { documentaryPattern.containsMatchIn(it) }
        val documentaryHit = documentaryPattern.containsMatchIn("$localized $original")
        if (!documentaryQuery && documentaryHit) score -= 5.0
        if (preferredKind != null && hit.kind == preferredKind) score += 3.0
        return score
    }
}

private fun ParsedVideoName.searchTitles(): List<String> =
    (listOf(title) + aliases)
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { normalizeTitle(it).length >= 2 }
        .distinctBy(::normalizeTitle)
        .take(9)

private class TmdbHttpException(
    val httpCode: Int,
    val body: String,
) : IllegalStateException("TMDb 请求失败 $httpCode: $body")

private inline fun <T> ignoreMissing(fallback: T, block: () -> T): T =
    try {
        block()
    } catch (error: TmdbHttpException) {
        if (error.httpCode == 404 || error.body.contains("\"status_code\":34")) {
            fallback
        } else {
            throw error
        }
    }

private fun normalizeTitle(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "")

private fun searchQueries(title: String): List<String> {
    val cleaned = title
        .replace(Regex("[\\[【].*?[\\]】]"), " ")
        .replace(Regex("[（(].*?[）)]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val variants = listOf(
        title.trim(),
        cleaned,
        cleaned.substringBefore(" - ").trim(),
        cleaned.substringBefore("：").trim(),
        cleaned.substringBefore(":").trim(),
    )
    return variants
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { normalizeTitle(it).length >= 2 }
        .distinct()
        .take(4)
}

private fun titleSimilarity(a: String, b: String): Double {
    if (a.isBlank() || b.isBlank()) return 0.0
    if (a == b) return 1.0
    val left = a.bigrams()
    val right = b.bigrams()
    if (left.isEmpty() || right.isEmpty()) return 0.0
    val overlap = left.sumOf { token -> minOf(left.count { it == token }, right.count { it == token }) }
    return (2.0 * overlap) / (left.size + right.size)
}

private fun String.bigrams(): List<String> =
    if (length <= 2) listOf(this) else windowed(2)

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
