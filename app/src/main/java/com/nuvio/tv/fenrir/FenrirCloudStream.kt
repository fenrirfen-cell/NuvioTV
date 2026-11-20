package com.nuvio.tv.fenrir

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A CloudStream site whose own home page (its categories) can be shown on Home. */
data class FenrirHomeSource(
    val id: String,
    val name: String,
    val subtitle: String,
    val iconUrl: String? = null,
)

/** One title from a CloudStream site. [key] is "Site|url". */
data class FenrirHomeItem(
    val key: String,
    val sourceName: String,
    val title: String,
    val poster: String?,
    /** "SUB", "DUB", "SUB · DUB" or null. */
    val badge: String? = null,
    val year: Int? = null,
    val isMovie: Boolean = false,
)

/** One category of a site's home page ("Latest", "Top K-Drama"…), in the site's own order. */
data class FenrirHomeRow(
    val title: String,
    val categoryName: String,
    val categoryData: String,
    val items: List<FenrirHomeItem>,
    val horizontalImages: Boolean = false,
)

/** One episode (or the movie) on a CloudStream site's title page. */
data class FenrirSourceEpisode(
    val key: String,
    val name: String,
    val season: Int? = null,
    val episode: Int? = null,
)

/** One playable link from a CloudStream site. */
data class FenrirSourceLink(
    val sourceName: String,
    val label: String,
    val detail: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Pair<String, String>> = emptyList(),
)

/**
 * Browsing your installed CloudStream plugins like the Fenrir phone app (and CloudStream itself):
 * a site's home categories, a title's episodes and an episode's links. The full build runs the
 * plugins; the Play Store build has none.
 */
interface FenrirCloudStreamBrowser {
    /** Sites with their own home page, from your enabled CloudStream plugins. */
    suspend fun homeSources(): List<FenrirHomeSource>

    /** All of [sourceId]'s home categories, first page each. */
    suspend fun homeRows(sourceId: String): List<FenrirHomeRow>

    /** A further page of one category ("See all"). Empty when there's no more. */
    suspend fun homeRowPage(sourceId: String, categoryName: String, categoryData: String, page: Int): List<FenrirHomeItem>

    /** The title's episodes (one entry for a movie). Works after a restart from the "Site|url" key. */
    suspend fun episodes(itemKey: String): List<FenrirSourceEpisode>

    /** Links for one episode. */
    suspend fun links(episode: FenrirSourceEpisode): List<FenrirSourceLink>
}

/** Play Store build: no CloudStream plugins. */
class NoCloudStreamBrowser : FenrirCloudStreamBrowser {
    override suspend fun homeSources(): List<FenrirHomeSource> = emptyList()
    override suspend fun homeRows(sourceId: String): List<FenrirHomeRow> = emptyList()
    override suspend fun homeRowPage(sourceId: String, categoryName: String, categoryData: String, page: Int) =
        emptyList<FenrirHomeItem>()
    override suspend fun episodes(itemKey: String): List<FenrirSourceEpisode> = emptyList()
    override suspend fun links(episode: FenrirSourceEpisode): List<FenrirSourceLink> = emptyList()
}

/**
 * Which CloudStream site Home shows (null = the normal Nuvio home), remembered on the TV.
 * Also the Continue watching ids for titles played from a site: "fenrircs:Site|url".
 */
object FenrirSourceHomePick {
    private const val PREFS = "fenrir_source_home"
    private const val KEY = "home_cloudstream_source"
    const val ID_PREFIX = "fenrircs:"

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        _selected.value = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun select(context: Context, sourceId: String?) {
        _selected.value = sourceId
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply { if (sourceId == null) remove(KEY) else putString(KEY, sourceId) }
            .apply()
    }

    fun isCloudStreamId(id: String?): Boolean = id?.startsWith(ID_PREFIX) == true

    /** "fenrircs:Kisskh|https://…" → "Kisskh". */
    fun sourceOf(contentId: String?): String? =
        contentId?.takeIf { isCloudStreamId(it) }?.removePrefix(ID_PREFIX)?.substringBefore("|")?.takeIf { it.isNotBlank() }
}
