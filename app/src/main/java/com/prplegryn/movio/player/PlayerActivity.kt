package com.prplegryn.movio.player

import android.media.AudioManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.prplegryn.movio.data.MovioStore
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {
    private var mpvView: MovioMpvView? = null
    private lateinit var store: MovioStore
    private var fileId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_MUSIC
        store = MovioStore(this)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        fileId = intent.getStringExtra(EXTRA_FILE_ID).orEmpty()
        val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)

        setContent {
            var tracks by remember { mutableStateOf<List<MpvTrack>>(emptyList()) }
            var badges by remember { mutableStateOf<List<String>>(emptyList()) }
            var positionMs by remember { mutableLongStateOf(startMs) }
            var durationMs by remember { mutableLongStateOf(0L) }
            var paused by remember { mutableStateOf(false) }
            var chromeVisible by remember { mutableStateOf(true) }
            var interactionVersion by remember { mutableIntStateOf(0) }
            var selectedMenu by remember { mutableStateOf<String?>(null) }
            var zoomFill by remember { mutableStateOf(false) }
            var playerError by remember(url) {
                mutableStateOf(if (url.isBlank()) "播放地址为空" else "")
            }
            val revealControls = {
                chromeVisible = true
                interactionVersion += 1
            }

            BackHandler { finish() }
            LaunchedEffect(chromeVisible, selectedMenu, interactionVersion, playerError) {
                if (chromeVisible && selectedMenu == null && playerError.isBlank()) {
                    delay(3200)
                    chromeVisible = false
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    saveProgress()
                    mpvView?.shutdown()
                    mpvView = null
                }
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (url.isNotBlank()) {
                    AndroidView(
                        factory = { context ->
                            runCatching {
                                MovioMpvView(
                                    context = context,
                                    listener = object : MovioMpvListener {
                                        override fun onPosition(positionMsValue: Long, durationMsValue: Long) {
                                            positionMs = positionMsValue
                                            durationMs = durationMsValue
                                        }

                                        override fun onPauseChanged(pausedValue: Boolean) {
                                            paused = pausedValue
                                        }

                                        override fun onTracksChanged(updatedTracks: List<MpvTrack>) {
                                            tracks = updatedTracks
                                        }

                                        override fun onDynamicRangeChanged(updatedBadges: List<String>) {
                                            badges = updatedBadges
                                        }

                                        override fun onPlaybackError(message: String) {
                                            playerError = message
                                        }
                                    },
                                ).also { view ->
                                    mpvView = view
                                    view.start(url, startMs)
                                }
                            }.getOrElse { error ->
                                playerError = "播放器初始化失败：${error.message ?: error.javaClass.simpleName}"
                                FrameLayout(context).apply {
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    visibility = View.VISIBLE
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (selectedMenu != null) {
                                selectedMenu = null
                            } else {
                                chromeVisible = !chromeVisible
                                interactionVersion += 1
                            }
                        },
                )

                AnimatedVisibility(
                    visible = chromeVisible || selectedMenu != null || playerError.isNotBlank(),
                    enter = fadeIn(tween(140)),
                    exit = fadeOut(tween(140)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f))) {
                        PlayerTopBar(
                            title = title.ifBlank { fileName },
                            badges = badges,
                            onClose = { finish() },
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                        PlayerBottomControls(
                            paused = paused,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            zoomFill = zoomFill,
                            onPlayPause = {
                                revealControls()
                                mpvView?.togglePause()
                            },
                            onSeek = {
                                revealControls()
                                mpvView?.seekTo(it)
                            },
                            onSubtitles = {
                                revealControls()
                                selectedMenu = if (selectedMenu == "sub") null else "sub"
                            },
                            onAudio = {
                                revealControls()
                                selectedMenu = if (selectedMenu == "audio") null else "audio"
                            },
                            onFill = {
                                revealControls()
                                zoomFill = !zoomFill
                                mpvView?.setFill(zoomFill)
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }

                selectedMenu?.let { type ->
                    TrackSelector(
                        title = if (type == "audio") "选择音轨" else "选择字幕",
                        tracks = tracks.filter { it.type == type },
                        onSelected = { track ->
                            mpvView?.setTrack(type, track.id)
                            selectedMenu = null
                            revealControls()
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
                    )
                }

                if (playerError.isNotBlank()) {
                    PlayerErrorDialog(playerError) { finish() }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onStop() {
        saveProgress()
        super.onStop()
    }

    private fun saveProgress() {
        if (fileId.isNotBlank()) {
            mpvView?.currentPositionMs()?.takeIf { it > 0L }?.let {
                store.saveProgress(fileId, it)
            }
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_FILE_ID = "file_id"
        const val EXTRA_START_MS = "start_ms"
    }
}

@androidx.compose.runtime.Composable
private fun PlayerTopBar(
    title: String,
    badges: List<String>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.46f))
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerButton("‹", onClose)
        Spacer(Modifier.width(12.dp))
        BasicText(
            if (badges.isEmpty()) title else "$title  ${badges.joinToString(" / ")}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(Color.White, 17.sp, FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
    }
}

@androidx.compose.runtime.Composable
private fun PlayerBottomControls(
    paused: Boolean,
    positionMs: Long,
    durationMs: Long,
    zoomFill: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.52f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SeekBar(positionMs, durationMs, onSeek)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                style = TextStyle(Color.White.copy(alpha = 0.9f), 13.sp, FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            PlayerButton(if (paused) "播放" else "暂停", onPlayPause)
            Spacer(Modifier.width(8.dp))
            PlayerButton("音轨", onAudio)
            Spacer(Modifier.width(8.dp))
            PlayerButton("字幕", onSubtitles)
            Spacer(Modifier.width(8.dp))
            PlayerButton(if (zoomFill) "适应" else "填充", onFill)
        }
    }
}

@androidx.compose.runtime.Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val fraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0L && size.width > 0) {
                        onSeek((durationMs * (offset.x / size.width).coerceIn(0f, 1f)).toLong())
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White),
        )
    }
}

@androidx.compose.runtime.Composable
private fun TrackSelector(
    title: String,
    tracks: List<MpvTrack>,
    onSelected: (MpvTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(280.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xEE17191D))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(title, style = TextStyle(Color.White, 16.sp, FontWeight.Bold), modifier = Modifier.padding(8.dp))
        if (tracks.isEmpty()) {
            BasicText("未检测到可用轨道", style = TextStyle(Color.White.copy(alpha = 0.66f), 14.sp), modifier = Modifier.padding(8.dp))
        }
        tracks.forEach { track ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (track.selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelected(track) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(if (track.selected) "✓" else "", style = TextStyle(Color.White, 14.sp), modifier = Modifier.width(22.dp))
                BasicText(track.label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(Color.White, 14.sp))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlayerButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(Color.White, 13.sp, FontWeight.Bold), maxLines = 1)
    }
}

@androidx.compose.runtime.Composable
private fun PlayerErrorDialog(message: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicText("无法播放", style = TextStyle(Color(0xFF111318), 20.sp, FontWeight.Bold))
            BasicText(message, style = TextStyle(Color(0xFF55585E), 14.sp), maxLines = 6, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0088FF))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    BasicText("关闭", style = TextStyle(Color.White, 14.sp, FontWeight.Bold))
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val remainder = seconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, remainder)
    } else {
        "%02d:%02d".format(minutes, remainder)
    }
}
