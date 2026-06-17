package com.prplegryn.movio.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.prplegryn.movio.data.MovioStore
import com.prplegryn.movio.data.SubtitleTrackInfo
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var store: MovioStore
    private var fileId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MovioStore(this)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        fileId = intent.getStringExtra(EXTRA_FILE_ID).orEmpty()
        val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)

        setContent {
            var subtitleTracks by remember { mutableStateOf<List<SubtitleTrackInfo>>(emptyList()) }
            var dynamicRangeBadges by remember { mutableStateOf<List<String>>(emptyList()) }
            var selectorOpen by remember { mutableStateOf(false) }
            var playerError by remember { mutableStateOf("") }
            var chromeVisible by remember { mutableStateOf(true) }
            var zoomFill by remember { mutableStateOf(false) }
            val exo = remember(url) {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(USER_AGENT)
                    .setDefaultRequestProperties(
                        mapOf(
                            "Referer" to "https://www.guangyapan.com/",
                            "Origin" to "https://www.guangyapan.com",
                        )
                    )
                val dataSourceFactory = DefaultDataSource.Factory(this@PlayerActivity, httpFactory)
                ExoPlayer.Builder(this@PlayerActivity)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                    .build()
            }

            DisposableEffect(exo, url) {
                player = exo
                val listener = object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        subtitleTracks = readSubtitleTracks(tracks)
                        dynamicRangeBadges = readVideoDynamicRangeBadges(tracks)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playerError = "${error.errorCodeName}: ${error.message ?: "播放失败"}"
                    }
                }
                exo.addListener(listener)
                if (url.isBlank()) {
                    playerError = "播放地址为空"
                } else {
                    exo.setMediaItem(MediaItem.fromUri(url))
                    exo.prepare()
                    if (startMs > 0L) exo.seekTo(startMs)
                    exo.playWhenReady = true
                }
                onDispose {
                    saveProgress()
                    exo.removeListener(listener)
                    exo.release()
                    player = null
                }
            }

            LaunchedEffect(chromeVisible, selectorOpen, playerError) {
                if (chromeVisible && !selectorOpen && playerError.isBlank()) {
                    delay(3000)
                    chromeVisible = false
                }
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            player = exo
                            useController = true
                            controllerShowTimeoutMs = 3000
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setOnClickListener {
                                chromeVisible = true
                            }
                        }
                    },
                    update = { view ->
                        view.player = exo
                        view.resizeMode = if (zoomFill) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                AnimatedVisibility(
                    visible = chromeVisible || selectorOpen || playerError.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            titleWithBadges(title.ifBlank { fileName }, dynamicRangeBadges),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(Color.White, 18.sp, FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                        )
                        PlayerChip("字幕") {
                            chromeVisible = true
                            selectorOpen = !selectorOpen
                        }
                        Spacer(Modifier.width(8.dp))
                        PlayerChip(if (zoomFill) "适应" else "填充") {
                            chromeVisible = true
                            zoomFill = !zoomFill
                        }
                        Spacer(Modifier.width(8.dp))
                        PlayerChip("退出") { finish() }
                    }
                }

                if (selectorOpen) {
                    SubtitleSelector(
                        tracks = subtitleTracks,
                        onSelected = { track ->
                            applySubtitleTrack(track)
                            selectorOpen = false
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 64.dp, end = 14.dp),
                    )
                }

                if (playerError.isNotBlank()) {
                    PlayerErrorDialog(
                        message = playerError,
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    private fun readVideoDynamicRangeBadges(tracks: Tracks): List<String> {
        val badges = mutableListOf<String>()
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) {
                    badges += formatDynamicRangeBadges(group.getTrackFormat(i))
                }
            }
        }
        return badges.distinct()
    }

    private fun formatDynamicRangeBadges(format: Format): List<String> {
        val sampleMimeType = format.sampleMimeType.orEmpty().lowercase()
        val codecs = format.codecs.orEmpty().lowercase()
        val badges = mutableListOf<String>()
        if (
            sampleMimeType.contains("dolby-vision") ||
            codecs.contains("dvhe") ||
            codecs.contains("dvh1") ||
            codecs.contains("dovi")
        ) {
            badges += "杜比视界"
        }
        when (format.colorInfo?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> {
                if ("杜比视界" !in badges) {
                    badges += if (codecs.contains("hdr10+") || codecs.contains("hdr10plus")) "HDR10+" else "HDR10"
                }
            }
            C.COLOR_TRANSFER_HLG -> badges += "HLG"
            C.COLOR_TRANSFER_SDR -> badges += "SDR"
        }
        return badges.distinct()
    }

    override fun onStop() {
        saveProgress()
        super.onStop()
    }

    private fun readSubtitleTracks(tracks: Tracks): List<SubtitleTrackInfo> {
        val result = mutableListOf(SubtitleTrackInfo(-1, "关闭字幕", "", false))
        var index = 0
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val label = format.label?.takeIf { it.isNotBlank() }
                    ?: format.language?.takeIf { it.isNotBlank() }
                    ?: "字幕 ${index + 1}"
                result += SubtitleTrackInfo(
                    index = index,
                    label = label,
                    language = format.language.orEmpty(),
                    selected = group.isTrackSelected(i),
                )
                index += 1
            }
        }
        return result.map {
            if (it.index == -1) it.copy(selected = result.none { track -> track.index >= 0 && track.selected }) else it
        }
    }

    private fun applySubtitleTrack(track: SubtitleTrackInfo) {
        val current = player ?: return
        val builder: TrackSelectionParameters.Builder =
            current.trackSelectionParameters.buildUpon()
        if (track.index < 0) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            var index = 0
            for (group in current.currentTracks.groups) {
                if (group.type != C.TRACK_TYPE_TEXT) continue
                for (i in 0 until group.length) {
                    if (index == track.index) {
                        builder
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                        current.trackSelectionParameters = builder.build()
                        return
                    }
                    index += 1
                }
            }
        }
        current.trackSelectionParameters = builder.build()
    }

    private fun saveProgress() {
        val current = player ?: return
        if (fileId.isNotBlank() && current.duration > 0L) {
            store.saveProgress(fileId, current.currentPosition)
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_FILE_ID = "file_id"
        const val EXTRA_START_MS = "start_ms"
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
    }
}

private fun titleWithBadges(title: String, badges: List<String>): String =
    if (badges.isEmpty()) title else "$title  ${badges.joinToString(" / ")}"

@androidx.compose.runtime.Composable
private fun PlayerChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(Color.White, 14.sp, FontWeight.Bold))
    }
}

@androidx.compose.runtime.Composable
private fun SubtitleSelector(
    tracks: List<SubtitleTrackInfo>,
    onSelected: (SubtitleTrackInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.76f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val visibleTracks = tracks.ifEmpty {
            listOf(SubtitleTrackInfo(-1, "未检测到内嵌字幕", "", true))
        }
        visibleTracks.forEach { track ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (track.selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(enabled = tracks.isNotEmpty()) { onSelected(track) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    if (track.selected) "✓" else "",
                    style = TextStyle(Color.White, 14.sp),
                    modifier = Modifier.width(22.dp),
                )
                BasicText(track.label, style = TextStyle(Color.White, 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlayerErrorDialog(
    message: String,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("无法播放", style = TextStyle(Color(0xFF111318), 20.sp, FontWeight.Bold))
            BasicText(message, style = TextStyle(Color(0xFF4F4B45), 14.sp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0088FF))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("关闭", style = TextStyle(Color.White, 14.sp, FontWeight.Bold))
                }
            }
        }
    }
}
