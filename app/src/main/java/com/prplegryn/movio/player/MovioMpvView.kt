package com.prplegryn.movio.player

import android.content.Context
import android.os.Build
import android.util.Xml
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

data class MpvTrack(
    val id: Int,
    val type: String,
    val label: String,
    val selected: Boolean,
)

interface MovioMpvListener {
    fun onPosition(positionMs: Long, durationMs: Long) = Unit
    fun onPauseChanged(paused: Boolean) = Unit
    fun onTracksChanged(tracks: List<MpvTrack>) = Unit
    fun onDynamicRangeChanged(badges: List<String>) = Unit
    fun onPlaybackError(message: String) = Unit
}

class MovioMpvView(
    context: Context,
    private val listener: MovioMpvListener,
) : BaseMPVView(context, Xml.asAttributeSet(Xml.newPullParser())), MPVLib.EventObserver, MPVLib.LogObserver {
    private var startPositionMs = 0L
    private var durationMs = 0L
    private var destroyed = false
    private var playbackStarted = false

    fun start(url: String, startMs: Long) {
        startPositionMs = startMs.coerceAtLeast(0L)
        MPVLib.addObserver(this)
        MPVLib.addLogObserver(this)
        initialize(context.filesDir.path, context.cacheDir.path)
        playFile(url)
    }

    fun shutdown() {
        if (destroyed) return
        destroyed = true
        MPVLib.removeObserver(this)
        MPVLib.removeLogObserver(this)
        destroy()
    }

    fun togglePause() {
        MPVLib.command("cycle", "pause")
    }

    fun seekTo(positionMs: Long) {
        MPVLib.setPropertyDouble("time-pos", positionMs.coerceAtLeast(0L) / 1000.0)
    }

    fun currentPositionMs(): Long =
        ((MPVLib.getPropertyDouble("time-pos") ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)

    fun setTrack(type: String, id: Int) {
        val property = if (type == "audio") "aid" else "sid"
        if (id < 0) {
            MPVLib.setPropertyString(property, "no")
        } else {
            MPVLib.setPropertyInt(property, id)
        }
        post { publishTracks() }
    }

    fun setFill(fill: Boolean) {
        MPVLib.setPropertyDouble("panscan", if (fill) 1.0 else 0.0)
    }

    override fun initOptions() {
        setVo("gpu-next")
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec", "mediacodec,mediacodec-copy")
        MPVLib.setOptionString("hwdec-codecs", "all")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("audio-channels", "auto-safe")
        MPVLib.setOptionString("audio-set-media-role", "yes")
        MPVLib.setOptionString("volume", "100")
        MPVLib.setOptionString("mute", "no")
        MPVLib.setOptionString("tls-verify", "no")
        MPVLib.setOptionString(
            "http-header-fields",
            "Referer: https://www.guangyapan.com/,Origin: https://www.guangyapan.com",
        )
        MPVLib.setOptionString("network-timeout", "30")
        MPVLib.setOptionString("demuxer-max-bytes", cacheSizeBytes().toString())
        MPVLib.setOptionString("demuxer-max-back-bytes", cacheSizeBytes().toString())
        MPVLib.setOptionString("sub-ass-override", "no")
        MPVLib.setOptionString("sub-use-margins", "yes")
        MPVLib.setOptionString("sub-ass-force-margins", "yes")
        MPVLib.setOptionString("sub-font-size", "38")
        MPVLib.setOptionString("sub-border-size", "2")
        MPVLib.setOptionString("target-colorspace-hint", "yes")
        MPVLib.setOptionString("input-default-bindings", "no")
        MPVLib.setOptionString("keep-open", "yes")
    }

    override fun postInitOptions() {
        MPVLib.setOptionString("save-position-on-quit", "no")
    }

    override fun observeProperties() {
        MPVLib.observeProperty("time-pos", MPVLib.mpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration/full", MPVLib.mpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("track-list", MPVLib.mpvFormat.MPV_FORMAT_NONE)
        MPVLib.observeProperty("video-params/gamma", MPVLib.mpvFormat.MPV_FORMAT_STRING)
        MPVLib.observeProperty("video-params/primaries", MPVLib.mpvFormat.MPV_FORMAT_STRING)
    }

    override fun eventProperty(property: String) {
        if (property == "track-list") post {
            publishTracks()
            publishDynamicRange()
        }
    }

    override fun eventProperty(property: String, value: Long) = Unit

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause") post { listener.onPauseChanged(value) }
    }

    override fun eventProperty(property: String, value: String) {
        if (property == "video-params/gamma" || property == "video-params/primaries") {
            post { publishDynamicRange() }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "duration/full" -> {
                durationMs = (value * 1000.0).toLong().coerceAtLeast(0L)
                post { listener.onPosition(currentPositionMs(), durationMs) }
            }
            "time-pos" -> {
                val position = (value * 1000.0).toLong().coerceAtLeast(0L)
                post { listener.onPosition(position, durationMs) }
            }
        }
    }

    override fun eventProperty(property: String, value: MPVNode) = Unit

    override fun event(eventId: Int) {
        if (eventId == MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED) {
            playbackStarted = true
            if (startPositionMs > 0L) seekTo(startPositionMs)
            MPVLib.setPropertyBoolean("pause", false)
            post {
                publishTracks()
                publishDynamicRange()
            }
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (
            playbackStarted ||
            level > MPVLib.mpvLogLevel.MPV_LOG_LEVEL_ERROR ||
            !text.contains(Regex("failed|error|cannot|unsupported", RegexOption.IGNORE_CASE))
        ) {
            return
        }
        val message = text.trim().takeIf { it.isNotBlank() } ?: return
        post { listener.onPlaybackError(message) }
    }

    private fun publishTracks() {
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        val tracks = buildList {
            add(MpvTrack(-1, "sub", "关闭字幕", MPVLib.getPropertyString("sid") == "no"))
            for (index in 0 until count) {
                val type = MPVLib.getPropertyString("track-list/$index/type") ?: continue
                if (type != "audio" && type != "sub") continue
                val id = MPVLib.getPropertyInt("track-list/$index/id") ?: continue
                val language = MPVLib.getPropertyString("track-list/$index/lang").orEmpty()
                val title = MPVLib.getPropertyString("track-list/$index/title").orEmpty()
                val codec = MPVLib.getPropertyString("track-list/$index/codec").orEmpty()
                val selected = MPVLib.getPropertyBoolean("track-list/$index/selected") == true
                val fallback = if (type == "audio") "音轨 $id" else "字幕 $id"
                val label = listOf(title, language, codec)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" · ")
                    .ifBlank { fallback }
                add(MpvTrack(id, type, label, selected))
            }
        }
        listener.onTracksChanged(tracks)
    }

    private fun publishDynamicRange() {
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        val selectedVideoIndex = (0 until count).firstOrNull { index ->
            MPVLib.getPropertyString("track-list/$index/type") == "video" &&
                MPVLib.getPropertyBoolean("track-list/$index/selected") == true
        }
        val codec = selectedVideoIndex
            ?.let { MPVLib.getPropertyString("track-list/$it/codec").orEmpty().lowercase() }
            .orEmpty()
        val dolbyProfile = selectedVideoIndex
            ?.let { MPVLib.getPropertyInt("track-list/$it/dolby-vision-profile") }
        val gamma = MPVLib.getPropertyString("video-params/gamma").orEmpty().lowercase()
        val badges = buildList {
            if (
                dolbyProfile != null ||
                codec.contains("dovi") ||
                codec.contains("dvhe") ||
                codec.contains("dvh1")
            ) {
                add("杜比视界")
            } else if (gamma.contains("pq")) {
                add("HDR10")
            } else if (gamma.contains("hlg")) {
                add("HLG")
            } else if (gamma.isNotBlank()) {
                add("SDR")
            }
        }
        listener.onDynamicRangeChanged(badges)
    }

    private fun cacheSizeBytes(): Long =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 96L else 48L) * 1024L * 1024L
}
