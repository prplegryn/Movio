package com.prplegryn.movio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
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
        if (controller.library.isNotEmpty()) {
            item {
                LibraryShortcutSection(
                    title = "继续观看",
                    groups = controller.library.filter {
                        ((it.movieFile?.playProgressMs
                            ?: it.episodes.maxOfOrNull { ep -> ep.video.playProgressMs }
                            ?: 0L) > 0L)
                    }.ifEmpty { controller.library.take(8) },
                    controller = controller,
                    onOpen = onOpen,
                )
            }
            item {
                LibraryShortcutSection(
                    title = "资源库",
                    groups = controller.library.take(12),
                    controller = controller,
                    onOpen = onOpen,
                )
            }
        }
        item {
            CoverSection(
                title = "最近观看",
                covers = recentCovers,
                wide = true,
            )
        }
        item {
            CoverSection("电影", movieCovers, wide = false)
        }
        item {
            CoverSection("电视剧", seriesCovers, wide = false)
        }
        item {
            CoverSection("其他", otherCovers, wide = false)
        }
        item {
            CoverSection("分类", categoryCovers, wide = false)
        }
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
                            BasicText("Movio", style = TextStyle(MutedInk, 12.sp, FontWeight.Bold))
                        }
                    }
                    BasicText(
                        group.displayTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(Ink, 13.sp, FontWeight.Bold),
                    )
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
private fun CoverSection(
    title: String,
    covers: List<CoverItem>,
    wide: Boolean,
) {
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
            items(covers) { cover ->
                CoverCard(cover = cover, wide = wide)
            }
        }
    }
}

@Composable
private fun CoverCard(
    cover: CoverItem,
    wide: Boolean,
) {
    val width = if (wide) 224.dp else 112.dp
    val aspectRatio = if (wide) 16f / 9f else 9f / 16f
    Column(
        modifier = Modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(if (wide) 18.dp else 16.dp))
                .background(Brush.linearGradient(cover.colors)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.24f),
                    radius = size.minDimension * 0.38f,
                    center = Offset(size.width * 0.18f, size.height * 0.16f),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.14f),
                    radius = size.minDimension * 0.52f,
                    center = Offset(size.width * 0.92f, size.height * 0.88f),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(size.width * 0.08f, size.height * 0.72f),
                    size = Size(size.width * 0.46f, size.height * 0.12f),
                    cornerRadius = CornerRadius(20f, 20f),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = if (wide) 0.26f else 0.34f),
                            )
                        )
                    )
            )
        }

        BasicText(
            text = cover.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = Ink,
                fontSize = if (wide) 15.sp else 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun TitlePage(title: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF7F3EB), Color(0xFFEAF0F4))
                )
            )
            .padding(horizontal = 24.dp)
    ) {
        BasicText(
            text = title,
            style = TextStyle(
                color = Ink,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 18.dp),
        )
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
private fun NavButton(
    glyph: Glyph,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
        label = "navSelection",
    )
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Accent.copy(alpha = 0.12f * progress))
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = glyph,
            tint = if (selected) Accent else Color(0xFF3F4247),
            modifier = Modifier
                .size(25.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.08f * progress
                    scaleY = 1f + 0.08f * progress
                },
        )
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

private data class CoverItem(
    val title: String,
    val colors: List<Color>,
)

private val recentCovers = listOf(
    CoverItem("银河边境", listOf(Color(0xFF0F172A), Color(0xFF2F80ED), Color(0xFFFFD166))),
    CoverItem("雨夜列车", listOf(Color(0xFF202124), Color(0xFF6C5CE7), Color(0xFF81ECEC))),
    CoverItem("海岸来信", listOf(Color(0xFF155E75), Color(0xFFB8E1DD), Color(0xFFFFB703))),
    CoverItem("午后剧场", listOf(Color(0xFF6D597A), Color(0xFFFFB4A2), Color(0xFF355070))),
)

private val movieCovers = listOf(
    CoverItem("逆光飞行", listOf(Color(0xFF264653), Color(0xFF2A9D8F), Color(0xFFE9C46A))),
    CoverItem("长街回声", listOf(Color(0xFF3A0CA3), Color(0xFFF72585), Color(0xFF4CC9F0))),
    CoverItem("白昼烟火", listOf(Color(0xFF343A40), Color(0xFFF8961E), Color(0xFFF94144))),
    CoverItem("第七频道", listOf(Color(0xFF073B4C), Color(0xFF06D6A0), Color(0xFFFFD166))),
    CoverItem("静默山谷", listOf(Color(0xFF1B4332), Color(0xFF74C69D), Color(0xFFD8F3DC))),
)

private val seriesCovers = listOf(
    CoverItem("城市样本", listOf(Color(0xFF14213D), Color(0xFFE5E5E5), Color(0xFFFCA311))),
    CoverItem("北纬三十度", listOf(Color(0xFF03045E), Color(0xFF00B4D8), Color(0xFFCAF0F8))),
    CoverItem("暗号计划", listOf(Color(0xFF3D405B), Color(0xFFE07A5F), Color(0xFFF2CC8F))),
    CoverItem("日落档案", listOf(Color(0xFF5F0F40), Color(0xFFFB8B24), Color(0xFFE36414))),
)

private val otherCovers = listOf(
    CoverItem("纪录短片", listOf(Color(0xFF0B132B), Color(0xFF5BC0BE), Color(0xFFFAF3DD))),
    CoverItem("现场音乐", listOf(Color(0xFF432818), Color(0xFFFFE6A7), Color(0xFF99582A))),
    CoverItem("动画精选", listOf(Color(0xFF7209B7), Color(0xFFB5179E), Color(0xFF4CC9F0))),
    CoverItem("专题合集", listOf(Color(0xFF22577A), Color(0xFF38A3A5), Color(0xFFC7F9CC))),
)

private val categoryCovers = listOf(
    CoverItem("动作", listOf(Color(0xFF9D0208), Color(0xFFFFBA08), Color(0xFF370617))),
    CoverItem("科幻", listOf(Color(0xFF240046), Color(0xFF7B2CBF), Color(0xFF64DFDF))),
    CoverItem("悬疑", listOf(Color(0xFF212529), Color(0xFFADB5BD), Color(0xFF495057))),
    CoverItem("喜剧", listOf(Color(0xFF006D77), Color(0xFFFFDDD2), Color(0xFFE29578))),
    CoverItem("家庭", listOf(Color(0xFF386641), Color(0xFFA7C957), Color(0xFFF2E8CF))),
)
