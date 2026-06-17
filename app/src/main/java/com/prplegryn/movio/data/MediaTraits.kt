package com.prplegryn.movio.data

private data class DynamicRangePattern(
    val label: String,
    val score: Int,
    val pattern: Regex,
)

private val dynamicRangePatterns = listOf(
    DynamicRangePattern("杜比视界", 60, Regex("(?i)\\b(?:dv|dovi|dolby[ ._-]*vision)\\b")),
    DynamicRangePattern("HDR10+", 50, Regex("(?i)hdr10\\+|hdr10plus")),
    DynamicRangePattern("HDR10", 40, Regex("(?i)hdr10")),
    DynamicRangePattern("HLG", 30, Regex("(?i)\\bhlg\\b")),
    DynamicRangePattern("HDR", 20, Regex("(?i)\\bhdr\\b")),
    DynamicRangePattern("SDR", 10, Regex("(?i)\\bsdr\\b")),
)

fun mediaBadges(name: String): List<String> =
    dynamicRangePatterns
        .filter { it.pattern.containsMatchIn(name) }
        .map { it.label }
        .distinct()

fun mediaBadgeText(name: String): String =
    mediaBadges(name).joinToString(" / ")

fun dynamicRangeScore(name: String): Int =
    dynamicRangePatterns.firstOrNull { it.pattern.containsMatchIn(name) }?.score ?: 0

fun bestDynamicRangeVideo(videos: List<CloudVideo>): CloudVideo? =
    videos.maxWithOrNull(compareBy<CloudVideo> { dynamicRangeScore(it.name) }.thenBy { it.size })
