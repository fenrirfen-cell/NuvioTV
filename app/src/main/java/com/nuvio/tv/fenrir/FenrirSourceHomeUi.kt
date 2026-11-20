package com.nuvio.tv.fenrir

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

private val Edge: Dp = 48.dp
private val Red = Color(0xFFFF4D57)
private val Soft = Color(0xFF2A2B34).copy(alpha = 0.6f)

/** Player route for a link found on a CloudStream site, saved to Continue watching as "fenrircs:Site|url". */
fun fenrirPlayRoute(
    itemKey: String,
    title: String,
    poster: String?,
    episode: FenrirSourceEpisode,
    link: FenrirSourceLink,
    isMovie: Boolean,
): String = Screen.Player.createRoute(
    streamUrl = link.url,
    title = title,
    streamName = link.label,
    headers = link.headers.takeIf { it.isNotEmpty() },
    contentId = FenrirSourceHomePick.ID_PREFIX + itemKey,
    contentType = if (isMovie) "movie" else "series",
    contentName = title,
    poster = poster,
    videoId = episode.key,
    season = if (isMovie) null else episode.season,
    episode = if (isMovie) null else episode.episode,
    episodeTitle = if (isMovie) null else episode.name,
    addonName = "CloudStream · ${link.sourceName}",
    streamDescription = link.detail,
)

// ------------------------------------------------------------------ source button + picker

/** "FENRIR TV ▾" pill: shows which site Home is showing and opens the list of sites. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FenrirSourceButton(sourceName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color(0xFF0B0B0F),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = NuvioTheme.focusRing.border(2.dp), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("FENRIR", fontSize = 14.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(5.dp))
            Text("TV", fontSize = 10.sp, letterSpacing = 2.sp, color = Red, fontWeight = FontWeight.SemiBold)
            Text(" ▾", fontSize = 13.sp)
            if (!sourceName.isNullOrBlank()) {
                Text("  ·  $sourceName", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** List of sites to show on Home, with "Nuvio home" first. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FenrirSourcePickerDialog(
    sources: List<FenrirHomeSource>?,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(sources) { runCatching { firstFocus.requestFocus() } }
    NuvioDialog(
        onDismiss = onDismiss,
        title = "Home shows",
        subtitle = "Pick a CloudStream source to see its own categories on Home.",
        width = 520.dp,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "nuvio") {
                SourceRow(
                    name = "Nuvio home",
                    subtitle = "Your add-ons' catalogs",
                    letter = null,
                    selected = selected == null,
                    onClick = { onPick(null) },
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }
            when {
                sources == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                sources.isEmpty() -> item(key = "empty") {
                    Text(
                        text = "No CloudStream sources with a home page yet. Add CloudStream plugins in Settings > Plugins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                else -> items(sources, key = { it.id }) { source ->
                    SourceRow(
                        name = source.name,
                        subtitle = source.subtitle,
                        letter = source.name.take(1).uppercase(),
                        iconUrl = source.iconUrl,
                        selected = selected == source.id,
                        onClick = { onPick(source.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceRow(
    name: String,
    subtitle: String,
    letter: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Red.copy(alpha = 0.18f) else Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color(0xFF0B0B0F),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2B34)),
                contentAlignment = Alignment.Center,
            ) {
                if (iconUrl != null) {
                    AsyncImage(model = iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(letter ?: "⌂", color = Color(0xFFB4B2A9), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = Color(0xFF888780))
                }
            }
            if (selected) Text("✓", color = Red, fontWeight = FontWeight.Bold)
        }
    }
}

// ------------------------------------------------------------------ source home

/**
 * Home when a CloudStream site is picked: a header like the site's page, then one row per
 * category. "See all" opens more of a category; posters open Nuvio's show page when your add-ons
 * know the title, otherwise the site's own episode list.
 */
@Composable
fun FenrirSourceHomeScreen(
    sourceId: String,
    onOpenDetail: (itemId: String, itemType: String) -> Unit,
    onPlay: (route: String) -> Unit,
    viewModel: FenrirSourceHomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var seeAll by remember { mutableStateOf<FenrirHomeRow?>(null) }
    var titleItem by remember { mutableStateOf<FenrirHomeItem?>(null) }
    var matching by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sourceId) { viewModel.load(sourceId) }

    val openItem: (FenrirHomeItem) -> Unit = { item ->
        if (matching == null) {
            matching = item.key
            scope.launch {
                val meta = viewModel.matchToNuvio(item)
                matching = null
                if (meta != null) onOpenDetail(meta.id, meta.apiType) else titleItem = item
            }
        }
    }

    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        val source = sources?.firstOrNull { it.id == sourceId }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Edge),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = source?.name ?: sourceId,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTheme.colors.TextPrimary,
                        )
                        val tags = listOfNotNull("CloudStream", source?.subtitle?.takeIf { it.isNotBlank() })
                        Text(
                            text = tags.joinToString("  ·  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = NuvioTheme.colors.TextSecondary,
                        )
                    }
                    FenrirSourceButton(sourceName = null, onClick = { viewModel.refreshSources(); picker = true })
                }
            }
            when {
                state.rows.isEmpty() && state.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                state.rows.isEmpty() -> item(key = "failed") {
                    Column(Modifier.padding(horizontal = Edge)) {
                        Text(
                            text = "This source didn't load. It may be down, or its plugin is turned off in Settings > Plugins.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioTheme.colors.TextSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Try again") { viewModel.load(sourceId, force = true) }
                            Pill("Back to Nuvio home") { FenrirSourceHomePick.select(context, null) }
                        }
                    }
                }
                else -> state.rows.forEachIndexed { index, row ->
                    item(key = "row-$index-${row.categoryName}") {
                        SourceRowSection(
                            row = row,
                            busyKey = matching,
                            onItem = openItem,
                            onSeeAll = { seeAll = row },
                        )
                    }
                }
            }
        }

        seeAll?.let { row ->
            SeeAllOverlay(
                sourceId = sourceId,
                row = row,
                viewModel = viewModel,
                busyKey = matching,
                onItem = openItem,
                onDismiss = { seeAll = null },
            )
        }
        titleItem?.let { item ->
            TitleOverlay(
                item = item,
                viewModel = viewModel,
                onPlay = { episode, link, isMovie ->
                    titleItem = null
                    onPlay(fenrirPlayRoute(item.key, item.title, item.poster, episode, link, isMovie))
                },
                onDismiss = { titleItem = null },
            )
        }
    }

    if (picker) {
        FenrirSourcePickerDialog(
            sources = sources,
            selected = sourceId,
            onPick = { id ->
                picker = false
                FenrirSourceHomePick.select(context, id)
            },
            onDismiss = { picker = false },
        )
    }
}

@Composable
private fun SourceRowSection(
    row: FenrirHomeRow,
    busyKey: String?,
    onItem: (FenrirHomeItem) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NuvioTheme.colors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            TextLink("See all", onSeeAll)
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Edge, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(row.items, key = { index, item -> "$index|${item.key}" }) { _, item ->
                PosterCard(
                    item = item,
                    wide = row.horizontalImages,
                    busy = busyKey == item.key,
                    onClick = { onItem(item) },
                    modifier = Modifier.width(if (row.horizontalImages) 220.dp else 130.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(
    item: FenrirHomeItem,
    wide: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 2f / 3f),
            shape = CardDefaults.shape(shape = shape),
            colors = CardDefaults.colors(containerColor = Color(0xFF20212A), focusedContainerColor = Color(0xFF20212A)),
            border = CardDefaults.border(focusedBorder = Border(border = NuvioTheme.focusRing.border(3.dp), shape = shape)),
            scale = CardDefaults.scale(focusedScale = 1.06f),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (item.poster != null) {
                    AsyncImage(model = item.poster, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = NuvioTheme.colors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    )
                }
                item.badge?.let { badge ->
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Red)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                if (busy) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = NuvioTheme.colors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.year?.let {
            Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextSecondary)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TextLink(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = Red,
            focusedContentColor = Color(0xFF0B0B0F),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Pill(label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Red else Soft,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color(0xFF0B0B0F),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            maxLines = 1,
        )
    }
}

/** "See all": the whole category as a grid, loading more pages at the end. */
@Composable
private fun SeeAllOverlay(
    sourceId: String,
    row: FenrirHomeRow,
    viewModel: FenrirSourceHomeViewModel,
    busyKey: String?,
    onItem: (FenrirHomeItem) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val items = remember(row) { mutableStateListOf<FenrirHomeItem>().apply { addAll(row.items) } }
    var page by remember(row) { mutableIntStateOf(1) }
    var hasMore by remember(row) { mutableStateOf(true) }
    var loading by remember(row) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        scope.launch {
            val next = viewModel.morePage(sourceId, row, page + 1)
            val fresh = next.filter { n -> items.none { it.key == n.key } }
            if (fresh.isEmpty()) hasMore = false else { items.addAll(fresh); page += 1 }
            loading = false
        }
    }
    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (row.horizontalImages) 220.dp else 130.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Edge, end = Edge, top = 24.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("◀ Back", onClick = onDismiss)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTheme.colors.TextPrimary,
                    )
                }
            }
            itemsIndexed(items, key = { index, item -> "$index|${item.key}" }) { _, item ->
                PosterCard(item, row.horizontalImages, busyKey == item.key, onClick = { onItem(item) })
            }
            if (hasMore) {
                item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                    LaunchedEffect(items.size) { loadMore() }
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            }
        }
    }
}

/** The site's own page for a title your add-ons don't know: episodes, then links for one. */
@Composable
private fun TitleOverlay(
    item: FenrirHomeItem,
    viewModel: FenrirSourceHomeViewModel,
    onPlay: (FenrirSourceEpisode, FenrirSourceLink, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var episodes by remember(item) { mutableStateOf<List<FenrirSourceEpisode>?>(null) }
    var picked by remember(item) { mutableStateOf<FenrirSourceEpisode?>(null) }
    var links by remember(item) { mutableStateOf<List<FenrirSourceLink>?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler {
        if (picked != null) { picked = null; links = null } else onDismiss()
    }
    LaunchedEffect(item) { episodes = viewModel.episodes(item) }
    val isMovie = item.isMovie || episodes?.size == 1

    fun pick(episode: FenrirSourceEpisode) {
        picked = episode
        links = null
        scope.launch { links = viewModel.links(episode) }
    }

    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        Row(Modifier.fillMaxSize().padding(start = Edge, end = Edge, top = 32.dp, bottom = 24.dp)) {
            Column(Modifier.width(200.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF20212A)),
                ) {
                    item.poster?.let {
                        AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NuvioTheme.colors.TextPrimary)
                Text(
                    text = listOfNotNull(item.sourceName, item.year?.toString(), item.badge).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Pill("◀ Back", onClick = onDismiss)
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = if (isMovie) "Play" else "Episodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTheme.colors.TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                when (val list = episodes) {
                    null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    else -> if (list.isEmpty()) {
                        Text("This source has no episodes for this title right now.", color = NuvioTheme.colors.TextSecondary)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(list, key = { it.key }) { episode ->
                                ListRow(
                                    title = if (isMovie) "Movie" else episode.name,
                                    selected = picked?.key == episode.key,
                                    onClick = { pick(episode) },
                                )
                            }
                        }
                    }
                }
            }
            if (picked != null) {
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        text = "Links",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTheme.colors.TextPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    when (val list = links) {
                        null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                        else -> if (list.isEmpty()) {
                            Text("No links found for this one. Try another episode or source.", color = NuvioTheme.colors.TextSecondary)
                        } else {
                            val firstLink = remember(list) { FocusRequester() }
                            LaunchedEffect(list) { runCatching { firstLink.requestFocus() } }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(list, key = { index, link -> "$index|${link.url}" }) { index, link ->
                                    ListRow(
                                        title = link.label,
                                        subtitle = link.detail,
                                        selected = false,
                                        onClick = { picked?.let { onPlay(it, link, isMovie) } },
                                        modifier = if (index == 0) Modifier.focusRequester(firstLink) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ListRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Red.copy(alpha = 0.2f) else Color(0xFF1C1D26),
            focusedContainerColor = Color.White,
            contentColor = if (selected) Red else Color.White,
            focusedContentColor = Color(0xFF0B0B0F),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = Color(0xFF888780))
            }
        }
    }
}
