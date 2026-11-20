package com.nuvio.tv.ui.screens.anidrama

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** K-, C- and J-drama side of the Ani-drama tab, from TMDB (filtered by country and language). */
internal object DramaRepository {
    private val log = AniDramaLog("AniDramaDrama")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private const val POSTER = "https://image.tmdb.org/t/p/w500"
    private const val BACKDROP = "https://image.tmdb.org/t/p/w780"

    /** Animation, news, reality and talk shows are left out so the lists stay dramas. */
    private const val EXCLUDED_GENRES = "16,10763,10764,10767"

    private val pageCache = HashMap<String, AniDramaPage>()
    private val genreNames = HashMap<String, Map<Int, String>>()
    private val keywordIds = HashMap<String, Int?>()
    private val detailCache = HashMap<Int, TvDetails>()
    private val airingMutex = Mutex()
    private val airingCache = HashMap<AniDramaRegion, List<TvDetails>>()

    data class Filter(
        val chip: DramaChip,
        val genreId: Int? = null,
        val keywordId: Int? = null,
        val networkId: Int? = null,
        /** Genres / Networks: newest premiere first instead of most popular. */
        val newest: Boolean = false,
    )

    private val AniDramaRegion.country: String
        get() = when (this) {
            AniDramaRegion.KDrama -> "KR"
            AniDramaRegion.CDrama -> "CN"
            AniDramaRegion.JDrama -> "JP"
            AniDramaRegion.Anime -> "JP"
        }

    private val AniDramaRegion.languageCode: String
        get() = when (this) {
            AniDramaRegion.KDrama -> "ko"
            AniDramaRegion.CDrama -> "zh"
            AniDramaRegion.JDrama -> "ja"
            AniDramaRegion.Anime -> "ja"
        }

    /** Extra "genres" that TMDB only has as keywords (it has no Romance genre for TV). */
    private fun keywordGenres(region: AniDramaRegion): List<Pair<String, List<String>>> = buildList {
        add("Romance" to listOf("romance"))
        add("Historical" to listOf("historical drama", "period drama", "costume drama"))
        add("School" to listOf("high school", "school life"))
        if (region == AniDramaRegion.CDrama) {
            add("Wuxia" to listOf("wuxia"))
            add("Xianxia" to listOf("xianxia"))
        }
        if (region == AniDramaRegion.KDrama) add("Revenge" to listOf("revenge"))
    }

    private fun language(): String = AniDramaTmdb.language()

    private fun apiKey(): String = AniDramaTmdb.apiKey()

    suspend fun page(region: AniDramaRegion, filter: Filter, page: Int): AniDramaPage? {
        val cacheKey = "$region|$filter|$page|${language()}"
        pageCache[cacheKey]?.let { return it }
        val today = AniDramaTime.todayIso()
        val query = linkedMapOf(
            "language" to language(),
            "page" to page.toString(),
            "include_adult" to "false",
            "with_origin_country" to region.country,
            "with_original_language" to region.languageCode,
            "without_genres" to EXCLUDED_GENRES,
            "sort_by" to "popularity.desc",
        )
        when (filter.chip) {
            DramaChip.AiringNow, DramaChip.Calendar -> {
                query["air_date.gte"] = AniDramaTime.addDays(today, -7)
                query["air_date.lte"] = AniDramaTime.addDays(today, 7)
            }
            DramaChip.New -> {
                // Since the 1st of last month, newest premiere first.
                query["first_air_date.gte"] = startOfMonthOffset(today, -1)
                query["first_air_date.lte"] = today
                query["sort_by"] = "first_air_date.desc"
            }
            DramaChip.Upcoming -> {
                // From tomorrow to the end of the third month ahead, soonest first.
                query["first_air_date.gte"] = AniDramaTime.addDays(today, 1)
                query["first_air_date.lte"] = AniDramaTime.addDays(startOfMonthOffset(today, 4), -1)
                query["sort_by"] = "first_air_date.asc"
            }
            DramaChip.Popular -> Unit
            DramaChip.TopRated -> {
                query["sort_by"] = "vote_average.desc"
                query["vote_count.gte"] = if (region == AniDramaRegion.KDrama) "150" else "60"
            }
            DramaChip.Genres -> {
                filter.genreId?.let { query["with_genres"] = it.toString() }
                filter.keywordId?.let { query["with_keywords"] = it.toString() }
            }
            DramaChip.Networks -> filter.networkId?.let { query["with_networks"] = it.toString() }
        }
        val newestBrowse = filter.newest && (filter.chip == DramaChip.Genres || filter.chip == DramaChip.Networks)
        if (newestBrowse) {
            query["sort_by"] = "first_air_date.desc"
            query["first_air_date.lte"] = today
        }
        val response = fetch<DiscoverResponse>("discover/tv", query) ?: return null
        val names = genreNames(region)
        val dateSorted = filter.chip == DramaChip.New || filter.chip == DramaChip.Upcoming || newestBrowse
        val ranked = filter.chip == DramaChip.Popular || filter.chip == DramaChip.TopRated
        // Sorting by date alone pulls in tiny listings; skip ones without a poster.
        val results = if (dateSorted) response.results.filter { it.posterPath != null } else response.results
        val items = response.results.mapIndexedNotNull { index, show ->
            if (show !in results) return@mapIndexedNotNull null
            val date = show.firstAirDate?.takeIf { it.length >= 10 }
            show.toItem(
                caption = if (dateSorted) date?.let(AniDramaSections::dateCaption) else show.firstAirDate?.take(4),
                genreNames = show.genreIds.mapNotNull { names[it] },
            ).copy(
                groupDate = if (dateSorted) date else null,
                rank = if (ranked) (page - 1) * 20 + index + 1 else null,
            )
        }
        val result = AniDramaPage(items, hasMore = response.page < response.totalPages)
        pageCache[cacheKey] = result
        return result
    }

    /** The 1st of the month [months] months from [iso]'s month (negative = back), yyyy-MM-dd. */
    private fun startOfMonthOffset(iso: String, months: Int): String {
        val year = iso.take(4).toInt()
        val month = iso.substring(5, 7).toInt() - 1 + months
        val y = year + month.floorDiv(12)
        val m = month.mod(12) + 1
        return AniDramaTime.firstOfMonth(y, m)
    }

    private val latestMutex = Mutex()
    private val latestCache = HashMap<String, AniDramaPage>()

    /**
     * Shows that aired an episode in the last three weeks, newest episode first, each with its
     * latest episode number and date (for This week / Last week / Earlier).
     */
    suspend fun airingLatest(region: AniDramaRegion): AniDramaPage? = latestMutex.withLock {
        val today = AniDramaTime.todayIso()
        val cacheKey = "$region|$today|${language()}"
        latestCache[cacheKey]?.let { return@withLock it }
        val query = linkedMapOf(
            "language" to language(),
            "include_adult" to "false",
            "with_origin_country" to region.country,
            "with_original_language" to region.languageCode,
            "without_genres" to EXCLUDED_GENRES,
            "sort_by" to "popularity.desc",
            "air_date.gte" to AniDramaTime.addDays(today, -21),
            "air_date.lte" to today,
        )
        val pages = (1..3).map { number -> fetch<DiscoverResponse>("discover/tv", query + ("page" to "$number")) }
        if (pages.all { it == null }) return@withLock null
        val ids = pages.flatMap { it?.results.orEmpty() }.map { it.id }.distinct()
        val permits = Semaphore(6)
        val shows = coroutineScope {
            ids.map { id -> async { permits.withPermit { details(id) } } }.awaitAll()
        }.filterNotNull()
        val items = shows.mapNotNull { show ->
            val last = show.lastEpisode ?: return@mapNotNull null
            val date = last.airDate?.takeIf { it.length >= 10 && it <= today } ?: return@mapNotNull null
            show.toItem(
                badge = show.networks.firstOrNull()?.name,
                caption = "EP ${last.episodeNumber} · ${AniDramaTime.shortDayLabel(date)}",
            ).copy(groupDate = date)
        }.sortedByDescending { it.groupDate }
        val result = AniDramaPage(items, hasMore = false)
        if (items.isNotEmpty()) latestCache[cacheKey] = result
        result
    }

    /** TMDB TV genres (minus excluded ones) plus keyword-based ones like Romance. Keyword ids are negative. */
    suspend fun genres(region: AniDramaRegion): List<AniDramaNamedId> {
        val base = genreNames(region)
            .filterKeys { it.toString() !in EXCLUDED_GENRES.split(',') && it != 10762 && it != 10766 }
            .map { (id, name) -> AniDramaNamedId(id, name) }
        val extras = keywordGenres(region).mapNotNull { (label, candidates) ->
            keywordId(candidates)?.let { AniDramaNamedId(-it, label) }
        }
        return (extras + base).distinctBy { it.name.lowercase() }
    }

    /** Networks of the shows airing now, most common first. */
    suspend fun networks(region: AniDramaRegion): List<AniDramaNamedId> =
        airingDetails(region)
            .flatMap { it.networks }
            .groupBy { it.id }
            .entries
            .sortedByDescending { it.value.size }
            .take(14)
            .map { (id, list) -> AniDramaNamedId(id, list.first().name) }

    /** Episodes airing this week (Monday to Sunday). */
    suspend fun weekSchedule(region: AniDramaRegion): List<AniDramaScheduleEntry> =
        schedule(region, AniDramaTime.mondayOfThisWeek(), 7)

    private val scheduleMutex = Mutex()
    private val scheduleCache = HashMap<String, List<AniDramaScheduleEntry>>()
    private val seasonCache = HashMap<String, List<Episode>>()

    /**
     * Episodes over [days] days from [startIso]: every show with an episode in that range (most
     * popular first) plus shows premiering in it, with dates from each show's season list.
     */
    suspend fun schedule(region: AniDramaRegion, startIso: String, days: Int): List<AniDramaScheduleEntry> =
        scheduleMutex.withLock {
            val cacheKey = "$region|$startIso|$days|${language()}"
            scheduleCache[cacheKey]?.let { return@withLock it }
            val startDay = AniDramaTime.epochDay(startIso) ?: return@withLock emptyList()
            val endIso = AniDramaTime.addDays(startIso, days - 1)
            val base = linkedMapOf(
                "language" to language(),
                "include_adult" to "false",
                "with_origin_country" to region.country,
                "with_original_language" to region.languageCode,
                "without_genres" to EXCLUDED_GENRES,
                "sort_by" to "popularity.desc",
            )
            val airingIds = (1..3).flatMap { page ->
                fetch<DiscoverResponse>(
                    "discover/tv",
                    base + mapOf("page" to "$page", "air_date.gte" to startIso, "air_date.lte" to endIso),
                )?.results.orEmpty()
            }.map { it.id }
            val premiereIds = (1..2).flatMap { page ->
                fetch<DiscoverResponse>(
                    "discover/tv",
                    base + mapOf("page" to "$page", "first_air_date.gte" to startIso, "first_air_date.lte" to endIso),
                )?.results.orEmpty()
            }.map { it.id }
            val ids = (airingIds + premiereIds).distinct().take(70)
            val permits = Semaphore(6)
            val shows = coroutineScope {
                ids.map { id -> async { permits.withPermit { details(id) } } }.awaitAll()
            }.filterNotNull()
            val entries = coroutineScope {
                shows.map { show ->
                    async {
                        permits.withPermit {
                            val seasons = listOfNotNull(show.lastEpisode?.seasonNumber, show.nextEpisode?.seasonNumber)
                                .ifEmpty { listOf(1) }
                                .distinct()
                            seasons.flatMap { season -> seasonEpisodes(show.id, season) }
                                .mapNotNull { episode -> show.entryFor(episode, startDay, days) }
                        }
                    }
                }.awaitAll().flatten()
            }
            if (entries.isNotEmpty()) scheduleCache[cacheKey] = entries
            entries
        }

    /** Shows premiering in the next 90 days (most talked-about first), soonest date first. */
    suspend fun comingSoon(region: AniDramaRegion): List<AniDramaItem> {
        val today = AniDramaTime.todayIso()
        val query = linkedMapOf(
            "language" to language(),
            "include_adult" to "false",
            "with_origin_country" to region.country,
            "with_original_language" to region.languageCode,
            "without_genres" to EXCLUDED_GENRES,
            "sort_by" to "popularity.desc",
            "first_air_date.gte" to AniDramaTime.addDays(today, 1),
            "first_air_date.lte" to AniDramaTime.addDays(today, 90),
        )
        val shows = (1..2).flatMap { page ->
            fetch<DiscoverResponse>("discover/tv", query + ("page" to "$page"))?.results.orEmpty()
        }.distinctBy { it.id }
        val names = genreNames(region)
        return shows
            .filter { !it.firstAirDate.isNullOrBlank() }
            .sortedBy { it.firstAirDate }
            .map { show ->
                show.toItem(
                    caption = "Starts ${AniDramaTime.shortDayLabel(show.firstAirDate!!)}",
                    genreNames = show.genreIds.mapNotNull { names[it] },
                )
            }
    }

    /** The next episode (number, local noon of its air date in epoch ms) of a show, or null. */
    suspend fun nextEpisode(tmdbId: Int): Pair<Int, Long>? {
        detailCache.remove(tmdbId)
        val next = details(tmdbId)?.nextEpisode ?: return null
        val date = next.airDate ?: return null
        return next.episodeNumber to (AniDramaTime.localStartOfDayMs(date) + NOON_MS)
    }

    private const val NOON_MS = 12 * 60 * 60 * 1000L

    private fun TvDetails.entryFor(episode: Episode, startDay: Long, days: Int): AniDramaScheduleEntry? {
        val date = episode.airDate ?: return null
        val day = AniDramaTime.epochDay(date) ?: return null
        val offset = (day - startDay).toInt()
        if (offset !in 0 until days) return null
        val network = networks.firstOrNull()?.name
        val premiere = episode.episodeNumber == 1
        val item = toItem(
            badge = network,
            caption = "EP ${episode.episodeNumber} · ${AniDramaTime.weekdayShort[AniDramaTime.weekdayOfEpochDay(day)]}",
        )
        return AniDramaScheduleEntry(
            item = item,
            weekday = AniDramaTime.weekdayOfEpochDay(day),
            dateIso = date,
            episode = episode.episodeNumber,
            isPremiere = premiere,
            airEpochMs = AniDramaTime.localStartOfDayMs(date) + NOON_MS,
            network = network,
            timeLabel = null,
            episodeLabel = listOfNotNull(
                if (premiere) "Premiere" else null,
                "EP ${episode.episodeNumber}",
                network,
                genres.firstOrNull()?.name,
            ).joinToString(" · "),
            sortKey = day * 1000 + (1000 - (popularity ?: 0.0).coerceAtMost(999.0).roundToInt()),
            genres = genres.map { it.name },
        )
    }

    private suspend fun seasonEpisodes(showId: Int, season: Int): List<Episode> {
        val key = "$showId/$season"
        seasonCache[key]?.let { return it }
        val episodes = fetch<SeasonDetails>("tv/$showId/season/$season", mapOf("language" to language()))
            ?.episodes.orEmpty()
        if (episodes.isNotEmpty()) seasonCache[key] = episodes
        return episodes
    }

    /** Details (network, episodes) for the ~60 most popular shows airing around this week. */
    private suspend fun airingDetails(region: AniDramaRegion): List<TvDetails> = airingMutex.withLock {
        airingCache[region]?.let { return@withLock it }
        val ids = (1..3).flatMap { pageNumber ->
            page(region, Filter(DramaChip.AiringNow), pageNumber)?.items.orEmpty()
        }.mapNotNull { (it.source as? AniDramaSource.Tmdb)?.tmdbId }.distinct()
        val permits = Semaphore(6)
        val details = coroutineScope {
            ids.map { id -> async { permits.withPermit { details(id) } } }.awaitAll()
        }.filterNotNull()
        if (details.isNotEmpty()) airingCache[region] = details
        details
    }

    private suspend fun details(id: Int): TvDetails? {
        detailCache[id]?.let { return it }
        val result = fetch<TvDetails>("tv/$id", mapOf("language" to language(), "append_to_response" to "external_ids"))
            ?: return null
        detailCache[id] = result
        return result
    }

    private suspend fun genreNames(region: AniDramaRegion): Map<Int, String> {
        val lang = language()
        genreNames[lang]?.let { return it }
        val result = fetch<GenreList>("genre/tv/list", mapOf("language" to lang))
            ?.genres?.associate { it.id to it.name }.orEmpty()
        if (result.isNotEmpty()) genreNames[lang] = result
        return result
    }

    private suspend fun keywordId(candidates: List<String>): Int? {
        for (name in candidates) {
            if (keywordIds.containsKey(name)) {
                keywordIds[name]?.let { return it } ?: continue
            }
            val found = fetch<KeywordSearch>("search/keyword", mapOf("query" to name))
                ?.results?.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
            keywordIds[name] = found
            if (found != null) return found
        }
        return null
    }

    private suspend inline fun <reified T> fetch(endpoint: String, query: Map<String, String>): T? {
        val url = buildTmdbUrl(endpoint = endpoint, apiKey = apiKey(), query = query)
        return runCatching { json.decodeFromString<T>(httpGetText(url)) }
            .onFailure { log.w { "TMDB $endpoint failed: ${it.message}" } }
            .getOrNull()
    }

    private fun TvResult.toItem(caption: String?, genreNames: List<String>): AniDramaItem = AniDramaItem(
        key = "tmdb:$id",
        title = name,
        poster = posterPath?.let { POSTER + it },
        backdrop = backdropPath?.let { BACKDROP + it },
        source = AniDramaSource.Tmdb(id),
        badge = voteAverage?.takeIf { it > 0 }?.let { ((it * 10).roundToInt() / 10.0).toString() },
        caption = caption,
        year = firstAirDate?.take(4),
        description = overview,
        genres = genreNames,
        matchIds = setOf("tmdb:$id"),
    )

    private fun TvDetails.toItem(badge: String?, caption: String?): AniDramaItem = AniDramaItem(
        key = "tmdb:$id",
        title = name,
        poster = posterPath?.let { POSTER + it },
        backdrop = backdropPath?.let { BACKDROP + it },
        source = AniDramaSource.Tmdb(id),
        badge = badge,
        caption = caption,
        year = firstAirDate?.take(4),
        description = overview,
        genres = genres.map { it.name },
        matchIds = setOfNotNull("tmdb:$id", externalIds?.imdbId?.takeIf { it.startsWith("tt") }),
    )

    private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun String.shortDate(): String {
        val month = substring(5, 7).toIntOrNull()?.let { monthNames.getOrNull(it - 1) } ?: return this
        val day = substring(8, 10).toIntOrNull() ?: return this
        return "$month $day"
    }

    @Serializable
    private data class DiscoverResponse(
        val page: Int = 1,
        @SerialName("total_pages") val totalPages: Int = 1,
        val results: List<TvResult> = emptyList(),
    )

    @Serializable
    private data class TvResult(
        val id: Int,
        val name: String = "",
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        val overview: String? = null,
        @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    )

    @Serializable
    private data class TvDetails(
        val id: Int,
        val name: String = "",
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        val overview: String? = null,
        val popularity: Double? = null,
        val networks: List<Named> = emptyList(),
        val genres: List<Named> = emptyList(),
        @SerialName("last_episode_to_air") val lastEpisode: Episode? = null,
        @SerialName("next_episode_to_air") val nextEpisode: Episode? = null,
        @SerialName("external_ids") val externalIds: ExternalIds? = null,
    )

    @Serializable
    private data class ExternalIds(@SerialName("imdb_id") val imdbId: String? = null)

    @Serializable
    private data class SeasonDetails(val episodes: List<Episode> = emptyList())

    @Serializable
    private data class Episode(
        @SerialName("air_date") val airDate: String? = null,
        @SerialName("episode_number") val episodeNumber: Int = 0,
        @SerialName("season_number") val seasonNumber: Int = 0,
    )

    @Serializable
    private data class Named(val id: Int, val name: String = "")

    @Serializable
    private data class GenreList(val genres: List<Named> = emptyList())

    @Serializable
    private data class KeywordSearch(val results: List<Named> = emptyList())
}
