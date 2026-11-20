package com.nuvio.tv.ui.screens.anidrama

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Anime side of the Ani-drama tab: AniList lists, English-dub info and ID matching. */
internal object AnimeRepository {
    private val log = AniDramaLog("AniDramaAnime")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Community list of anime with an English dub (MyAnimeList IDs). Loaded once, used as-is. */
    private const val DUB_LIST_URL = "https://raw.githubusercontent.com/MAL-Dubs/MAL-Dubs/main/data/dubInfo.json"

    /** Matches AniList IDs to Kitsu / IMDb / TMDB IDs (https://arm.haglund.dev). */
    private const val ID_MAP_URL = "https://arm.haglund.dev/api/v2/ids?include=anilist,kitsu,imdb,themoviedb,myanimelist"

    private val dubMutex = Mutex()
    private var dubbedMalIds: Set<Int>? = null

    private val idMutex = Mutex()
    private val idCache = HashMap<Int, MappedIds>()

    private data class MappedIds(val kitsu: Int?, val imdb: String?, val tmdb: Int?)

    private val pageCache = HashMap<String, Pair<List<AniDramaItem>, Boolean>>()

    suspend fun dubbedIds(): Set<Int> = dubMutex.withLock {
        dubbedMalIds?.let { return@withLock it }
        val ids = runCatching {
            val root = json.parseToJsonElement(httpGetText(DUB_LIST_URL)).jsonObject
            root["dubbed"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.toSet().orEmpty()
        }.onFailure { log.w { "Dub list failed: ${it.message}" } }.getOrNull()
        if (ids != null && ids.isNotEmpty()) dubbedMalIds = ids
        ids.orEmpty()
    }

    /**
     * One page of a list, already matched to add-on IDs and marked dub / no dub.
     * Results are kept for this app session so flipping between chips is instant.
     */
    suspend fun page(query: AniListClient.MediaQuery, page: Int): AniDramaPage? {
        val cacheKey = "$query#$page"
        pageCache[cacheKey]?.let { (items, more) -> return AniDramaPage(items, more) }
        val (media, hasMore) = AniListClient.page(query, page, PER_PAGE) ?: return null
        val dubbed = dubbedIds()
        mapIds(media.map { it.id })
        val byDate = query.sort.startsWith("START_DATE")
        val items = media.mapIndexed { index, it ->
            val date = it.startDate?.isoOrNull()
            val caption = when {
                byDate -> date?.let(AniDramaSections::dateCaption) ?: captionFor(it, query)
                query.ranked -> listOfNotNull((it.seasonYear ?: it.startDate?.year)?.toString(), it.format?.toFormatLabel())
                    .joinToString(" · ")
                else -> captionFor(it, query)
            }
            it.toItem(dubbed, caption).copy(
                groupDate = if (byDate) date else null,
                rank = if (query.ranked) (page - 1) * PER_PAGE + index + 1 else null,
            )
        }
        pageCache[cacheKey] = items to hasMore
        return AniDramaPage(items, hasMore)
    }

    private const val PER_PAGE = 30

    /** "2027-01-05", "2027-01" or "2027", depending on how much of the date AniList knows. */
    private fun AniListDate.isoOrNull(): String? {
        val y = year ?: return null
        val m = month ?: return "$y"
        val mm = m.toString().padStart(2, '0')
        val d = day ?: return "$y-$mm"
        return "$y-$mm-${d.toString().padStart(2, '0')}"
    }

    /** Today as AniList's yyyyMMdd number, moved by [days]. */
    fun fuzzyToday(days: Int = 0): Int = AniDramaTime.addDays(AniDramaTime.todayIso(), days).replace("-", "").toInt()

    private var latestCache: Pair<String, AniDramaPage>? = null

    /**
     * Shows with an episode out in the last three weeks, newest episode first, each with that
     * episode's number and local date (for This week / Last week / Earlier).
     */
    suspend fun airingLatest(): AniDramaPage? {
        val today = AniDramaTime.todayIso()
        latestCache?.takeIf { it.first == today }?.let { return it.second }
        val now = AniDramaTime.nowEpochMs() / 1000
        val airings = AniListClient.schedule(now - 21L * 24 * 3600, now + 1, maxPages = 20, newestFirst = true)
        if (airings.isEmpty()) return null
        val dubbed = dubbedIds()
        val latest = airings.filter { it.media != null }.groupBy { it.media!!.id }.values.map { list -> list.maxBy { it.airingAt } }
        mapIds(latest.map { it.media!!.id })
        val items = latest.sortedByDescending { it.airingAt }.map { airing ->
            val date = AniDramaTime.localDayIsoOf(airing.airingAt * 1000)
            airing.media!!.toItem(dubbed, caption = "EP ${airing.episode} · ${AniDramaTime.shortDayLabel(date)}")
                .copy(groupDate = date)
        }.sortedByDescending { it.groupDate }
        val result = AniDramaPage(items, hasMore = false)
        latestCache = today to result
        return result
    }

    /** This week's episodes, Monday to Sunday in the phone's time zone. */
    suspend fun weekSchedule(): List<AniDramaScheduleEntry> = schedule(AniDramaTime.mondayOfThisWeek(), 7)

    private val scheduleCache = HashMap<String, List<AniDramaScheduleEntry>>()

    /** Episodes airing over [days] days from [startIso] (local dates). Kept for this app session. */
    suspend fun schedule(startIso: String, days: Int): List<AniDramaScheduleEntry> {
        val cacheKey = "$startIso+$days"
        scheduleCache[cacheKey]?.let { return it }
        val startDay = AniDramaTime.epochDay(startIso) ?: return emptyList()
        val from = AniDramaTime.localStartOfDayMs(startIso) / 1000 - 1
        val to = AniDramaTime.localStartOfDayMs(AniDramaTime.addDays(startIso, days)) / 1000
        val airings = AniListClient.schedule(from, to, maxPages = if (days > 7) 24 else 6)
        val dubbed = dubbedIds()
        mapIds(airings.mapNotNull { it.media?.id }.distinct())
        // Local midnight of each day in the range (and the day after), to place episodes quickly.
        val dayStarts = (0..days).map { AniDramaTime.localStartOfDayMs(AniDramaTime.addDays(startIso, it)) }
        val entries = airings.mapNotNull { airing ->
            val media = airing.media ?: return@mapNotNull null
            val ms = airing.airingAt * 1000
            val offset = (0 until days).firstOrNull { ms >= dayStarts[it] && ms < dayStarts[it + 1] } ?: return@mapNotNull null
            val dateIso = AniDramaTime.addDays(startIso, offset)
            val day = startDay + offset
            val minutes = ((ms - dayStarts[offset]) / 60_000L).toInt()
            val time = AniDramaTime.timeLabel(minutes / 60, minutes % 60)
            val item = media.toItem(dubbed, caption = "EP ${airing.episode}")
            AniDramaScheduleEntry(
                item = item,
                weekday = AniDramaTime.weekdayOfEpochDay(day),
                dateIso = dateIso,
                episode = airing.episode,
                isPremiere = airing.episode == 1,
                airEpochMs = ms,
                timeLabel = time,
                episodeLabel = buildString {
                    append("EP ${airing.episode} airs $time")
                    append(if (item.hasEnglishDub) " · Dub" else " · Sub")
                },
                sortKey = airing.airingAt,
                genres = media.genres,
            )
        }
        if (entries.isNotEmpty()) scheduleCache[cacheKey] = entries
        return entries
    }

    /** The next episode (number, epoch ms) of a show, or null when nothing is scheduled. */
    suspend fun nextEpisode(aniListId: Int): Pair<Int, Long>? =
        AniListClient.nextEpisode(aniListId)?.let { it.episode to it.airingAt * 1000 }

    fun applyAudioFilter(items: List<AniDramaItem>, filter: AnimeAudioFilter): List<AniDramaItem> = when (filter) {
        AnimeAudioFilter.Both -> items
        AnimeAudioFilter.Dub -> items.filter { it.hasEnglishDub }
        AnimeAudioFilter.Sub -> items.filterNot { it.hasEnglishDub }
    }

    private fun captionFor(media: AniListMedia, query: AniListClient.MediaQuery): String {
        val format = media.format?.toFormatLabel()
        val next = media.nextAiringEpisode
        val episodePart = when {
            next != null && query.status == "RELEASING" -> "EP ${next.episode - 1}".takeIf { next.episode > 1 } ?: "Starts soon"
            next != null -> "EP ${next.episode} next"
            media.episodes != null && media.format != "MOVIE" -> "${media.episodes} EP"
            else -> null
        }
        val yearPart = media.seasonYear?.toString() ?: media.startDate?.year?.toString()
        return listOfNotNull(episodePart, format, if (episodePart == null) yearPart else null).joinToString(" · ")
    }

    private fun String.toFormatLabel(): String = when (this) {
        "TV" -> "TV"
        "TV_SHORT" -> "Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MUSIC" -> "Music"
        else -> this.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun AniListMedia.toItem(dubbed: Set<Int>, caption: String?): AniDramaItem {
        val ids = idCache[id]
        return AniDramaItem(
            key = "anilist:$id",
            title = displayTitle,
            poster = coverImage?.extraLarge ?: coverImage?.large,
            backdrop = bannerImage,
            source = AniDramaSource.AniList(
                aniListId = id,
                malId = idMal,
                isMovie = format == "MOVIE",
                kitsuId = ids?.kitsu,
                imdbId = ids?.imdb,
                tmdbId = ids?.tmdb,
            ),
            badge = averageScore?.let { score -> "${score / 10}.${score % 10}" },
            caption = caption,
            year = (seasonYear ?: startDate?.year)?.toString(),
            description = description?.stripHtml(),
            genres = genres,
            hasEnglishDub = idMal != null && idMal in dubbed,
            matchIds = buildSet {
                add("anilist:$id")
                idMal?.let { add("mal:$it") }
                ids?.kitsu?.let { add("kitsu:$it") }
                ids?.imdb?.let { add(it) }
                ids?.tmdb?.let { add("tmdb:$it") }
            },
        )
    }

    /** Looks up Kitsu / IMDb / TMDB IDs for AniList IDs not seen yet (100 per request). */
    private suspend fun mapIds(aniListIds: List<Int>) = idMutex.withLock {
        val missing = aniListIds.filter { it !in idCache }.distinct()
        missing.chunked(100).forEach { chunk ->
            val body = buildJsonArray {
                chunk.forEach { id -> add(buildJsonObject { put("anilist", id) }) }
            }.toString()
            val results = runCatching { json.parseToJsonElement(httpPostJson(ID_MAP_URL, body)).jsonArray }
                .onFailure { log.w { "ID matching failed: ${it.message}" } }
                .getOrNull() ?: return@forEach
            results.forEachIndexed { index, element ->
                val aniListId = chunk.getOrNull(index) ?: return@forEachIndexed
                val obj = element as? JsonObject
                idCache[aniListId] = if (obj == null || element is JsonNull) {
                    MappedIds(null, null, null)
                } else {
                    MappedIds(
                        kitsu = obj["kitsu"]?.jsonPrimitive?.intOrNull,
                        imdb = obj["imdb"]?.jsonPrimitive?.content?.takeIf { it.startsWith("tt") },
                        tmdb = obj["themoviedb"]?.jsonPrimitive?.intOrNull,
                    )
                }
            }
        }
    }

    private fun String.stripHtml(): String =
        replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
            .trim()
}
