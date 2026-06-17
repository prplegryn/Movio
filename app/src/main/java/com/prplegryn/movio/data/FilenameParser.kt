package com.prplegryn.movio.data

private val videoExtension = Regex("\\.(mkv|mp4|m4v|mov|webm|avi|ts|m2ts)$", RegexOption.IGNORE_CASE)
private val numberToken = "[0-9一二三四五六七八九十两]+"
private val seasonEpisodePatterns = listOf(
    Regex("(?i)S\\s*(\\d{1,2})\\s*E\\s*(\\d{1,3})"),
    Regex("(?i)(\\d{1,2})\\s*x\\s*(\\d{1,3})"),
    Regex("第\\s*($numberToken)\\s*季\\s*第\\s*($numberToken)\\s*(?:集|话)"),
)
private val episodeOnlyPatterns = listOf(
    Regex("第\\s*($numberToken)\\s*(?:集|话)"),
    Regex("(?i)\\b(?:EP?|Episode)\\s*0*(\\d{1,3})\\b"),
    Regex("(?i)(?:^|[ ._\\-])0*(\\d{1,3})(?:v\\d)?$"),
)
private val seasonOnlyPatterns = listOf(
    Regex("(?i)\\bS\\s*(\\d{1,2})\\b"),
    Regex("(?i)\\b(?:season|series)\\s*(\\d{1,2})\\b"),
    Regex("第\\s*($numberToken)\\s*季"),
)
private val seasonMarker = Regex("(?i)(?:\\bS\\s*\\d{1,2}\\b|\\b(?:season|series)\\s*\\d{1,2}\\b|第\\s*$numberToken\\s*季)")
private val seasonTitleCutPattern = Regex(
    "(?i)(?:S\\s*\\d{1,2}\\s*(?:E\\s*\\d{1,3})?|\\d{1,2}\\s*x\\s*\\d{1,3}|第\\s*$numberToken\\s*季(?:\\s*第\\s*$numberToken\\s*(?:集|话))?)",
)
private val seasonRangePattern = Regex("第\\s*$numberToken\\s*(?:-|到|至)\\s*$numberToken\\s*季")
private val yearPattern = Regex("(19\\d{2}|20\\d{2})")
private val tmdbIdPattern = Regex("(?i)(?:tmdb(?:id)?|themoviedb)[-_=:\\s]*(\\d+)|\\{tmdb-(\\d+)\\}|\\[tmdb-(\\d+)\\]")
private val imdbIdPattern = Regex("(?i)(tt\\d{6,10})")
private val sitePattern = Regex("(?i)\\bwww\\.[a-z0-9][a-z0-9-]*\\.(?:com|net|org|tv|cc|io|me|cn)\\b")
private val bracketNoisePattern = Regex(
    "(?i)[\\[【(（][^\\]】)）]*(?:字幕|全\\s*\\d{1,3}\\s*集|www\\.|发布|微信|公众号|多音轨|双版本|HDR|杜比|国英|国粤|简繁)[^\\]】)）]*[\\]】)）]",
)
private val releaseNoise = Regex(
    "(?i)(?:来自\\s*[:：]?\\s*云添加|高清(?:剧集网|影视之家)(?:发布)?|首发于高清影视之家|免费公益影视站|不太灵|免费资源关注微信(?:公众)?号\\s*[:：]?\\s*[a-z0-9_\\-]*|微信(?:公众)?号\\s*[:：]?\\s*[a-z0-9_\\-]*|发布|首发于|中文字幕|简繁英字幕|简繁字幕|中英字幕|双语字幕|多音轨|国粤英多音轨|国英多音轨|国粤多音轨|杜比视界双版本|双版本|全\\s*\\d{1,3}\\s*集)",
)
private val cleanupTokens = Regex(
    "(?i)\\b(4320p|2160p|1440p|1080p|720p|480p|8k|4k|uhd|fhd|hdr10plus|hdr10|hdr|sdr|hlg|dv|dovi|dolby[ ._-]*vision|bluray|blu-ray|bdrip|web-dl|webrip|web|hdtv|x264|x265|h[ ._-]*264|h[ ._-]*265|hevc|av1|aac|ac3|dd5[ ._-]*1|ddp5[ ._-]*1|ddp|atmos|truehd\\d*(?:[ ._-]*\\d)?|dts|remux|proper|repack|10bit|8bit|12bit|multi[ ._-]*audio|hdma|nf|dsnp|atvp|kktv|max|itunes|ma|hk|hr|quickio|bathd|dreamhd|batweb|pandaqt|dhtclub|zerotv|minitv|bobo|xiaomi|colortv|blacktv|parktv)\\b",
)

fun parseVideoName(fileName: String, folderPath: String = ""): ParsedVideoName {
    val withoutExtension = fileName.replace(videoExtension, "")
    val pathParts = folderPath.split('/').map { it.trim() }.filter { it.isNotBlank() }
    val folderName = pathParts.lastOrNull().orEmpty()
    val parentFolderName = pathParts.dropLast(1).lastOrNull().orEmpty()
    val libraryFolderName = folderName
        .takeUnless { seasonMarker.containsMatchIn(it) || seasonRangePattern.containsMatchIn(it) }
        ?: parentFolderName
    val source = listOf(withoutExtension, folderName, parentFolderName).filter { it.isNotBlank() }.joinToString(" ")
    val folderSeason = seasonOnlyPatterns.firstNotNullOfOrNull { pattern ->
        pattern.find(source)?.groups?.get(1)?.value.parseMediaNumber()
    }
    val seasonEpisode = seasonEpisodePatterns.firstNotNullOfOrNull { pattern ->
        pattern.find(source)?.let { match ->
            EpisodeMatch(
                marker = match.value,
                season = match.groups[1]?.value.parseMediaNumber(),
                episode = match.groups[2]?.value.parseMediaNumber(),
            )
        }
    } ?: episodeOnlyPatterns.firstNotNullOfOrNull { pattern ->
        pattern.find(withoutExtension)?.let { match ->
            EpisodeMatch(
                marker = match.value,
                season = folderSeason,
                episode = match.groups[1]?.value.parseMediaNumber(),
            )
        }
    } ?: episodeOnlyPatterns.firstNotNullOfOrNull { pattern ->
        pattern.find(source)?.let { match ->
            EpisodeMatch(
                marker = match.value,
                season = folderSeason,
                episode = match.groups[1]?.value.parseMediaNumber(),
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

    val fileCandidates = titleCandidatesFrom(withoutExtension, trimAtYear = seasonEpisode == null)
    val folderCandidates = listOf(libraryFolderName, folderName, parentFolderName)
        .filter { it.isNotBlank() }
        .flatMap { titleCandidatesFrom(it, trimAtYear = false) }
    val useFolderPrimary = episodeOnlyFileName || fileNameIsMostlyEpisodeNumber || fileCandidates.firstOrNull(::isUsefulTitle) == null
    val primaryTitle = if (useFolderPrimary) {
        folderCandidates.firstOrNull(::isUsefulTitle) ?: fileCandidates.firstOrNull(::isUsefulTitle)
    } else {
        fileCandidates.firstOrNull(::isUsefulTitle)
    } ?: cleanTitle(withoutExtension).takeIf(::isUsefulTitle)
    val title = primaryTitle ?: withoutExtension
    val aliases = (fileCandidates + folderCandidates)
        .filter(::isUsefulTitle)
        .distinctBy { normalizeTitleCandidate(it) }
        .filterNot { normalizeTitleCandidate(it) == normalizeTitleCandidate(title) }
        .take(8)

    return ParsedVideoName(
        title = title,
        seasonNumber = seasonEpisode?.season,
        episodeNumber = seasonEpisode?.episode,
        year = year,
        tmdbId = tmdbId,
        imdbId = imdbId,
        aliases = aliases,
    )
}

private fun titleCandidatesFrom(value: String, trimAtYear: Boolean): List<String> {
    val cleaned = cleanTitle(sliceTitleSource(value, trimAtYear))
    if (!isUsefulTitle(cleaned)) return emptyList()
    val chinese = cleaned
        .replace(Regex("[^\\u4e00-\\u9fa5·:：]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val latin = cleaned
        .replace(Regex("[^A-Za-z0-9'&: ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return listOf(cleaned, chinese, latin)
        .map { it.trim(' ', '.', '-', '_', ':', '：', ',', '，') }
        .filter(::isUsefulTitle)
        .distinctBy { normalizeTitleCandidate(it) }
}

private fun sliceTitleSource(value: String, trimAtYear: Boolean): String {
    var end = value.length
    seasonTitleCutPattern.find(value)?.let { end = minOf(end, it.range.first) }
    if (trimAtYear) {
        yearPattern.find(value)?.let { end = minOf(end, it.range.first) }
    }
    return value.substring(0, end)
}

private fun cleanTitle(value: String): String =
    value
        .replace(bracketNoisePattern, " ")
        .replace(sitePattern, " ")
        .replace(tmdbIdPattern, " ")
        .replace(imdbIdPattern, " ")
        .replace(yearPattern, " ")
        .replace(seasonRangePattern, " ")
        .replace(seasonMarker, " ")
        .replace(releaseNoise, " ")
        .replace(cleanupTokens, " ")
        .replace(Regex("^\\s*\\d{2,6}\\s+"), " ")
        .replace(Regex("[._\\[\\]【】()（）-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.', '-', '_', ':', '：', ',', '，')

private fun isUsefulTitle(value: String): Boolean {
    val normalized = normalizeTitleCandidate(value)
    return normalized.length >= 2 && normalized.any { !it.isDigit() } && !value.contains("www", ignoreCase = true)
}

private fun normalizeTitleCandidate(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "")

private fun String?.parseMediaNumber(): Int? {
    val value = this?.trim()?.trimStart('0').orEmpty()
    if (value.isBlank()) return null
    return value.toIntOrNull() ?: chineseNumberToInt(value)
}

private fun chineseNumberToInt(value: String): Int? {
    val digits = mapOf(
        '一' to 1,
        '二' to 2,
        '两' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9,
    )
    if (value == "十") return 10
    val tenIndex = value.indexOf('十')
    if (tenIndex >= 0) {
        val left = value.substring(0, tenIndex).firstOrNull()?.let { digits[it] } ?: 1
        val right = value.substring(tenIndex + 1).firstOrNull()?.let { digits[it] } ?: 0
        return left * 10 + right
    }
    return value.mapNotNull { digits[it] }.takeIf { it.isNotEmpty() }?.fold(0) { acc, item -> acc * 10 + item }
}

private data class EpisodeMatch(
    val marker: String,
    val season: Int?,
    val episode: Int?,
)
