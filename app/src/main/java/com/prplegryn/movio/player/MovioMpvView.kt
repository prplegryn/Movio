package com.prplegryn.movio.player

import android.content.Context
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import `is`.xyz.mpv.MPV
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
) : SurfaceView(context), SurfaceHolder.Callback, MPV.EventObserver, MPV.LogObserver {
    private val mpv = MPV()
    private var startPositionMs = 0L
    private var durationMs = 0L
    private var released = false
    private var surfaceAttached = false
    private var pendingUrl = ""
    private var playbackStarted = false

    fun start(url: String, startMs: Long) {
        startPositionMs = startMs.coerceAtLeast(0L)
        pendingUrl = url
        mpv.create(context)
        mpv.addObserver(this)
        mpv.addLogObserver(this)
        configureMpv()
        mpv.init()
        mpv.setOptionString("force-window", "no")
        mpv.setOptionString("idle", "once")
        observeProperties()
        holder.addCallback(this)
    }

    fun shutdown() {
        if (released) return
        released = true
        holder.removeCallback(this)
        mpv.removeObserver(this)
        mpv.removeLogObserver(this)
        if (!mpv.isInitialized) return
        runCatching { mpv.command("stop") }
        detachSurface()
        runCatching { mpv.destroy() }
    }

    fun togglePause() {
        if (released || !mpv.isInitialized) return
        mpv.command("cycle", "pause")
    }

    fun seekTo(positionMs: Long) {
        if (released || !mpv.isInitialized) return
        mpv.setPropertyDouble("time-pos", positionMs.coerceAtLeast(0L) / 1000.0)
    }

    fun currentPositionMs(): Long =
        if (released || !mpv.isInitialized) {
            0L
        } else {
            ((mpv.getPropertyDouble("time-pos") ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
        }

    fun setTrack(type: String, id: Int) {
        if (released || !mpv.isInitialized) return
        val property = if (type == "audio") "aid" else "sid"
        if (id < 0) {
            mpv.setPropertyString(property, "no")
        } else {
            mpv.setPropertyInt(property, id)
        }
        post { publishTracks() }
    }

    fun setFill(fill: Boolean) {
        if (released || !mpv.isInitialized) return
        mpv.setPropertyDouble("panscan", if (fill) 1.0 else 0.0)
        val safeSubtitlePosition = if (fill) 78.0 else 100.0
        mpv.setPropertyDouble("sub-pos", safeSubtitlePosition)
        mpv.setPropertyDouble("secondary-sub-pos", safeSubtitlePosition)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (released || !mpv.isInitialized || surfaceAttached) return
        mpv.attachSurface(holder.surface)
        surfaceAttached = true
        mpv.setOptionString("force-window", "yes")
        if (pendingUrl.isNotBlank()) {
            mpv.command("loadfile", pendingUrl)
            pendingUrl = ""
        } else {
            mpv.setPropertyString("vo", "gpu-next")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!released && mpv.isInitialized) {
            mpv.setPropertyString("android-surface-size", "${width}x$height")
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        detachSurface()
    }

    private fun detachSurface() {
        if (!surfaceAttached || !mpv.isInitialized) return
        runCatching { mpv.setPropertyString("vo", "null") }
        runCatching { mpv.setPropertyString("force-window", "no") }
        runCatching { mpv.detachSurface() }
        surfaceAttached = false
    }

    private fun configureMpv() {
        mpv.setOptionString("config", "yes")
        mpv.setOptionString("config-dir", context.filesDir.path)
        mpv.setOptionString("gpu-shader-cache-dir", context.cacheDir.path)
        mpv.setOptionString("icc-cache-dir", context.cacheDir.path)
        mpv.setOptionString("vo", "gpu-next")
        mpv.setOptionString("profile", "fast")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")
        mpv.setOptionString("hwdec", "mediacodec,mediacodec-copy")
        mpv.setOptionString("hwdec-codecs", "all")
        mpv.setOptionString("ao", "audiotrack,opensles")
        mpv.setOptionString("audio-channels", "auto-safe")
        mpv.setOptionString("audio-set-media-role", "yes")
        mpv.setOptionString("volume", "100")
        mpv.setOptionString("mute", "no")
        mpv.setOptionString("tls-verify", "no")
        mpv.setOptionString(
            "http-header-fields",
            "Referer: https://www.guangyapan.com/,Origin: https://www.guangyapan.com",
        )
        mpv.setOptionString("network-timeout", "30")
        mpv.setOptionString("demuxer-max-bytes", cacheSizeBytes().toString())
        mpv.setOptionString("demuxer-max-back-bytes", cacheSizeBytes().toString())
        mpv.setOptionString("sub-ass-override", "no")
        mpv.setOptionString("sub-use-margins", "yes")
        mpv.setOptionString("sub-ass-force-margins", "yes")
        mpv.setOptionString("blend-subtitles", "no")
        mpv.setOptionString("sub-font-size", "38")
        mpv.setOptionString("sub-border-size", "2")
        mpv.setOptionString("target-colorspace-hint", "yes")
        mpv.setOptionString("input-default-bindings", "no")
        mpv.setOptionString("keep-open", "yes")
        mpv.setOptionString("save-position-on-quit", "no")
    }

    private fun observeProperties() {
        mpv.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("duration/full", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("track-list", MPV.mpvFormat.MPV_FORMAT_NONE)
        mpv.observeProperty("video-params/gamma", MPV.mpvFormat.MPV_FORMAT_STRING)
        mpv.observeProperty("video-params/primaries", MPV.mpvFormat.MPV_FORMAT_STRING)
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

    override fun event(eventId: Int, data: MPVNode) {
        if (eventId == MPV.mpvEvent.MPV_EVENT_FILE_LOADED) {
            playbackStarted = true
            if (startPositionMs > 0L) seekTo(startPositionMs)
            mpv.setPropertyBoolean("pause", false)
            post {
                publishTracks()
                publishDynamicRange()
            }
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (
            playbackStarted ||
            level > MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR ||
            !text.contains(Regex("failed|error|cannot|unsupported", RegexOption.IGNORE_CASE))
        ) {
            return
        }
        val message = text.trim().takeIf { it.isNotBlank() } ?: return
        post { listener.onPlaybackError(message) }
    }

    private fun publishTracks() {
        val count = mpv.getPropertyInt("track-list/count") ?: 0
        val tracks = buildList {
            add(MpvTrack(-1, "sub", "关闭字幕", mpv.getPropertyString("sid") == "no"))
            for (index in 0 until count) {
                val type = mpv.getPropertyString("track-list/$index/type") ?: continue
                if (type != "audio" && type != "sub") continue
                val id = mpv.getPropertyInt("track-list/$index/id") ?: continue
                val language = mpv.getPropertyString("track-list/$index/lang").orEmpty()
                val title = mpv.getPropertyString("track-list/$index/title").orEmpty()
                val codec = mpv.getPropertyString("track-list/$index/codec").orEmpty()
                val selected = mpv.getPropertyBoolean("track-list/$index/selected") == true
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
        val count = mpv.getPropertyInt("track-list/count") ?: 0
        val selectedVideoIndex = (0 until count).firstOrNull { index ->
            mpv.getPropertyString("track-list/$index/type") == "video" &&
                mpv.getPropertyBoolean("track-list/$index/selected") == true
        }
        val codec = selectedVideoIndex
            ?.let { mpv.getPropertyString("track-list/$it/codec").orEmpty().lowercase() }
            .orEmpty()
        val dolbyProfile = selectedVideoIndex
            ?.let { mpv.getPropertyInt("track-list/$it/dolby-vision-profile") }
        val gamma = mpv.getPropertyString("video-params/gamma").orEmpty().lowercase()
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
