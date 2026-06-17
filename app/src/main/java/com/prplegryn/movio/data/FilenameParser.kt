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
)
private val yearPattern = Regex("(19\\d{2}|20\\d{2})")
private val cleanupTokens = Regex(
    "(?i)\\b(2160p|1080p|720p|480p|4k|uhd|hdr|dv|bluray|blu-ray|web-dl|webrip|hdtv|x264|x265|hevc|aac|ddp|atmos|remux)\\b"
)

fun parseVideoName(fileName: String): ParsedVideoName {
    val withoutExtension = fileName.replace(videoExtension, "")
    val seasonEpisode = seasonEpisodePatterns.firstNotNullOfOrNull { pattern ->
        pattern.find(withoutExtension)?.let { match ->
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
                season = null,
                episode = match.groups[1]?.value?.toIntOrNull(),
            )
        }
    }
    val year = yearPattern.find(withoutExtension)?.value?.toIntOrNull()
    val title = withoutExtension
        .substringBefore(seasonEpisode?.marker ?: "###")
        .replace(yearPattern, " ")
        .replace(cleanupTokens, " ")
        .replace(Regex("[._\\[\\]【】()（）-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank {
            withoutExtension
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
    )
}

private data class EpisodeMatch(
    val marker: String,
    val season: Int?,
    val episode: Int?,
)
