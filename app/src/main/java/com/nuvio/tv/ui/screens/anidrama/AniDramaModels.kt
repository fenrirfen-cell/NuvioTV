package com.nuvio.tv.ui.screens.anidrama

/** The four sides of the Ani-drama switch. */
enum class AniDramaRegion(val label: String) {
    Anime("Anime"),
    KDrama("K-Drama"),
    CDrama("C-Drama"),
    JDrama("J-Drama"),
    ;

    val isDrama: Boolean get() = this != Anime
}

/** Chips on the anime side (Otaku-style menus). */
enum class AnimeChip(val label: String) {
    Airing("Airing"),
    Trending("Trending"),
    Popular("Popular"),
    Top100("Top 100"),
    Seasons("Seasons"),
    Genres("Genres"),
    Upcoming("Upcoming"),
    Calendar("Calendar"),
}

/** Chips on the drama sides. */
enum class DramaChip(val label: String) {
    AiringNow("Airing now"),
    New("New"),
    Upcoming("Upcoming"),
    Popular("Popular"),
    TopRated("Top rated"),
    Genres("Genres"),
    Networks("Networks"),
    Calendar("Calendar"),
}

/** Dub / Sub / Both filter on the anime side, using the community MAL-Dubs list. */
enum class AnimeAudioFilter(val label: String) {
    Dub("Dub"),
    Sub("Sub"),
    Both("Both"),
}

enum class AnimeSeason(val label: String, val aniListName: String) {
    Winter("Winter", "WINTER"),
    Spring("Spring", "SPRING"),
    Summer("Summer", "SUMMER"),
    Fall("Fall", "FALL"),
}

/** Where an item came from, so it can be opened in Nuvio's own detail page. */
sealed interface AniDramaSource {
    data class AniList(
        val aniListId: Int,
        val malId: Int?,
        val isMovie: Boolean,
        val kitsuId: Int? = null,
        val imdbId: String? = null,
        val tmdbId: Int? = null,
    ) : AniDramaSource

    data class Tmdb(val tmdbId: Int) : AniDramaSource
}

/** One poster in the Ani-drama tab. */
data class AniDramaItem(
    val key: String,
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val source: AniDramaSource,
    /** Small badge at the top left of the poster (score, network...). */
    val badge: String? = null,
    /** Line under the title (episode, format, air day...). */
    val caption: String? = null,
    val year: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val hasEnglishDub: Boolean = false,
    /** IDs this show may have in your Library (tt..., tmdb:..., kitsu:...), for "My shows". */
    val matchIds: Set<String> = emptySet(),
    /** Date used for month / week sections (yyyy-MM-dd, or yyyy-MM / yyyy when that's all that's known). */
    val groupDate: String? = null,
    /** Place in a ranked list (Popular, Top rated), shown as #1, #2... */
    val rank: Int? = null,
)

/** How a poster grid is split into sections. */
enum class AniDramaGrouping {
    None,
    /** Month headers, newest month first (New, Newest in Genres / Networks). */
    MonthNewest,
    /** Month headers, soonest first; this month reads "Later this month" (Upcoming). */
    MonthSoonest,
    /** This week / Last week / Earlier, by latest episode (Airing now). */
    Week,
}

/** One episode in the calendar. */
data class AniDramaScheduleEntry(
    val item: AniDramaItem,
    /** 0 = Monday ... 6 = Sunday, in the phone's time zone. */
    val weekday: Int,
    /** Local date, yyyy-MM-dd. */
    val dateIso: String = "",
    val episode: Int = 0,
    /** First episode of a show (or season). */
    val isPremiere: Boolean = false,
    /** When it airs (epoch ms); for dramas TMDB only gives a date, so this is local noon. */
    val airEpochMs: Long? = null,
    val network: String? = null,
    /** Local time, e.g. "9:30 PM"; null when only the date is known (dramas). */
    val timeLabel: String?,
    val episodeLabel: String,
    val sortKey: Long,
    val genres: List<String> = emptyList(),
)

data class AniDramaPage(
    val items: List<AniDramaItem>,
    val hasMore: Boolean,
)

data class AniDramaNamedId(val id: Int, val name: String)
