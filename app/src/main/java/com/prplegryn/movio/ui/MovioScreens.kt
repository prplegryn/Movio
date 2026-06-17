package com.prplegryn.movio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.prplegryn.movio.data.CloudFolder
import com.prplegryn.movio.data.CloudVideo
import com.prplegryn.movio.data.LibraryEpisode
import com.prplegryn.movio.data.MediaGroup
import com.prplegryn.movio.data.MediaKind
import com.prplegryn.movio.data.MovioController
import kotlinx.coroutines.launch

private val Ink = Color(0xFF111318)
private val Muted = Color(0xFF6E6A61)
private val Accent = Color(0xFF0088FF)
private val Surface = Color.White.copy(alpha = 0.72f)

@Composable
fun MovioLibraryPage(
    controller: MovioController,
    onOpen: (MediaGroup) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val movies = controller.library.filter { it.kind == MediaKind.Movie && it.tmdb != null }
    val tvShows = controller.library.filter { it.kind == MediaKind.Tv && it.tmdb != null }
    val others = controller.library.filter { it.kind == MediaKind.Unknown }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF7F3EB), Color(0xFFEAF0F4)))),
        contentPadding = PaddingValues(top = 88.dp, start = 20.dp, end = 20.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageHeader(
                title = "资源库",
                action = if (controller.loading) "同步中" else "同步",
                onAction = { scope.launch { controller.refreshLibrary() } },
            )
        }

        if (controller.library.isEmpty()) {
            item {
                EmptyPanel(
                    title = "还没有资源",
                    body = "在“我的”里登录光鸭、选择根目录、配置 TMDb Read Access Token 后同步资源库。",
                )
            }
        } else {
            mediaSection("电影", movies, controller, onOpen)
            mediaSection("电视剧", tvShows, controller, onOpen)
            mediaSection("其他", others, controller, onOpen)
        }
    }
}

private fun LazyListScope.mediaSection(
    title: String,
    groups: List<MediaGroup>,
    controller: MovioController,
    onOpen: (MediaGroup) -> Unit,
) {
    if (groups.isEmpty()) return
    item {
        BasicText(title, style = TextStyle(Ink, 22.sp, FontWeight.Bold))
    }
    items(groups.chunked(2)) { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            row.forEach { group ->
                MediaCard(
                    group = group,
                    controller = controller,
                    modifier = Modifier.weight(1f),
                    onOpen = { onOpen(group) },
                )
            }
            if (row.size == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MovioMinePage(controller: MovioController) {
    val scope = rememberCoroutineScope()
    var phone by remember(controller.settings.guangya.phone) { mutableStateOf(controller.settings.guangya.phone.ifBlank { "+86 " }) }
    var code by remember { mutableStateOf("") }
    var tmdbToken by remember(controller.settings.tmdbToken) { mutableStateOf(controller.settings.tmdbToken) }
    val loggedIn = controller.loggedIn

    LaunchedEffect(loggedIn) {
        if (loggedIn && controller.rootFolders.isEmpty()) {
            controller.refreshRootFolders()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF7F3EB), Color(0xFFEAF0F4)))),
        contentPadding = PaddingValues(top = 88.dp, start = 20.dp, end = 20.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { BasicText("我的", style = TextStyle(Ink, 34.sp, fontWeight = FontWeight.Bold)) }

        item {
            SettingsPanel(title = "光鸭云盘") {
                if (loggedIn) {
                    StatusLine("登录账号", controller.settings.guangya.phone.ifBlank { "已登录" })
                    ActionChip("退出登录", Modifier.fillMaxWidth()) {
                        controller.logout()
                    }
                } else {
                    StatusLine("登录状态", "未登录")
                    LabeledInput("手机号", phone, onChange = { phone = it }, placeholder = "+86 13800138000")
                    if (controller.pendingVerificationId.isNotBlank()) {
                        LabeledInput("短信验证码", code, onChange = { code = it }, placeholder = "6 位验证码")
                    }
                    ActionChip(
                        if (controller.pendingVerificationId.isBlank()) "发送验证码" else "完成登录",
                        Modifier.fillMaxWidth(),
                    ) {
                        scope.launch {
                            if (controller.pendingVerificationId.isBlank()) {
                                controller.requestSms(phone)
                            } else {
                                controller.finishSmsLogin(code)
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsPanel(title = "资源与搜刮") {
                RootFolderSelector(
                    folders = controller.rootFolders.ifEmpty {
                        listOf(
                            if (controller.settings.rootId == "*") {
                                CloudFolder("*", "全部视频")
                            } else {
                                CloudFolder(controller.settings.rootId, "当前目录")
                            }
                        )
                    },
                    selectedId = controller.settings.rootId,
                    enabled = loggedIn,
                    onSelected = { controller.updateRootId(it.id) },
                )
                LabeledInput(
                    "TMDb Read Access Token",
                    tmdbToken,
                    onChange = { tmdbToken = it },
                    placeholder = "粘贴 TMDb API Read Access Token",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip("保存配置", Modifier.weight(1f)) {
                        controller.updateTmdbToken(tmdbToken)
                    }
                    ActionChip(if (controller.loading) "同步中" else "同步资源库", Modifier.weight(1f)) {
                        controller.updateTmdbToken(tmdbToken)
                        scope.launch { controller.refreshLibrary() }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip("刷新目录", Modifier.weight(1f)) {
                        scope.launch { controller.refreshRootFolders() }
                    }
                    Spacer(Modifier.weight(1f))
                }
                StatusLine("TMDb", if (tmdbToken.isBlank()) "未配置，无法建立影视库" else "已配置")
            }
        }

        if (controller.message.isNotBlank()) {
            item {
                BasicText(
                    controller.message,
                    style = TextStyle(if (controller.message.contains("失败")) Color(0xFFB3261E) else Muted, 14.sp),
                )
            }
        }
    }
}

@Composable
fun MediaDetailOverlay(
    group: MediaGroup,
    controller: MovioController,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedSeason by remember(group) {
        mutableIntStateOf(
            group.episodes.firstOrNull()?.parsed?.seasonNumber
                ?: group.seasons.firstOrNull()?.seasonNumber
                ?: 1
        )
    }
    BackHandler(onBack = onClose)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F2EA)),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            DetailHero(
                group = group,
                selectedSeason = selectedSeason,
                controller = controller,
                onClose = onClose,
                onPlay = {
                    scope.launch { controller.play(it, group) }
                },
            )
        }

        when (group.kind) {
            MediaKind.Tv -> {
                item {
                    SeasonTabs(
                        group = group,
                        selectedSeason = selectedSeason,
                        onSelected = { selectedSeason = it },
                    )
                }
                items(group.episodes.filter { (it.parsed.seasonNumber ?: 1) == selectedSeason }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        group = group,
                        controller = controller,
                        onClick = { scope.launch { controller.play(it, group, episode) } },
                    )
                }
            }
            MediaKind.Movie -> {
                item {
                    MovieFileRow(
                        group = group,
                        controller = controller,
                        onClick = { scope.launch { controller.play(it, group) } },
                    )
                }
            }
            MediaKind.Unknown -> {
                items(group.unmatchedFiles) { video ->
                    FileRow(
                        video = video,
                        onClick = { scope.launch { controller.playVideo(it, video, video.name) } },
                    )
                }
            }
        }
    }
}

@Composable
fun LibrarySearchResults(
    query: String,
    controller: MovioController,
    onOpen: (MediaGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val results = remember(query, controller.library) {
        controller.library.filter {
            query.isBlank() ||
                it.displayTitle.contains(query, ignoreCase = true) ||
                it.localTitle.contains(query, ignoreCase = true)
        }.take(8)
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 92.dp, start = 18.dp, end = 18.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(results) { group ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.86f))
                    .clickable { onOpen(group) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PosterImage(
                    url = controller.imageUrl(group.primaryPosterPath, "w185"),
                    modifier = Modifier
                        .width(54.dp)
                        .aspectRatio(2f / 3f),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(group.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(Ink, 16.sp, FontWeight.Bold))
                    BasicText(group.subtitle(), style = TextStyle(Muted, 13.sp))
                }
            }
        }
    }
}

@Composable
private fun DetailHero(
    group: MediaGroup,
    selectedSeason: Int,
    controller: MovioController,
    onClose: () -> Unit,
    onPlay: (android.content.Context) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val seasonPoster = group.seasons.firstOrNull { it.seasonNumber == selectedSeason }?.posterPath.orEmpty()
    val poster = seasonPoster.ifBlank { group.primaryPosterPath }
    Box(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(Color(0xFFE6E0D5)),
    ) {
        AsyncImage(
            model = controller.imageUrl(group.primaryBackdropPath.ifBlank { poster }, "w780"),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color(0xFFF6F2EA)), startY = 100f))
        )
        ActionIcon("×", Modifier.statusBarsPadding().padding(18.dp).align(Alignment.TopStart), onClose)
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            PosterImage(
                url = controller.imageUrl(poster, "w342"),
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(2f / 3f),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(group.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(Ink, 26.sp, FontWeight.Bold))
                BasicText(
                    group.tmdb?.overview?.ifBlank { "暂无简介" } ?: "暂无简介",
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(Muted, 14.sp),
                )
                ActionChip("播放", Modifier.width(96.dp)) { onPlay(context) }
            }
        }
    }
}

@Composable
private fun SeasonTabs(group: MediaGroup, selectedSeason: Int, onSelected: (Int) -> Unit) {
    val seasons = group.seasons.ifEmpty {
        group.episodes.map { it.parsed.seasonNumber ?: 1 }.distinct().map {
            com.prplegryn.movio.data.TmdbSeason(it, "第 $it 季")
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(seasons) { season ->
            val selected = season.seasonNumber == selectedSeason
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) Accent else Color.White.copy(alpha = 0.72f))
                    .clickable { onSelected(season.seasonNumber) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                BasicText(season.name, style = TextStyle(if (selected) Color.White else Ink, 14.sp, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: LibraryEpisode,
    group: MediaGroup,
    controller: MovioController,
    onClick: (android.content.Context) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val progress = episode.video.playProgressMs
    val cover = if (progress > 0L && episode.video.rawCoverUrl.isNotBlank()) {
        episode.video.rawCoverUrl
    } else {
        controller.imageUrl(episode.tmdb?.stillPath.orEmpty(), "w300")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .clickable { onClick(context) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(
            url = cover,
            modifier = Modifier
                .width(116.dp)
                .aspectRatio(16f / 9f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val epNo = episode.parsed.episodeNumber ?: 1
            BasicText("第 $epNo 集 ${episode.tmdb?.title.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(Ink, 15.sp, FontWeight.Bold))
            BasicText(episode.tmdb?.overview?.ifBlank { episode.video.name } ?: episode.video.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(Muted, 12.sp))
            if (progress > 0L) {
                BasicText("已看到 ${formatDuration(progress)}", style = TextStyle(Accent, 12.sp, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun MovieFileRow(
    group: MediaGroup,
    controller: MovioController,
    onClick: (android.content.Context) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val video = group.movieFile
    val progress = video?.playProgressMs ?: 0L
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .clickable(enabled = video != null) { onClick(context) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(
            url = video?.rawCoverUrl?.takeIf { progress > 0L }.orEmpty()
                .ifBlank { controller.imageUrl(group.primaryBackdropPath.ifBlank { group.primaryPosterPath }, "w500") },
            modifier = Modifier
                .width(132.dp)
                .aspectRatio(16f / 9f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText(group.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(Ink, 16.sp, FontWeight.Bold))
            BasicText(video?.name ?: "没有可播放文件", maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(Muted, 12.sp))
            if (progress > 0L) {
                BasicText("已看到 ${formatDuration(progress)}", style = TextStyle(Accent, 12.sp, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun FileRow(
    video: CloudVideo,
    onClick: (android.content.Context) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val progress = video.playProgressMs
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .clickable { onClick(context) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(
            url = video.rawCoverUrl.takeIf { progress > 0L }.orEmpty(),
            modifier = Modifier
                .width(116.dp)
                .aspectRatio(16f / 9f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText(video.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(Ink, 15.sp, FontWeight.Bold))
            BasicText(formatFileSize(video.size), style = TextStyle(Muted, 12.sp))
            if (progress > 0L) {
                BasicText("已看到 ${formatDuration(progress)}", style = TextStyle(Accent, 12.sp, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun MediaCard(
    group: MediaGroup,
    controller: MovioController,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.66f))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PosterImage(
            url = controller.imageUrl(group.primaryPosterPath, "w342"),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        )
        BasicText(group.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(Ink, 15.sp, FontWeight.Bold))
        BasicText(group.subtitle(), style = TextStyle(Muted, 12.sp))
    }
}

@Composable
private fun PosterImage(url: String, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFDEE6EA), Color(0xFFC8D6DD)))),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            BasicText("Movio", style = TextStyle(Muted, 13.sp, FontWeight.Bold))
        }
    }
}

@Composable
private fun PageHeader(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, style = TextStyle(Ink, 34.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        ActionChip(action, onClick = onAction)
    }
}

@Composable
private fun EmptyPanel(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(title, style = TextStyle(Ink, 18.sp, FontWeight.Bold))
        BasicText(body, style = TextStyle(Muted, 14.sp))
    }
}

@Composable
private fun SettingsPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(title, style = TextStyle(Ink, 18.sp, FontWeight.Bold))
        content()
    }
}

@Composable
private fun RootFolderSelector(
    folders: List<CloudFolder>,
    selectedId: String,
    enabled: Boolean,
    onSelected: (CloudFolder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = folders.firstOrNull { it.id == selectedId }
        ?: folders.firstOrNull()
        ?: CloudFolder("*", "全部视频")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText("资源根目录", style = TextStyle(Muted, 12.sp, FontWeight.SemiBold))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = if (enabled) 0.78f else 0.45f))
                .clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    selected.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(Ink, 15.sp, FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                BasicText(if (expanded) "收起" else "选择", style = TextStyle(Muted, 13.sp))
            }
        }
        if (expanded && enabled) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                folders.forEach { folder ->
                    val isSelected = folder.id == selected.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) Accent.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable {
                                onSelected(folder)
                                expanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            folder.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(if (isSelected) Accent else Ink, 14.sp, FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            BasicText("已选", style = TextStyle(Accent, 12.sp, FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledInput(label: String, value: String, onChange: (String) -> Unit, placeholder: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label, style = TextStyle(Muted, 12.sp, FontWeight.SemiBold))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.78f))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isBlank()) {
                BasicText(placeholder, style = TextStyle(Muted.copy(alpha = 0.62f), 15.sp))
            }
            BasicTextField(value = value, onValueChange = onChange, singleLine = true, textStyle = TextStyle(Ink, 15.sp), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = TextStyle(Muted, 13.sp), modifier = Modifier.weight(1f))
        BasicText(value, style = TextStyle(Ink, 13.sp, FontWeight.SemiBold))
    }
}

@Composable
private fun ActionChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(Color.White, 14.sp, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun ActionIcon(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.82f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(Ink, 24.sp, FontWeight.Bold))
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分"
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "视频文件"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    return if (index == 0) "${bytes}B" else String.format("%.1f%s", value, units[index])
}

private fun MediaGroup.subtitle(): String =
    when (kind) {
        MediaKind.Movie -> "电影"
        MediaKind.Tv -> "${episodes.size} 集"
        MediaKind.Unknown -> "${unmatchedFiles.size} 个未匹配文件"
    }
