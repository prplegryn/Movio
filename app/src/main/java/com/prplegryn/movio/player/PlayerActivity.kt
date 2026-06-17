package com.prplegryn.movio.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.prplegryn.movio.data.MovioStore
import com.prplegryn.movio.data.SubtitleTrackInfo

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var store: MovioStore
    private var fileId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MovioStore(this)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        fileId = intent.getStringExtra(EXTRA_FILE_ID).orEmpty()
        val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)

        setContent {
            var subtitleTracks by remember { mutableStateOf<List<SubtitleTrackInfo>>(emptyList()) }
            var selectorOpen by remember { mutableStateOf(false) }
            val exo = remember(url) {
                ExoPlayer.Builder(this@PlayerActivity).build()
            }

            DisposableEffect(exo, url) {
                player = exo
                exo.setMediaItem(MediaItem.fromUri(url))
                exo.prepare()
                if (startMs > 0L) exo.seekTo(startMs)
                exo.playWhenReady = true
                val listener = object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        subtitleTracks = readSubtitleTracks(tracks)
                    }
                }
                exo.addListener(listener)
                onDispose {
                    saveProgress()
                    exo.removeListener(listener)
                    exo.release()
                    player = null
                }
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            player = exo
                            useController = true
                        }
                    },
                    update = { view ->
                        view.player = exo
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(Color.White, 18.sp, FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    PlayerChip("字幕") { selectorOpen = !selectorOpen }
                    Spacer(Modifier.width(8.dp))
                    PlayerChip("退出") { finish() }
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
            }
        }
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
        const val EXTRA_FILE_ID = "file_id"
        const val EXTRA_START_MS = "start_ms"
    }
}

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
        tracks.forEach { track ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (track.selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelected(track) }
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
