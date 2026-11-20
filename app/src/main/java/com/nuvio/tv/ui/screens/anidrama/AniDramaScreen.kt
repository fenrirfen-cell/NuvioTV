package com.nuvio.tv.ui.screens.anidrama

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * The Ani-drama tab on the TV: Otaku-style anime browsing (AniList) and K-/C-/J-drama browsing
 * (TMDB), like the Fenrir phone app. Posters open Nuvio's own show page.
 */
@Composable
fun AniDramaScreen(
    onOpenDetail: (itemId: String, itemType: String) -> Unit,
    viewModel: AniDramaViewModel = hiltViewModel(),
) {
    val addons by viewModel.addons.collectAsStateWithLifecycle()
    var regionIndex by rememberSaveable { mutableIntStateOf(0) }
    var animeChipIndex by rememberSaveable { mutableIntStateOf(0) }
    var dramaChipIndex by rememberSaveable { mutableIntStateOf(0) }
    var audioIndex by rememberSaveable { mutableIntStateOf(AnimeAudioFilter.Both.ordinal) }
    var airingExpanded by rememberSaveable { mutableStateOf(false) }

    val region = AniDramaRegion.entries[regionIndex]
    val animeChip = AnimeChip.entries[animeChipIndex]
    val dramaChip = DramaChip.entries[dramaChipIndex]
    val audio = AnimeAudioFilter.entries[audioIndex]

    val openItem: (AniDramaItem) -> Unit = { item ->
        val target = item.target(addons)
        onOpenDetail(target.id, target.type)
    }

    BackHandler(enabled = airingExpanded) { airingExpanded = false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .padding(top = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = AniDramaEdge, end = AniDramaEdge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ani-drama",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NuvioTheme.colors.TextPrimary,
            )
            Spacer(Modifier.width(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AniDramaRegion.entries.forEach { option ->
                    AniDramaChip(
                        label = option.label,
                        selected = option == region,
                        onClick = {
                            if (option != region) {
                                regionIndex = option.ordinal
                                airingExpanded = false
                            }
                        },
                    )
                }
            }
            if (region == AniDramaRegion.Anime) {
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimeAudioFilter.entries.forEach { option ->
                        AniDramaChip(
                            label = option.label,
                            selected = option == audio,
                            onClick = { audioIndex = option.ordinal },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val chipLabels = if (region == AniDramaRegion.Anime) AnimeChip.entries.map { it.label } else DramaChip.entries.map { it.label }
        AniDramaChipRow(
            labels = chipLabels,
            selected = if (region == AniDramaRegion.Anime) animeChip.label else dramaChip.label,
            onSelect = { label ->
                airingExpanded = false
                val index = chipLabels.indexOf(label).coerceAtLeast(0)
                if (region == AniDramaRegion.Anime) animeChipIndex = index else dramaChipIndex = index
            },
        )
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (region == AniDramaRegion.Anime) {
                AnimeContent(animeChip, audio, airingExpanded, { airingExpanded = it }, openItem)
            } else {
                DramaContent(region, dramaChip, airingExpanded, { airingExpanded = it }, openItem)
            }
        }
    }
}

// ---------------------------------------------------------------- Anime

@Composable
private fun AnimeContent(
    chip: AnimeChip,
    audio: AnimeAudioFilter,
    airingExpanded: Boolean,
    onExpandAiring: (Boolean) -> Unit,
    onItemClick: (AniDramaItem) -> Unit,
) {
    when (chip) {
        AnimeChip.Airing -> if (airingExpanded) {
            AnimeGrid(
                query = AniListClient.MediaQuery(sort = "LATEST_EPISODE", status = "RELEASING"),
                audio = audio,
                grouping = AniDramaGrouping.Week,
                loader = { page -> if (page == 1) AnimeRepository.airingLatest() else AniDramaPage(emptyList(), false) },
                title = "Airing now · latest episode first",
                onBack = { onExpandAiring(false) },
                onItemClick = onItemClick,
            )
        } else {
            AnimeOverview(audio = audio, onSeeAllAiring = { onExpandAiring(true) }, onItemClick = onItemClick)
        }
        AnimeChip.Trending -> AnimeGrid(AniListClient.MediaQuery(sort = "TRENDING_DESC"), audio, onItemClick = onItemClick)
        AnimeChip.Popular -> AnimeGrid(AniListClient.MediaQuery(sort = "POPULARITY_DESC", ranked = true), audio, onItemClick = onItemClick)
        AnimeChip.Top100 -> AnimeGrid(AniListClient.MediaQuery(sort = "SCORE_DESC", ranked = true), audio, maxItems = 100, onItemClick = onItemClick)
        AnimeChip.Upcoming -> AnimeGrid(
            AniListClient.MediaQuery(sort = "START_DATE", status = "NOT_YET_RELEASED", startGreater = AnimeRepository.fuzzyToday()),
            audio,
            grouping = AniDramaGrouping.MonthSoonest,
            onItemClick = onItemClick,
        )
        AnimeChip.Seasons -> AnimeSeasons(audio, onItemClick)
        AnimeChip.Genres -> AnimeGenres(audio, onItemClick)
        AnimeChip.Calendar -> AniDramaWeekView(
            key = "anime-calendar",
            loader = { start, days -> AnimeRepository.schedule(start, days) },
            filter = { AnimeRepository.applyAudioFilter(listOf(it.item), audio).isNotEmpty() },
            showDubBadge = true,
            onItemClick = onItemClick,
        )
    }
}

@Composable
private fun AnimeOverview(
    audio: AnimeAudioFilter,
    onSeeAllAiring: () -> Unit,
    onItemClick: (AniDramaItem) -> Unit,
) {
    val airing = rememberAniDramaPagedList(key = "anime-overview-airing") { page ->
        AnimeRepository.page(AniListClient.MediaQuery(sort = "POPULARITY_DESC", status = "RELEASING"), page)
    }
    val (schedule, _) = rememberAniDramaLoad("anime-overview-schedule") { AnimeRepository.weekSchedule() }
    val todayIndex = remember { AniDramaTime.weekdayOfIso(AniDramaTime.todayIso()) ?: 0 }
    val todayItems = remember(schedule.value, audio) {
        schedule.value.orEmpty()
            .filter { it.weekday == todayIndex }
            .sortedBy { it.sortKey }
            .map { entry -> entry.item.copy(caption = entry.episodeLabel.substringBefore(" ·")) }
            .let { AnimeRepository.applyAudioFilter(it, audio) }
            .distinctBy { it.key }
    }
    val trending = rememberAniDramaPagedList(key = "anime-overview-trending") { page ->
        if (page == 1) AnimeRepository.page(AniListClient.MediaQuery(sort = "TRENDING_DESC"), 1) else AniDramaPage(emptyList(), false)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item(key = "airing") {
            AniDramaShelf(
                title = "Airing now",
                items = AnimeRepository.applyAudioFilter(airing.items, audio),
                loading = airing.loading,
                showDubBadge = true,
                onSeeAll = onSeeAllAiring,
                onItemClick = onItemClick,
            )
        }
        item(key = "today") {
            AniDramaShelf(
                title = "Airing today",
                items = todayItems,
                loading = schedule.loading,
                showDubBadge = true,
                onItemClick = onItemClick,
            )
        }
        item(key = "trending") {
            AniDramaShelf(
                title = "Trending",
                items = AnimeRepository.applyAudioFilter(trending.items, audio),
                loading = trending.loading,
                showDubBadge = true,
                onItemClick = onItemClick,
            )
        }
    }
}

@Composable
private fun AnimeGrid(
    query: AniListClient.MediaQuery,
    audio: AnimeAudioFilter,
    maxItems: Int = 600,
    grouping: AniDramaGrouping = AniDramaGrouping.None,
    loader: (suspend (Int) -> AniDramaPage?)? = null,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    onItemClick: (AniDramaItem) -> Unit,
) {
    val list = rememberAniDramaPagedList(key = query, maxItems = maxItems) { page ->
        loader?.invoke(page) ?: AnimeRepository.page(query, page)
    }
    AniDramaGrid(
        items = AnimeRepository.applyAudioFilter(list.items, audio),
        list = list,
        grouping = grouping,
        showDubBadge = true,
        title = title,
        onBack = onBack,
        header = header,
        onItemClick = onItemClick,
    )
}

@Composable
private fun AnimeSeasons(audio: AnimeAudioFilter, onItemClick: (AniDramaItem) -> Unit) {
    val current = remember { AniDramaTime.currentAnimeSeason() }
    var seasonIndex by rememberSaveable { mutableIntStateOf(current.first.ordinal) }
    var year by rememberSaveable { mutableIntStateOf(current.second) }
    val season = AnimeSeason.entries[seasonIndex]
    AnimeGrid(
        query = AniListClient.MediaQuery(sort = "POPULARITY_DESC", season = season, seasonYear = year),
        audio = audio,
        header = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AniDramaChip(label = "◀ ${year - 1}", selected = false, onClick = { year-- })
                Text(
                    text = "${season.label} $year",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTheme.colors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (year < current.second + 1) {
                    AniDramaChip(label = "${year + 1} ▶", selected = false, onClick = { year++ })
                }
                Spacer(Modifier.width(16.dp))
                AnimeSeason.entries.forEach { option ->
                    AniDramaChip(option.label, option == season, onClick = { seasonIndex = option.ordinal })
                }
            }
        },
        onItemClick = onItemClick,
    )
}

@Composable
private fun AnimeGenres(audio: AnimeAudioFilter, onItemClick: (AniDramaItem) -> Unit) {
    var genre by rememberSaveable { mutableStateOf(AniListClient.genres.first()) }
    var newest by rememberSaveable { mutableStateOf(true) }
    AnimeGrid(
        query = if (newest) {
            AniListClient.MediaQuery(sort = "START_DATE_DESC", genre = genre, startLesser = AnimeRepository.fuzzyToday(1))
        } else {
            AniListClient.MediaQuery(sort = "POPULARITY_DESC", genre = genre)
        },
        audio = audio,
        grouping = if (newest) AniDramaGrouping.MonthNewest else AniDramaGrouping.None,
        header = { BrowseHeader(AniListClient.genres, genre, { genre = it }, newest, { newest = it }) },
        onItemClick = onItemClick,
    )
}

// ---------------------------------------------------------------- Dramas

@Composable
private fun DramaContent(
    region: AniDramaRegion,
    chip: DramaChip,
    airingExpanded: Boolean,
    onExpandAiring: (Boolean) -> Unit,
    onItemClick: (AniDramaItem) -> Unit,
) {
    when (chip) {
        DramaChip.AiringNow -> if (airingExpanded) {
            DramaGrid(
                region = region,
                filter = DramaRepository.Filter(DramaChip.AiringNow),
                grouping = AniDramaGrouping.Week,
                title = "Airing now · latest episode first",
                onBack = { onExpandAiring(false) },
                onItemClick = onItemClick,
            )
        } else {
            DramaOverview(region, { onExpandAiring(true) }, onItemClick)
        }
        DramaChip.New, DramaChip.Upcoming, DramaChip.Popular, DramaChip.TopRated -> DramaGrid(
            region = region,
            filter = DramaRepository.Filter(chip),
            grouping = when (chip) {
                DramaChip.New -> AniDramaGrouping.MonthNewest
                DramaChip.Upcoming -> AniDramaGrouping.MonthSoonest
                else -> AniDramaGrouping.None
            },
            onItemClick = onItemClick,
        )
        DramaChip.Genres -> DramaGenres(region, onItemClick)
        DramaChip.Networks -> DramaNetworks(region, onItemClick)
        DramaChip.Calendar -> AniDramaWeekView(
            key = "drama-calendar-$region",
            loader = { start, days -> DramaRepository.schedule(region, start, days) },
            filter = { true },
            showDubBadge = false,
            onItemClick = onItemClick,
        )
    }
}

@Composable
private fun DramaOverview(
    region: AniDramaRegion,
    onSeeAllAiring: () -> Unit,
    onItemClick: (AniDramaItem) -> Unit,
) {
    val (schedule, _) = rememberAniDramaLoad("drama-overview-schedule-$region") { DramaRepository.weekSchedule(region) }
    val todayIso = remember { AniDramaTime.todayIso() }
    val monthStart = remember { todayIso.take(8) + "01" }
    val monthDays = remember {
        AniDramaTime.daysInMonth(todayIso.take(4).toIntOrNull() ?: 2026, todayIso.substring(5, 7).toIntOrNull() ?: 1)
    }
    val (month, _) = rememberAniDramaLoad("drama-overview-month-$region-$monthStart") {
        DramaRepository.schedule(region, monthStart, monthDays)
    }
    val (comingSoon, _) = rememberAniDramaLoad("drama-overview-soon-$region") { DramaRepository.comingSoon(region) }
    val airing = rememberAniDramaPagedList(key = "drama-overview-airing-$region") { page ->
        DramaRepository.page(region, DramaRepository.Filter(DramaChip.AiringNow), page)
    }

    /** Latest released episode per show, newest first, with the real air date on the poster. */
    fun released(entries: List<AniDramaScheduleEntry>?): List<AniDramaItem> =
        entries.orEmpty()
            .filter { it.dateIso <= todayIso }
            .sortedWith(compareByDescending<AniDramaScheduleEntry> { it.dateIso }.thenBy { it.sortKey })
            .distinctBy { it.item.key }
            .map { entry -> entry.item.copy(caption = "EP ${entry.episode} · ${AniDramaTime.shortDayLabel(entry.dateIso)}") }

    val releasedThisWeek = remember(schedule.value) { released(schedule.value) }
    val releasedThisMonth = remember(month.value) { released(month.value) }
    val premieredThisMonth = remember(month.value) {
        month.value.orEmpty()
            .filter { it.isPremiere && it.dateIso <= todayIso }
            .sortedByDescending { it.dateIso }
            .distinctBy { it.item.key }
            .map { it.item.copy(caption = "Premiered ${AniDramaTime.shortDayLabel(it.dateIso)}") }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item(key = "released-week") {
            AniDramaShelf("Released this week", releasedThisWeek, onItemClick, loading = schedule.loading)
        }
        item(key = "released-month") {
            AniDramaShelf("Released this month", releasedThisMonth, onItemClick, loading = month.loading)
        }
        item(key = "premiered-month") {
            AniDramaShelf("Premiered this month", premieredThisMonth, onItemClick, loading = month.loading)
        }
        item(key = "coming-soon") {
            AniDramaShelf("Coming soon", comingSoon.value.orEmpty(), onItemClick, loading = comingSoon.loading)
        }
        item(key = "airing") {
            AniDramaShelf("Airing now", airing.items, onItemClick, loading = airing.loading, onSeeAll = onSeeAllAiring)
        }
    }
}

@Composable
private fun DramaGrid(
    region: AniDramaRegion,
    filter: DramaRepository.Filter,
    grouping: AniDramaGrouping = AniDramaGrouping.None,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    onItemClick: (AniDramaItem) -> Unit,
) {
    val key = "$region|$filter|$grouping"
    val list = rememberAniDramaPagedList(key = key) { page ->
        if (filter.chip == DramaChip.AiringNow && grouping == AniDramaGrouping.Week) {
            if (page == 1) DramaRepository.airingLatest(region) else AniDramaPage(emptyList(), false)
        } else {
            DramaRepository.page(region, filter, page)
        }
    }
    AniDramaGrid(
        items = list.items,
        list = list,
        grouping = grouping,
        showDubBadge = false,
        title = title,
        onBack = onBack,
        header = header,
        onItemClick = onItemClick,
    )
}

@Composable
private fun DramaGenres(region: AniDramaRegion, onItemClick: (AniDramaItem) -> Unit) {
    val (genres, retry) = rememberAniDramaLoad("drama-genres-$region") { DramaRepository.genres(region).takeIf { it.isNotEmpty() } }
    var selectedName by rememberSaveable(region) { mutableStateOf<String?>(null) }
    var newest by rememberSaveable(region) { mutableStateOf(true) }
    val options = genres.value.orEmpty()
    val selected = options.firstOrNull { it.name == selectedName } ?: options.firstOrNull()
    when {
        selected != null -> DramaGrid(
            region = region,
            filter = DramaRepository.Filter(
                chip = DramaChip.Genres,
                genreId = selected.id.takeIf { it > 0 },
                keywordId = (-selected.id).takeIf { it > 0 },
                newest = newest,
            ),
            grouping = if (newest) AniDramaGrouping.MonthNewest else AniDramaGrouping.None,
            header = { BrowseHeader(options.map { it.name }, selected.name, { selectedName = it }, newest, { newest = it }) },
            onItemClick = onItemClick,
        )
        genres.loading -> AniDramaLoading(Modifier.fillMaxWidth().height(200.dp))
        else -> AniDramaMessage("Couldn't load genres.", Modifier.padding(horizontal = AniDramaEdge), onRetry = retry)
    }
}

@Composable
private fun DramaNetworks(region: AniDramaRegion, onItemClick: (AniDramaItem) -> Unit) {
    val (networks, retry) = rememberAniDramaLoad("drama-networks-$region") { DramaRepository.networks(region).takeIf { it.isNotEmpty() } }
    var selectedName by rememberSaveable(region) { mutableStateOf<String?>(null) }
    var newest by rememberSaveable(region) { mutableStateOf(true) }
    val options = networks.value.orEmpty()
    val selected = options.firstOrNull { it.name == selectedName } ?: options.firstOrNull()
    when {
        selected != null -> DramaGrid(
            region = region,
            filter = DramaRepository.Filter(chip = DramaChip.Networks, networkId = selected.id, newest = newest),
            grouping = if (newest) AniDramaGrouping.MonthNewest else AniDramaGrouping.None,
            header = { BrowseHeader(options.map { it.name }, selected.name, { selectedName = it }, newest, { newest = it }) },
            onItemClick = onItemClick,
        )
        networks.loading -> AniDramaLoading(Modifier.fillMaxWidth().height(200.dp))
        else -> AniDramaMessage("Couldn't load networks.", Modifier.padding(horizontal = AniDramaEdge), onRetry = retry)
    }
}

// ---------------------------------------------------------------- Shared views

/** Genre / network chips with a Newest | Popular switch. */
@Composable
private fun BrowseHeader(
    labels: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    newest: Boolean,
    onNewestChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AniDramaChip("Newest", newest, onClick = { onNewestChange(true) })
            AniDramaChip("Popular", !newest, onClick = { onNewestChange(false) })
        }
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(labels.size, key = { labels[it] }) { index ->
                val label = labels[index]
                AniDramaChip(label, label == selected, onClick = { onSelect(label) })
            }
        }
    }
}

/** Poster grid, split into sections, that loads more as you reach the end. */
@Composable
private fun AniDramaGrid(
    items: List<AniDramaItem>,
    list: AniDramaPagedList,
    grouping: AniDramaGrouping,
    showDubBadge: Boolean,
    title: String?,
    onBack: (() -> Unit)?,
    header: (@Composable () -> Unit)?,
    onItemClick: (AniDramaItem) -> Unit,
) {
    // Built on every pass: the paged list grows in place, so a remembered copy would go stale.
    val sections = AniDramaSections.build(items.toList(), grouping)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(AniDramaTileWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = AniDramaEdge, end = AniDramaEdge, top = 8.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (header != null) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        if (title != null) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        AniDramaChip(label = "◀ Back", selected = false, onClick = onBack)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTheme.colors.TextPrimary,
                    )
                }
            }
        }
        sections.forEach { section ->
            if (section.title.isNotBlank()) {
                item(key = "section-${section.key}", span = { GridItemSpan(maxLineSpan) }) {
                    AniDramaSectionHeader(section.title, section.note)
                }
            }
            items(section.items, key = { "${section.key}/${it.key}" }) { item ->
                AniDramaPosterTile(item = item, onClick = { onItemClick(item) }, showDubBadge = showDubBadge)
            }
        }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
            when {
                list.failed -> AniDramaMessage("Couldn't load this list.", onRetry = { list.retry() })
                list.hasMore -> {
                    // Reaching the end loads the next page.
                    LaunchedEffect(list.items.size) { list.loadMore() }
                    AniDramaLoading(Modifier.fillMaxWidth().height(120.dp))
                }
                list.loading -> AniDramaLoading(Modifier.fillMaxWidth().height(120.dp))
                items.isEmpty() -> AniDramaMessage("Nothing here right now.")
                else -> Spacer(Modifier.height(1.dp))
            }
        }
    }
}

/**
 * Calendar on the TV: one week (Monday to Sunday) with a row of episodes per day,
 * and chips to move a week back or forward.
 */
@Composable
private fun AniDramaWeekView(
    key: String,
    loader: suspend (String, Int) -> List<AniDramaScheduleEntry>,
    filter: (AniDramaScheduleEntry) -> Boolean,
    showDubBadge: Boolean,
    onItemClick: (AniDramaItem) -> Unit,
) {
    var weekOffset by rememberSaveable(key) { mutableIntStateOf(0) }
    val thisMonday = remember { AniDramaTime.mondayOfThisWeek() }
    val start = AniDramaTime.addDays(thisMonday, weekOffset * 7)
    val (state, retry) = rememberAniDramaLoad("$key|$start") { loader(start, 7) }
    val today = remember { AniDramaTime.todayIso() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "week-nav") {
            Row(
                modifier = Modifier.padding(horizontal = AniDramaEdge),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AniDramaChip("◀ Previous week", false, onClick = { weekOffset-- })
                Text(
                    text = "Week of ${AniDramaTime.shortDayLabel(start)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTheme.colors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                AniDramaChip("Next week ▶", false, onClick = { weekOffset++ })
                if (weekOffset != 0) AniDramaChip("This week", true, onClick = { weekOffset = 0 })
            }
        }
        when {
            state.loading -> item(key = "loading") { AniDramaLoading(Modifier.fillMaxWidth().height(200.dp)) }
            state.failed -> item(key = "failed") {
                AniDramaMessage("Couldn't load the calendar.", Modifier.padding(horizontal = AniDramaEdge), onRetry = retry)
            }
            else -> {
                val entries = state.value.orEmpty().filter(filter)
                (0 until 7).forEach { offset ->
                    val dayIso = AniDramaTime.addDays(start, offset)
                    val dayItems = entries
                        .filter { it.dateIso == dayIso }
                        .sortedBy { it.sortKey }
                        .distinctBy { "${it.item.key}#${it.episode}" }
                        .map { entry ->
                            entry.item.copy(
                                key = "${entry.item.key}#${entry.episode}",
                                caption = listOfNotNull(
                                    if (entry.isPremiere) "Premiere" else null,
                                    "EP ${entry.episode}",
                                    entry.timeLabel,
                                ).joinToString(" · "),
                            )
                        }
                    item(key = "day-$dayIso") {
                        AniDramaShelf(
                            title = AniDramaTime.longDayLabel(dayIso) + if (dayIso == today) "  ·  Today" else "",
                            items = dayItems,
                            onItemClick = onItemClick,
                            showDubBadge = showDubBadge,
                            emptyText = "No episodes.",
                        )
                    }
                }
            }
        }
    }
}
