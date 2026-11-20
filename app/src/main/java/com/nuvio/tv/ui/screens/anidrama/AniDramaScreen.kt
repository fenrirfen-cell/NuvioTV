package com.nuvio.tv.ui.screens.anidrama

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * The Ani-drama tab on the TV: Otaku-style anime browsing (AniList) and K-/C-/J-drama browsing
 * (TMDB), like the Fenrir phone app. Posters open Nuvio's own show page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AniDramaScreen(
    onOpenDetail: (itemId: String, itemType: String) -> Unit,
    addonRows: List<CatalogRow> = emptyList(),
    onOpenCatalog: ((CatalogRow) -> Unit)? = null,
    viewModel: AniDramaViewModel = hiltViewModel(),
) {
    val addons by viewModel.addons.collectAsStateWithLifecycle()
    val libraryIds by viewModel.libraryIds.collectAsStateWithLifecycle()
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

    val extras = AniDramaExtras(
        addonRows = addonRows,
        libraryIds = libraryIds,
        onOpenMeta = { meta -> onOpenDetail(meta.id, meta.apiType) },
        onOpenCatalog = onOpenCatalog,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .padding(top = 20.dp),
    ) {
        Text(
            text = "Ani-drama",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(horizontal = AniDramaEdge, vertical = 4.dp),
        )
        AniDramaSegmented(
            options = AniDramaRegion.entries.map { it.label },
            selectedIndex = regionIndex,
            onSelect = { index ->
                if (index != regionIndex) {
                    regionIndex = index
                    airingExpanded = false
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = AniDramaEdge - 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        val chipLabels = if (region == AniDramaRegion.Anime) AnimeChip.entries.map { it.label } else DramaChip.entries.map { it.label }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AniDramaEdge),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chipLabels.forEachIndexed { index, label ->
                val selectedIndex = if (region == AniDramaRegion.Anime) animeChipIndex else dramaChipIndex
                AniDramaChip(
                    label = label,
                    selected = index == selectedIndex,
                    onClick = {
                        airingExpanded = false
                        if (region == AniDramaRegion.Anime) animeChipIndex = index else dramaChipIndex = index
                    },
                )
            }
        }
        if (region == AniDramaRegion.Anime) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = AniDramaEdge, end = AniDramaEdge, top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                AniDramaSegmented(
                    options = AnimeAudioFilter.entries.map { it.label },
                    selectedIndex = audioIndex,
                    onSelect = { audioIndex = it },
                    compact = true,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (region == AniDramaRegion.Anime) {
                AnimeContent(animeChip, audio, airingExpanded, { airingExpanded = it }, openItem, extras)
            } else {
                DramaContent(region, dramaChip, airingExpanded, { airingExpanded = it }, openItem, extras)
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
    extras: AniDramaExtras,
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
            AnimeOverview(audio = audio, onSeeAllAiring = { onExpandAiring(true) }, onItemClick = onItemClick, extras = extras)
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
        AnimeChip.Calendar -> AniDramaCalendarView(
            key = "anime-calendar",
            loader = { start, days -> AnimeRepository.schedule(start, days) },
            filter = { AnimeRepository.applyAudioFilter(listOf(it.item), audio).isNotEmpty() },
            showDubBadge = true,
            libraryIds = extras.libraryIds,
            onItemClick = onItemClick,
        )
    }
}

@Composable
private fun AnimeOverview(
    audio: AnimeAudioFilter,
    onSeeAllAiring: () -> Unit,
    onItemClick: (AniDramaItem) -> Unit,
    extras: AniDramaExtras,
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
    OverviewColumn(AniDramaRegion.Anime, extras) {
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AniDramaStepButton(left = true, description = "Previous year", onClick = { year-- })
                    Text(
                        text = "${season.label} $year",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTheme.colors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    AniDramaStepButton(left = false, description = "Next year", onClick = { if (year < current.second + 1) year++ })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimeSeason.entries.forEach { option ->
                        AniDramaChip(option.label, option == season, onClick = { seasonIndex = option.ordinal })
                    }
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
    extras: AniDramaExtras,
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
            DramaOverview(region, { onExpandAiring(true) }, onItemClick, extras)
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
        DramaChip.Calendar -> AniDramaCalendarView(
            key = "drama-calendar-$region",
            loader = { start, days -> DramaRepository.schedule(region, start, days) },
            filter = { true },
            showDubBadge = false,
            libraryIds = extras.libraryIds,
            onItemClick = onItemClick,
        )
    }
}

@Composable
private fun DramaOverview(
    region: AniDramaRegion,
    onSeeAllAiring: () -> Unit,
    onItemClick: (AniDramaItem) -> Unit,
    extras: AniDramaExtras,
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

    OverviewColumn(region, extras) {
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
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(labels.size, key = { labels[it] }) { index ->
                val label = labels[index]
                AniDramaChip(label, label == selected, onClick = { onSelect(label) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AniDramaSegmented(
                options = listOf("Newest", "Popular"),
                selectedIndex = if (newest) 0 else 1,
                onSelect = { onNewestChange(it == 0) },
                compact = true,
            )
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
        if (title != null) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        AniDramaStepButton(left = true, description = "Back", onClick = onBack)
                        Spacer(Modifier.width(8.dp))
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
        if (header != null) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
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
                items.isEmpty() -> AniDramaMessage("Nothing found.")
                else -> Spacer(Modifier.height(1.dp))
            }
        }
    }
}

/** Things the tab gets from the rest of the app: your add-on rows and library. */
internal class AniDramaExtras(
    val addonRows: List<CatalogRow>,
    val libraryIds: Set<String>,
    val onOpenMeta: (MetaPreview) -> Unit,
    val onOpenCatalog: ((CatalogRow) -> Unit)?,
)

/**
 * Overview list like the phone: the tab's own shelves, then "From your add-ons" with the
 * anime / Asian-drama rows from your add-ons that belong to this side of the tab.
 */
@Composable
private fun OverviewColumn(
    region: AniDramaRegion,
    extras: AniDramaExtras,
    shelves: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val rows = remember(extras.addonRows, region) { AniDramaRows.rowsFor(region, extras.addonRows) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        shelves()
        if (rows.isNotEmpty()) {
            item(key = "addons-title") {
                Text(
                    text = "From your add-ons",
                    style = MaterialTheme.typography.labelLarge,
                    color = NuvioTheme.colors.Secondary,
                    modifier = Modifier.padding(horizontal = AniDramaEdge),
                )
            }
            rows.forEach { row ->
                item(key = "addon-${row.addonId}-${row.rawType}-${row.catalogId}") {
                    AddonRowShelf(row, extras)
                }
            }
        }
    }
}

@Composable
private fun AddonRowShelf(row: CatalogRow, extras: AniDramaExtras) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AniDramaEdge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.catalogName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTheme.colors.TextPrimary,
                )
                Text(
                    text = row.addonName,
                    style = MaterialTheme.typography.labelSmall,
                    color = NuvioTheme.colors.TextSecondary,
                )
            }
            extras.onOpenCatalog?.let { open -> AniDramaTextButton(label = "See all", onClick = { open(row) }) }
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = AniDramaEdge, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(row.items.size, key = { "${row.items[it].id}#$it" }) { index ->
                val meta = row.items[index]
                AniDramaMetaTile(
                    title = meta.name,
                    poster = meta.poster,
                    onClick = { extras.onOpenMeta(meta) },
                    modifier = Modifier.width(AniDramaTileWidth),
                )
            }
        }
    }
}
