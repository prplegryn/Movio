package com.prplegryn.movio.data

private val videoExtension = Regex("\\.(mkv|mp4|m4v|mov|webm|avi|ts|m2ts)$", RegexOption.IGNORE_CASE)
private val seasonEpisode = Regex("(?i)(?:S(\\d{1,2})\\s*E(\\d{1,3})|第\\s*(\\d{1,2})\\s*季\\s*第\\s*(\\d{1,3})\\s*(?:集|话))")
private val yearPattern = Regex("(19\\d{2}|20\\d{2})")
private val cleanupTokens = Regex(
    "(?i)\\b(2160p|1080p|720p|480p|4k|uhd|hdr|dv|bluray|blu-ray|web-dl|webrip|hdtv|x264|x265|hevc|aac|ddp|atmos|remux)\\b"
)

fun parseVideoName(fileName: String): ParsedVideoName {
    val withoutExtension = fileName.replace(videoExtension, "")
    val se = seasonEpisode.find(withoutExtension)
    val season = se?.groups?.get(1)?.value?.toIntOrNull() ?: se?.groups?.get(3)?.value?.toIntOrNull()
    val episode = se?.groups?.get(2)?.value?.toIntOrNull() ?: se?.groups?.get(4)?.value?.toIntOrNull()
    val year = yearPattern.find(withoutExtension)?.value?.toIntOrNull()
    val title = withoutExtension
        .substringBefore(se?.value ?: "###")
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
        seasonNumber = season,
        episodeNumber = episode,
        year = year,
    )
}
