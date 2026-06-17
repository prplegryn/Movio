package com.prplegryn.movio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.prplegryn.movio.data.MediaGroup
import com.prplegryn.movio.data.MediaKind
import com.prplegryn.movio.data.MovioController
import com.prplegryn.movio.ui.LibrarySearchResults
import com.prplegryn.movio.ui.MediaDetailOverlay
import com.prplegryn.movio.ui.MovioLibraryPage
import com.prplegryn.movio.ui.MovioMinePage
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovioApp()
        }
    }
}

private enum class Destination {
    Play,
    Library,
    Mine,
}

private enum class Glyph {
    Play,
    Library,
    Mine,
    Search,
    Menu,
    Refresh,
    Check,
}

private val AppBackground = Color(0xFFF6F2EA)
private val Ink = Color(0xFF111318)
private val MutedInk = Color(0xFF68665F)
private val Accent = Color(0xFF2F80ED)

@Composable
private fun MovioApp() {
    val context = LocalContext.current
    val controller = remember { MovioController(context) }
    var destination by remember { mutableStateOf(Destination.Play) }
    var searchOpen by remember { mutableStateOf(false) }
    val backdrop = rememberLayerBackdrop {
        drawRect(AppBackground)
        drawContent()
    }

    BackHandler(enabled = controller.selectedGroup != null) {
        controller.closeDetail()
    }
    BackHandler(enabled = searchOpen && controller.selectedGroup == null) {
        searchOpen = false
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(AppBackground)
        ) {
            when (destination) {
                Destination.Play -> PlayPage(
                    controller = controller,
                    onOpen = { controller.openDetail(it) },
                )
                Destination.Library -> MovioLibraryPage(
                    controller = controller,
                    onOpen = { controller.openDetail(it) },
                )
                Destination.Mine -> MovioMinePage(controller)
            }
        }

        BottomChrome(
            selected = destination,
            backdrop = backdrop,
            onSelected = { destination = it },
            onSearch = { searchOpen = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SearchOverlay(
            visible = searchOpen,
            backdrop = backdrop,
            controller = controller,
            onOpen = {
                searchOpen = false
                controller.openDetail(it)
            },
            onDismiss = { searchOpen = false },
        )

        controller.selectedGroup?.let { group ->
            MediaDetailOverlay(
                group = group,
                controller = controller,
                onClose = { controller.closeDetail() },
            )
        }

        if (controller.message.isErrorMessage()) {
            AppErrorDialog(
                message = controller.message,
                onDismiss = { controller.dismissMessage() },
            )
        }
    }
}

@Composable
private fun PlayPage(
    controller: MovioController,
    onOpen: (MediaGroup) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF6F2EA),
                        Color(0xFFF1EDE5),
                        Color(0xFFE9EEF2),
                    )
                )
            )
    ) {
        PlayContent(
            controller = controller,
            onOpen = onOpen,
        )

        PlayTopBar(
            menuExpanded = menuExpanded,
            onMenuClick = { menuExpanded = !menuExpanded },
            modifier = Modifier.zIndex(4f),
        )

        MenuBackdrop(
            visible = menuExpanded,
            onDismiss = { menuExpanded = false },
        )
    }
}

@Composable
private fun PlayContent(
    controller: MovioController,
    onOpen: (MediaGroup) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 104.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val movies = controller.library.filter { it.kind == MediaKind.Movie && it.tmdb != null }
        val tvShows = controller.library.filter { it.kind == MediaKind.Tv && it.tmdb != null }
        val others = controller.library.filter { it.kind == MediaKind.Unknown }
        val continueWatching = controller.library.filter {
            ((it.movieFile?.playProgressMs
                ?: it.episodes.maxOfOrNull { ep -> ep.video.playProgressMs }
                ?: it.unmatchedFiles.maxOfOrNull { file -> file.playProgressMs }
                ?: 0L) > 0L)
        }
        if (controller.library.isEmpty()) {
            item {
                EmptyPlayState()
            }
        } else {
            if (continueWatching.isNotEmpty()) {
                item {
                    LibraryShortcutSection(
                        title = "继续观看",
                        groups = continueWatching,
                        controller = controller,
                        onOpen = onOpen,
                    )
                }
            }
            if (movies.isNotEmpty()) {
                item {
                    LibraryShortcutSection(
                        title = "电影",
                        groups = movies,
                        controller = controller,
                        onOpen = onOpen,
                    )
                }
            }
            if (tvShows.isNotEmpty()) {
                item {
                    LibraryShortcutSection(
                        title = "电视剧",
                        groups = tvShows,
                        controller = controller,
                        onOpen = onOpen,
                    )
                }
            }
            if (others.isNotEmpty()) {
                item {
                    LibraryShortcutSection(
                        title = "其他",
                        groups = others,
                        controller = controller,
                        onOpen = onOpen,
                    )
                }
            }
            if (movies.isEmpty() && tvShows.isEmpty() && others.isEmpty()) {
                item {
                    EmptyPlayState(
                        title = "还没有影视库条目",
                        body = "请在“我的”里同步资源库，匹配成功的电影和电视剧会显示在这里。",
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlayState(
    title: String = "还没有同步资源库",
    body: String = "在“我的”里登录光鸭、选择根目录、配置 TMDb Read Access Token 后同步。",
) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.68f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(title, style = TextStyle(Ink, 19.sp, FontWeight.Bold))
        BasicText(body, style = TextStyle(MutedInk, 14.sp))
    }
}

@Composable
private fun LibraryShortcutSection(
    title: String,
    groups: List<MediaGroup>,
    controller: MovioController,
    onOpen: (MediaGroup) -> Unit,
) {
    if (groups.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(
            text = title,
            style = TextStyle(
                color = Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            items(groups) { group ->
                Column(
                    Modifier
                        .width(118.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.60f))
                        .clickable { onOpen(group) }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFCBD9DF), Color(0xFFE7E0D2)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        val poster = controller.imageUrl(group.primaryPosterPath, "w342")
                        if (poster.isNotBlank()) {
                            AsyncImage(model = poster, contentDescription = null, modifier = Modifier.fillMaxSize())
                        } else {
                            BasicText("其他", style = TextStyle(MutedInk, 12.sp, FontWeight.Bold))
                        }
                    }
                    BasicText(
                        group.displayTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(Ink, 13.sp, FontWeight.Bold),
                    )
                    BasicText(
                        group.playSubtitle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(MutedInk, 12.sp),
                    )
                }
            }
        }
    }
}

private fun MediaGroup.playSubtitle(): String =
    when (kind) {
        MediaKind.Movie -> "电影"
        MediaKind.Tv -> "${episodes.size} 集"
        MediaKind.Unknown -> "${unmatchedFiles.size} 个未匹配"
    }

private fun String.isErrorMessage(): Boolean =
    contains("失败") || contains("无法") || contains("没有") || contains("请先")

@Composable
private fun AppErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color.Black.copy(alpha = 0.26f))
            .noRippleClickable(onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .noRippleClickable {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("操作失败", style = TextStyle(Ink, 20.sp, FontWeight.Bold))
            BasicText(message, style = TextStyle(MutedInk, 14.sp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Accent)
                        .noRippleClickable(onDismiss)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("知道了", style = TextStyle(Color.White, 14.sp, FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun PlayTopBar(
    menuExpanded: Boolean,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "Play",
            style = TextStyle(
                color = Ink,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.weight(1f),
        )

        IconButton(
            glyph = Glyph.Menu,
            contentDescription = "筛选",
            selected = menuExpanded,
            onClick = onMenuClick,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            glyph = Glyph.Refresh,
            contentDescription = "刷新",
            selected = false,
            onClick = {},
        )
    }
}
@Composable
private fun MenuBackdrop(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(170)),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(2f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f))
                .noRippleClickable(onDismiss)
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = naturalMenuEnter(),
        exit = naturalMenuExit(),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 66.dp, end = 20.dp)
            .zIndex(5f),
    ) {
        Box(Modifier.fillMaxWidth()) {
            MultiSelectMenu(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(224.dp),
            )
        }
    }
}

private fun naturalMenuEnter(): EnterTransition =
    fadeIn(tween(120)) +
        scaleIn(
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 260f),
            initialScale = 0.94f,
            transformOrigin = TransformOrigin(0.92f, 0f),
        ) +
        expandVertically(
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 260f),
            expandFrom = Alignment.Top,
        )

private fun naturalMenuExit(): ExitTransition =
    fadeOut(tween(120)) +
        scaleOut(
            animationSpec = tween(140),
            targetScale = 0.96f,
            transformOrigin = TransformOrigin(0.92f, 0f),
        ) +
        shrinkVertically(animationSpec = tween(140), shrinkTowards = Alignment.Top)

@Composable
private fun MultiSelectMenu(modifier: Modifier = Modifier) {
    val selected = remember { mutableStateListOf(true, false, true) }
    val options = listOf("本地资源", "继续播放", "高清优先")
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier
            .graphicsLayer {
                shadowElevation = 22.dp.toPx()
                this.shape = shape
                clip = false
                ambientShadowColor = Color(0x66000000)
                spotShadowColor = Color(0x26000000)
            }
            .clip(shape)
            .background(Color.White)
            .padding(vertical = 8.dp),
    ) {
        options.forEachIndexed { index, title ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .noRippleClickable {
                        selected[index] = !selected[index]
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = title,
                    style = TextStyle(
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (selected[index]) {
                        GlyphIcon(Glyph.Check, Accent, Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomChrome(
    selected: Destination,
    backdrop: Backdrop,
    onSelected: (Destination) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidBottomTabs(
            selectedTabIndex = { selected.ordinal },
            onTabSelected = { index ->
                onSelected(
                    when (index) {
                        0 -> Destination.Play
                        1 -> Destination.Library
                        else -> Destination.Mine
                    }
                )
            },
            backdrop = backdrop,
            tabsCount = 3,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            LiquidBottomTab {
                GlyphIcon(Glyph.Play, Ink, Modifier.size(27.dp))
            }
            LiquidBottomTab {
                GlyphIcon(Glyph.Library, Ink, Modifier.size(27.dp))
            }
            LiquidBottomTab {
                GlyphIcon(Glyph.Mine, Ink, Modifier.size(27.dp))
            }
        }

        LiquidCircleIconButton(
            onClick = onSearch,
            backdrop = backdrop,
            modifier = Modifier.size(64.dp),
        ) {
            GlyphIcon(Glyph.Search, Accent, Modifier.size(26.dp))
        }
    }
}

@Composable
private fun IconButton(
    glyph: Glyph,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.70f) else Color.Transparent)
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, Ink, Modifier.size(24.dp))
    }
}

@Composable
private fun SearchOverlay(
    visible: Boolean,
    backdrop: Backdrop,
    controller: MovioController,
    onOpen: (MediaGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            query = ""
            delay(80)
            focusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(220)),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = {
                            blur(18.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(Color.Black.copy(alpha = 0.14f))
                        },
                    )
                    .noRippleClickable(onDismiss)
            )

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(170)) + slideInVertically(
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 260f),
                    initialOffsetY = { -it / 2 },
                ),
                exit = fadeOut(tween(170)) + slideOutVertically(
                    animationSpec = tween(190),
                    targetOffsetY = { -it / 2 },
                ),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.96f))
                        .padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(Glyph.Search, MutedInk, Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            BasicText(
                                text = "搜索",
                                style = TextStyle(
                                    color = MutedInk.copy(alpha = 0.72f),
                                    fontSize = 18.sp,
                                ),
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Ink,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = Brush.verticalGradient(listOf(Accent, Accent)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }
                }
            }

            LibrarySearchResults(
                query = query,
                controller = controller,
                onOpen = onOpen,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun GlyphIcon(
    glyph: Glyph,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = w * 0.09f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        when (glyph) {
            Glyph.Play -> {
                val path = Path().apply {
                    moveTo(w * 0.36f, h * 0.24f)
                    lineTo(w * 0.36f, h * 0.76f)
                    lineTo(w * 0.76f, h * 0.50f)
                    close()
                }
                drawPath(path, tint)
            }

            Glyph.Library -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.20f, h * 0.20f),
                    size = Size(w * 0.26f, h * 0.26f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.54f, h * 0.20f),
                    size = Size(w * 0.26f, h * 0.26f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.20f, h * 0.54f),
                    size = Size(w * 0.60f, h * 0.26f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = stroke,
                )
            }

            Glyph.Mine -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.14f,
                    center = Offset(w * 0.50f, h * 0.34f),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(w * 0.23f, h * 0.48f),
                    size = Size(w * 0.54f, h * 0.44f),
                    style = stroke,
                )
            }

            Glyph.Search -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.24f,
                    center = Offset(w * 0.44f, h * 0.42f),
                    style = stroke,
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.62f, h * 0.62f),
                    end = Offset(w * 0.80f, h * 0.80f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }

            Glyph.Menu -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.30f), Offset(w * 0.78f, h * 0.30f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.78f, h * 0.50f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.22f, h * 0.70f), Offset(w * 0.78f, h * 0.70f), stroke.width, StrokeCap.Round)
            }

            Glyph.Refresh -> {
                drawArc(
                    color = tint,
                    startAngle = 36f,
                    sweepAngle = 292f,
                    useCenter = false,
                    topLeft = Offset(w * 0.22f, h * 0.22f),
                    size = Size(w * 0.56f, h * 0.56f),
                    style = stroke,
                )
                val path = Path().apply {
                    moveTo(w * 0.76f, h * 0.20f)
                    lineTo(w * 0.78f, h * 0.40f)
                    lineTo(w * 0.60f, h * 0.34f)
                }
                drawPath(path, tint, style = stroke)
            }

            Glyph.Check -> {
                val path = Path().apply {
                    moveTo(w * 0.22f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.72f)
                    lineTo(w * 0.80f, h * 0.30f)
                }
                drawPath(path, tint, style = stroke)
            }
        }
    }
}

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}
