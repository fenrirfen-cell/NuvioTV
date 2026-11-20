package com.nuvio.tv.ui.screens.anidrama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Reads anime lists from AniList's free GraphQL API (no account or key needed). */
internal object AniListClient {
    private const val ENDPOINT = "https://graphql.anilist.co"
    private val log = AniDramaLog("AniList")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    /** AniList's genres, minus adult ones. */
    val genres = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo", "Mecha",
        "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural",
        "Thriller",
    )

    private const val MEDIA_FIELDS = """
        id idMal format status episodes averageScore seasonYear genres
        title { romaji english userPreferred }
        coverImage { extraLarge large }
        bannerImage
        description(asHtml: false)
        nextAiringEpisode { episode airingAt }
        startDate { year month day }
    """

    private const val PAGE_QUERY = """
        query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort], ${'$'}status: MediaStatus,
               ${'$'}season: MediaSeason, ${'$'}seasonYear: Int, ${'$'}genre: String, ${'$'}country: CountryCode,
               ${'$'}startGreater: FuzzyDateInt, ${'$'}startLesser: FuzzyDateInt) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            pageInfo { hasNextPage }
            media(type: ANIME, isAdult: false, sort: ${'$'}sort, status: ${'$'}status, season: ${'$'}season,
                  seasonYear: ${'$'}seasonYear, genre: ${'$'}genre, countryOfOrigin: ${'$'}country,
                  startDate_greater: ${'$'}startGreater, startDate_lesser: ${'$'}startLesser) {
              $MEDIA_FIELDS
            }
          }
        }
    """

    private const val SCHEDULE_QUERY = """
        query (${'$'}page: Int, ${'$'}from: Int, ${'$'}to: Int, ${'$'}sort: [AiringSort]) {
          Page(page: ${'$'}page, perPage: 50) {
            pageInfo { hasNextPage }
            airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: ${'$'}sort) {
              episode airingAt
              media { isAdult countryOfOrigin $MEDIA_FIELDS }
            }
          }
        }
    """

    data class MediaQuery(
        val sort: String,
        val status: String? = null,
        val season: AnimeSeason? = null,
        val seasonYear: Int? = null,
        val genre: String? = null,
        /** Only shows starting after / before this date (yyyyMMdd). */
        val startGreater: Int? = null,
        val startLesser: Int? = null,
        /** Show #1, #2... on the posters. */
        val ranked: Boolean = false,
    )

    suspend fun page(query: MediaQuery, page: Int, perPage: Int = 30): Pair<List<AniListMedia>, Boolean>? {
        val variables = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            put("sort", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(query.sort))))
            query.status?.let { put("status", it) }
            query.season?.let { put("season", it.aniListName) }
            query.seasonYear?.let { put("seasonYear", it) }
            query.genre?.let { put("genre", it) }
            query.startGreater?.let { put("startGreater", it) }
            query.startLesser?.let { put("startLesser", it) }
            put("country", "JP")
        }
        val response = post<PageEnvelope>(PAGE_QUERY, variables) ?: return null
        val pageData = response.data?.page ?: return null
        return pageData.media to (pageData.pageInfo?.hasNextPage == true)
    }

    private const val NEXT_QUERY = """
        query (${'$'}id: Int) { Media(id: ${'$'}id, type: ANIME) { nextAiringEpisode { episode airingAt } } }
    """

    /** The next episode of one show, or null if none is scheduled. */
    suspend fun nextEpisode(id: Int): AniListNextEpisode? {
        val variables = buildJsonObject { put("id", id) }
        return post<NextEnvelope>(NEXT_QUERY, variables)?.data?.media?.nextAiringEpisode
    }

    /** Episodes airing between two moments (epoch seconds), Japanese TV only, oldest first. */
    suspend fun schedule(
        fromEpochSeconds: Long,
        toEpochSeconds: Long,
        maxPages: Int = 6,
        newestFirst: Boolean = false,
    ): List<AniListAiring> {
        val all = mutableListOf<AniListAiring>()
        for (page in 1..maxPages) {
            val variables = buildJsonObject {
                put("page", page)
                put("from", fromEpochSeconds.toInt())
                put("to", toEpochSeconds.toInt())
                put("sort", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(if (newestFirst) "TIME_DESC" else "TIME"))))
            }
            val response = post<ScheduleEnvelope>(SCHEDULE_QUERY, variables) ?: break
            val data = response.data?.page ?: break
            all += data.airingSchedules.filter { airing ->
                airing.media?.isAdult != true && (airing.media?.countryOfOrigin ?: "JP") == "JP"
            }
            if (data.pageInfo?.hasNextPage != true) break
        }
        return all
    }

    private suspend inline fun <reified T> post(query: String, variables: JsonObject): T? {
        val body = buildJsonObject {
            put("query", query.trimIndent())
            put("variables", variables)
        }.toString()
        return runCatching { json.decodeFromString<T>(httpPostJson(ENDPOINT, body)) }
            .onFailure { log.w { "AniList request failed: ${it.message}" } }
            .getOrNull()
    }

    @Serializable
    private data class PageEnvelope(val data: PageData? = null)

    @Serializable
    private data class PageData(@SerialName("Page") val page: MediaPage? = null)

    @Serializable
    private data class MediaPage(
        val pageInfo: PageInfo? = null,
        val media: List<AniListMedia> = emptyList(),
    )

    @Serializable
    private data class ScheduleEnvelope(val data: SchedulePageData? = null)

    @Serializable
    private data class SchedulePageData(@SerialName("Page") val page: SchedulePage? = null)

    @Serializable
    private data class SchedulePage(
        val pageInfo: PageInfo? = null,
        val airingSchedules: List<AniListAiring> = emptyList(),
    )

    @Serializable
    private data class PageInfo(val hasNextPage: Boolean = false)

    @Serializable
    private data class NextEnvelope(val data: NextData? = null)

    @Serializable
    private data class NextData(@SerialName("Media") val media: NextMedia? = null)

    @Serializable
    private data class NextMedia(val nextAiringEpisode: AniListNextEpisode? = null)
}

@Serializable
internal data class AniListMedia(
    val id: Int,
    val idMal: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val averageScore: Int? = null,
    val seasonYear: Int? = null,
    val genres: List<String> = emptyList(),
    val title: AniListTitle? = null,
    val coverImage: AniListCover? = null,
    val bannerImage: String? = null,
    val description: String? = null,
    val nextAiringEpisode: AniListNextEpisode? = null,
    val startDate: AniListDate? = null,
    val isAdult: Boolean? = null,
    val countryOfOrigin: String? = null,
) {
    val displayTitle: String
        get() = title?.english?.takeIf { it.isNotBlank() }
            ?: title?.userPreferred?.takeIf { it.isNotBlank() }
            ?: title?.romaji.orEmpty()
}

@Serializable
internal data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    val userPreferred: String? = null,
)

@Serializable
internal data class AniListCover(val extraLarge: String? = null, val large: String? = null)

@Serializable
internal data class AniListNextEpisode(val episode: Int, val airingAt: Long)

@Serializable
internal data class AniListDate(val year: Int? = null, val month: Int? = null, val day: Int? = null)

@Serializable
internal data class AniListAiring(
    val episode: Int,
    val airingAt: Long,
    val media: AniListMedia? = null,
)
