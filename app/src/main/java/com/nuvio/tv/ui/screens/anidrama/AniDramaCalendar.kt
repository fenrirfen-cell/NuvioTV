package com.nuvio.tv.ui.screens.anidrama

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.theme.NuvioTheme

private val PremiereColor = Color(0xFFD85A30)

/**
 * Month calendar (with a Week view) of episodes and premieres, like the phone app:
 * pick a day to see what airs; "My shows" keeps only shows in your library.
 */
@Composable
internal fun AniDramaCalendarView(
    key: String,
    loader: suspend (startIso: String, days: Int) -> List<AniDramaScheduleEntry>,
    filter: (AniDramaScheduleEntry) -> Boolean,
    showDubBadge: Boolean,
    libraryIds: Set<String>,
    onItemClick: (AniDramaItem) -> Unit,
) {
    val todayIso = remember { AniDramaTime.todayIso() }
    var year by rememberSaveable(key) { mutableIntStateOf(todayIso.take(4).toIntOrNull() ?: 2026) }
    var month by rememberSaveable(key) { mutableIntStateOf(todayIso.substring(5, 7).toIntOrNull() ?: 1) }
    var selectedIso by rememberSaveable(key) { mutableStateOf(todayIso) }
    var weekView by rememberSaveable(key) { mutableStateOf(false) }
    var myShowsOnly by rememberSaveable(key) { mutableStateOf(false) }

    val monthStart = AniDramaTime.firstOfMonth(year, month)
    val monthDays = AniDramaTime.daysInMonth(year, month)
    val weekStart = remember(selectedIso) {
        val day = AniDramaTime.epochDay(selectedIso) ?: 0L
        AniDramaTime.isoFromEpochDay(day - AniDramaTime.weekdayOfEpochDay(day))
    }
    val rangeStart = if (weekView) weekStart else monthStart
    val rangeDays = if (weekView) 7 else monthDays
    val (state, retry) = rememberAniDramaLoad("$key|$rangeStart|$rangeDays") { loader(rangeStart, rangeDays) }

    val entries = state.value.orEmpty()
        .filter(filter)
        .filter { !myShowsOnly || it.item.matchIds.any { id -> id in libraryIds } }
        .distinctBy { it.item.key + "#" + it.episode + "#" + it.dateIso }
    val byDay = entries.groupBy { it.dateIso }
    val dayEntries = byDay[selectedIso].orEmpty().sortedBy { it.sortKey }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AniDramaEdge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!weekView) {
                    AniDramaStepButton(left = true, description = "Previous month", onClick = {
                        if (month == 1) { month = 12; year-- } else month--
                        selectedIso = AniDramaTime.firstOfMonth(year, month)
                    })
                    Text(
                        text = "${AniDramaTime.monthName(month)} $year",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTheme.colors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    AniDramaStepButton(left = false, description = "Next month", onClick = {
                        if (month == 12) { month = 1; year++ } else month++
                        selectedIso = AniDramaTime.firstOfMonth(year, month)
                    })
                } else {
                    Text(
                        text = "Week of ${AniDramaTime.longDayLabel(weekStart).substringAfter(", ")}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTheme.colors.TextPrimary,
                    )
                }
                Spacer(Modifier.weight(1f))
                AniDramaSegmented(
                    options = listOf("All", "My shows"),
                    selectedIndex = if (myShowsOnly) 1 else 0,
                    onSelect = { myShowsOnly = it == 1 },
                    compact = true,
                )
            }
        }
        item(key = "grid") {
            if (weekView) {
                WeekStrip(weekStart, selectedIso, todayIso, byDay) { selectedIso = it }
            } else {
                MonthGrid(monthStart, monthDays, selectedIso, todayIso, byDay) { selectedIso = it }
            }
        }
        item(key = "legend") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AniDramaEdge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(NuvioTheme.colors.TextSecondary)
                Text(" New episodes   ", style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextSecondary)
                Dot(PremiereColor)
                Text(" Premiere", style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextSecondary)
                Spacer(Modifier.weight(1f))
                AniDramaTextButton(
                    label = if (weekView) "Month view" else "Week view",
                    onClick = { weekView = !weekView },
                )
            }
        }
        item(key = "day-title") {
            Text(
                text = AniDramaTime.longDayLabel(selectedIso),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = NuvioTheme.colors.TextPrimary,
                modifier = Modifier.padding(horizontal = AniDramaEdge, vertical = 2.dp),
            )
        }
        when {
            state.loading -> item(key = "loading") { AniDramaLoading(Modifier.fillMaxWidth().height(140.dp)) }
            state.failed -> item(key = "failed") {
                AniDramaMessage("Couldn't load the calendar.", Modifier.padding(horizontal = AniDramaEdge), onRetry = retry)
            }
            dayEntries.isEmpty() -> item(key = "empty") {
                AniDramaMessage(
                    if (myShowsOnly) "None of your shows air on this day." else "Nothing airs on this day.",
                    Modifier.padding(horizontal = AniDramaEdge),
                )
            }
            else -> items(dayEntries, key = { it.item.key + "#" + it.episode + "#" + it.dateIso }) { entry ->
                CalendarRow(entry = entry, showDubBadge = showDubBadge, onClick = { onItemClick(entry.item) })
            }
        }
    }
}

@Composable
private fun MonthGrid(
    monthStart: String,
    days: Int,
    selectedIso: String,
    todayIso: String,
    byDay: Map<String, List<AniDramaScheduleEntry>>,
    onSelect: (String) -> Unit,
) {
    val leading = AniDramaTime.weekdayOfIso(monthStart) ?: 0
    val cells = leading + days
    val rows = (cells + 6) / 7
    Column(Modifier.fillMaxWidth().padding(horizontal = AniDramaEdge - 4.dp)) {
        WeekdayHeader(listOf("M", "T", "W", "T", "F", "S", "S"))
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val index = row * 7 + column - leading
                    if (index in 0 until days) {
                        val iso = AniDramaTime.addDays(monthStart, index)
                        DayCell(
                            label = (index + 1).toString(),
                            iso = iso,
                            selected = iso == selectedIso,
                            today = iso == todayIso,
                            entries = byDay[iso].orEmpty(),
                            onSelect = onSelect,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f).height(44.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(
    weekStart: String,
    selectedIso: String,
    todayIso: String,
    byDay: Map<String, List<AniDramaScheduleEntry>>,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AniDramaEdge - 4.dp)) {
        WeekdayHeader(AniDramaTime.weekdayShort)
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { offset ->
                val iso = AniDramaTime.addDays(weekStart, offset)
                DayCell(
                    label = iso.substring(8, 10).trimStart('0'),
                    iso = iso,
                    selected = iso == selectedIso,
                    today = iso == todayIso,
                    entries = byDay[iso].orEmpty(),
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeader(labels: List<String>) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NuvioTheme.colors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DayCell(
    label: String,
    iso: String,
    selected: Boolean,
    today: Boolean,
    entries: List<AniDramaScheduleEntry>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = NuvioTheme.colors.Secondary
    val shape = RoundedCornerShape(8.dp)
    val episodes = entries.count { !it.isPremiere }
    val premieres = entries.count { it.isPremiere }
    Surface(
        onClick = { onSelect(iso) },
        modifier = modifier.height(44.dp).padding(2.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) accent else Color.Transparent,
            focusedContainerColor = if (selected) accent else Color.White,
            contentColor = if (selected) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.TextPrimary,
            focusedContentColor = if (selected) NuvioTheme.colors.OnSecondary else Color(0xFF0B0B0F),
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (today && !selected) Border(border = androidx.compose.foundation.BorderStroke(1.dp, accent), shape = shape) else Border.None,
            focusedBorder = Border(border = NuvioTheme.focusRing.border(2.dp), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp).padding(top = 2.dp)) {
                repeat(episodes.coerceAtMost(3)) { Dot(if (selected) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.TextSecondary) }
                if (premieres > 0) Dot(PremiereColor)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(4.dp).clip(CircleShape).background(color))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CalendarRow(
    entry: AniDramaScheduleEntry,
    showDubBadge: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = AniDramaEdge - 8.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF1C1D26),
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContentColor = NuvioTheme.colors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = NuvioTheme.focusRing.border(2.dp), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2B34)),
            ) {
                entry.item.poster?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.episodeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.isPremiere) PremiereColor else NuvioTheme.colors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showDubBadge && entry.item.hasEnglishDub) {
                Text(
                    text = "DUB",
                    style = MaterialTheme.typography.labelSmall,
                    color = NuvioTheme.colors.OnSecondary,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NuvioTheme.colors.Secondary)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}
