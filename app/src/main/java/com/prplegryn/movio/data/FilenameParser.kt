package com.prplegryn.movio.data

private val videoExtension = Regex("\\.(mkv|mp4|m4v|mov|webm|avi|ts|m2ts)$", RegexOption.IGNORE_CASE)
private val seasonEpisodePatterns = listOf(
    Regex("(?i)S(\\d{1,2})\\s*E(\\d{1,3})"),
    Regex("(?i)(\\d{1,2})\\s*x\\s*(\\d{1,3})"),
    Regex("第\\s*(\\d{1,2})\\s*季\\s*第\\s*(\\d{1,3})\\s*(?:集|话)"),
)
private val episodeOnlyPatterns = listOf(
    Regex("第\\s*(\\d{1,3})\\s*(?:集|话)"),
    Regex("(?i)\\b(?:EP?|Episode)\\s*0*(\\d{1,3})\\b"),
    Regex("(?i)(?:^|[ ._\\-])0*(\\d{1,3})(?:v\\d)?$"),
)
private val seasonOnlyPatterns = listOf(
    Regex("(?i)\\bS(\\d{1,2})\\b"),
    Regex("(?i)\\b(?:season|series)\\s*(\\d{1,2})\\b"),
    Regex("第\\s*(\\d{1,2})\\s*季"),
)
private val seasonMarker = Regex("(?i)(?:\\bS\\s*\\d{1,2}\\b|\\b(?:season|series)\\s*\\d{1,2}\\b|第\\s*\\d{1,2}\\s*季)")
private val yearPattern = Regex("(19\\d{2}|20\\d{2})")
private val tmdbIdPattern = Regex("(?i)(?:tmdb(?:id)?|themoviedb)[-_=:\\s]*(\\d+)|\\{tmdb-(\\d+)\\}|\\[tmdb-(\\d+)\\]")
private val imdbIdPattern = Regex("(?i)(tt\\d{6,10})")
private val cleanupTokens = Regex(
    "(?i)\\b(4320p|2160p|1440p|1080p|720p|480p|8k|4k|uhd|fhd|hdr10plus|hdr10|hdr|sdr|hlg|dv|dovi|dolby[ ._-]*vision|bluray|blu-ray|bdrip|web-dl|webrip|hdtv|x264|x265|h264|h265|hevc|av1|aac|ddp|atmos|truehd|dts|remux|proper|repack)\\b"
)

fun parseVideoName(fileName: String, folderPath: String = ""): ParsedVideoName {
    val withoutExtension = fileName.replace(videoExtension, "")
    val pathParts = folderPath.split('/').map { it.trim() }.filter { it.isNotBlank() }
    val folderName = pathParts.lastOrNull().orEmpty()
    val parentFolderName = pathParts.dropLast(1).lastOrNull().orEmpty()
    val libraryFolderName = folderName
        .takeUnless { seasonMarker.containsMatchIn(it) }
        ?: parentFolderName
    val source = listOf(withoutExtension, folderName, parentFolderName).filter { it.isNotBlank() }.joinToString(" ")
    val folderSeason = seasonOnlyPatterns.firstNotNullOfOrNull { pattern ->
        pattern.find(source)?.groups?.get(1)?.value?.toIntOrNull()
    }
    val seasonEpisode = seasonEpisodePatterns.firstNotNullOfOrNull { pattern ->
        pattern.find(source)?.let { match ->
            EpisodeMatch(
                marker = match.value,
                season = match.groups[1]?.value?.toIntOrNull(),
                episode = match.groups[2]?.value?.toIntOrNull(),
            )
        }
    } ?: episodeOnlyPatterns.firstNotNullOfOrNull { pattern ->
        pattern.find(withoutExtension)?.let { match ->
            EpisodeMatch(
                marker = match.value,
                season = folderSeason,
                episode = match.groups[1]?.value?.toIntOrNull(),
            )
        }
    } ?: episodeOnlyPatterns.firstNotNullOfOrNull { pattern ->
        pattern.find(source)?.let { match ->
            EpisodeMatch(
                marker = match.value,
                season = folderSeason,
                episode = match.groups[1]?.value?.toIntOrNull(),
            )
        }
    }
    val tmdbId = tmdbIdPattern.find(source)?.groups?.drop(1)?.firstOrNull { it?.value?.isNotBlank() == true }?.value?.toIntOrNull()
    val imdbId = imdbIdPattern.find(source)?.value.orEmpty()
    val year = yearPattern.find(source)?.value?.toIntOrNull()
    val episodeOnlyFileName = seasonEpisode?.episode != null && seasonEpisode.season == null
    val fileNameIsMostlyEpisodeNumber = Regex("(?i)^(?:s\\d{1,2}e)?\\d{1,3}(?:v\\d)?$").matches(
        withoutExtension.replace(Regex("[ ._\\-]+"), ""),
    )
    val rawTitleSource = libraryFolderName
        .takeIf {
            it.isNotBlank() &&
                (episodeOnlyFileName || fileNameIsMostlyEpisodeNumber || yearPattern.containsMatchIn(it) || tmdbIdPattern.containsMatchIn(it) || imdbIdPattern.containsMatchIn(it))
        }
        ?: withoutExtension
    val title = rawTitleSource
        .substringBefore(seasonEpisode?.marker ?: "###")
        .replace(tmdbIdPattern, " ")
        .replace(imdbIdPattern, " ")
        .replace(yearPattern, " ")
        .replace(seasonMarker, " ")
        .replace(cleanupTokens, " ")
        .replace(Regex("[._\\[\\]【】()（）-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank {
            withoutExtension
                .replace(tmdbIdPattern, " ")
                .replace(imdbIdPattern, " ")
                .replace(yearPattern, " ")
                .replace(cleanupTokens, " ")
                .replace(Regex("[._\\[\\]【】()（）-]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

    return ParsedVideoName(
        title = title.ifBlank { withoutExtension },
        seasonNumber = seasonEpisode?.season,
        episodeNumber = seasonEpisode?.episode,
        year = year,
        tmdbId = tmdbId,
        imdbId = imdbId,
    )
}

private data class EpisodeMatch(
    val marker: String,
    val season: Int?,
    val episode: Int?,
)
