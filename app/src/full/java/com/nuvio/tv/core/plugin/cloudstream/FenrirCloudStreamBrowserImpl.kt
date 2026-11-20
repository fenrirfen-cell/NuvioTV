package com.nuvio.tv.core.plugin.cloudstream

import android.util.Log
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.nuvio.tv.core.plugin.PluginManager
import com.nuvio.tv.domain.model.RepositoryType
import com.nuvio.tv.fenrir.FenrirCloudStreamBrowser
import com.nuvio.tv.fenrir.FenrirHomeItem
import com.nuvio.tv.fenrir.FenrirHomeRow
import com.nuvio.tv.fenrir.FenrirHomeSource
import com.nuvio.tv.fenrir.FenrirSourceEpisode
import com.nuvio.tv.fenrir.FenrirSourceLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fenrir: browse the CloudStream plugins you installed (Settings > Plugins) like the phone app:
 * a site's own home categories, a title's episodes, an episode's links.
 */
@Singleton
class FenrirCloudStreamBrowserImpl @Inject constructor(
    private val pluginManager: PluginManager,
    private val extensionLoader: ExternalExtensionLoader,
) : FenrirCloudStreamBrowser {

    private companion object {
        const val TAG = "FenrirCloudStream"
        const val HOME_ROW_TIMEOUT_MS = 20_000L
        const val LOAD_TIMEOUT_MS = 30_000L
    }

    /** Title pages already seen, by "Site|url". */
    private val hits = ConcurrentHashMap<String, Pair<MainAPI, String>>()
    /** Episode data for links, by episode key. */
    private val episodeData = ConcurrentHashMap<String, Pair<MainAPI, String>>()

    /** Every site from your enabled CloudStream plugins. */
    private suspend fun providers(): List<MainAPI> = withContext(Dispatchers.IO) {
        val scrapers = runCatching { pluginManager.enabledScrapers.first() }.getOrDefault(emptyList())
            .filter { it.type == RepositoryType.EXTERNAL_DEX }
        scrapers.flatMap { scraper ->
            runCatching { extensionLoader.getAllApis(scraper.id) }
                .onFailure { Log.w(TAG, "Couldn't load ${scraper.name}: ${it.message}") }
                .getOrDefault(emptyList())
        }.distinctBy { it.name }
    }

    override suspend fun homeSources(): List<FenrirHomeSource> = withContext(Dispatchers.IO) {
        val scrapers = runCatching { pluginManager.enabledScrapers.first() }.getOrDefault(emptyList())
            .filter { it.type == RepositoryType.EXTERNAL_DEX }
        scrapers.flatMap { scraper ->
            runCatching { extensionLoader.getAllApis(scraper.id) }.getOrDefault(emptyList())
                .filter { runCatching { it.hasMainPage }.getOrDefault(false) }
                .map { provider ->
                    FenrirHomeSource(
                        id = provider.name,
                        name = provider.name,
                        subtitle = listOfNotNull(
                            scraper.name.takeIf { it != provider.name },
                            provider.lang.uppercase().takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        iconUrl = scraper.logo,
                    )
                }
        }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    private suspend fun homeProvider(sourceId: String): MainAPI? =
        providers().firstOrNull { it.name == sourceId && runCatching { it.hasMainPage }.getOrDefault(false) }

    override suspend fun homeRows(sourceId: String): List<FenrirHomeRow> = coroutineScope {
        val provider = homeProvider(sourceId) ?: return@coroutineScope emptyList()
        val categories = provider.mainPage
        // Every category at once, each with its own time limit, so one slow one can't hold up the rest.
        val responses = categories.map { category ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(HOME_ROW_TIMEOUT_MS) {
                    runCatching {
                        provider.getMainPage(1, MainPageRequest(category.name, category.data, category.horizontalImages))
                    }.onFailure { Log.w(TAG, "${provider.name} home '${category.name}': ${it.message}") }.getOrNull()
                }
            }
        }.awaitAll()
        responses.flatMapIndexed { index, response ->
            val category = categories.getOrNull(index)
            response?.items.orEmpty().mapNotNull { list ->
                val items = list.list.map { it.toHomeItem(provider) }
                if (items.isEmpty()) {
                    null
                } else {
                    FenrirHomeRow(
                        title = list.name.ifBlank { category?.name.orEmpty() },
                        categoryName = category?.name ?: list.name,
                        categoryData = category?.data.orEmpty(),
                        items = items,
                        horizontalImages = list.isHorizontalImages,
                    )
                }
            }
        }
    }

    override suspend fun homeRowPage(
        sourceId: String,
        categoryName: String,
        categoryData: String,
        page: Int,
    ): List<FenrirHomeItem> = withContext(Dispatchers.IO) {
        val provider = homeProvider(sourceId) ?: return@withContext emptyList()
        val category = provider.mainPage.firstOrNull { it.name == categoryName && it.data == categoryData }
            ?: provider.mainPage.firstOrNull { it.name == categoryName }
            ?: return@withContext emptyList()
        val response = withTimeoutOrNull(HOME_ROW_TIMEOUT_MS) {
            runCatching {
                provider.getMainPage(page, MainPageRequest(category.name, category.data, category.horizontalImages))
            }.getOrNull()
        } ?: return@withContext emptyList()
        response.items.flatMap { list -> list.list.map { it.toHomeItem(provider) } }
    }

    private fun SearchResponse.toHomeItem(provider: MainAPI): FenrirHomeItem {
        val key = "${provider.name}|$url"
        hits[key] = provider to url
        val dub = (this as? AnimeSearchResponse)?.dubStatus.orEmpty()
        val badge = listOfNotNull(
            "SUB".takeIf { DubStatus.Subbed in dub },
            "DUB".takeIf { DubStatus.Dubbed in dub },
        ).joinToString(" · ").ifBlank { null }
        val year = (this as? MovieSearchResponse)?.year
            ?: (this as? TvSeriesSearchResponse)?.year
            ?: (this as? AnimeSearchResponse)?.year
        val typeName = type?.name.orEmpty()
        return FenrirHomeItem(
            key = key,
            sourceName = provider.name,
            title = name,
            poster = posterUrl,
            badge = badge,
            year = year,
            isMovie = typeName in setOf("Movie", "AnimeMovie", "Documentary"),
        )
    }

    override suspend fun episodes(itemKey: String): List<FenrirSourceEpisode> = withContext(Dispatchers.IO) {
        // After a restart the cache is empty: the key itself is "Site|url" (Continue watching).
        val (provider, url) = hits[itemKey]
            ?: providers().firstOrNull { it.name == itemKey.substringBefore("|") }?.let { it to itemKey.substringAfter("|") }
            ?: return@withContext emptyList()
        val response = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            runCatching { provider.load(url) }
                .onFailure { Log.w(TAG, "${provider.name} load: ${it.message}") }
                .getOrNull()
        } ?: return@withContext emptyList()
        data class Row(val data: String, val label: String, val season: Int?, val episode: Int?)
        val list: List<Row> = when (response) {
            is MovieLoadResponse -> listOf(Row(response.dataUrl, response.name, null, null))
            is TvSeriesLoadResponse -> response.episodes.map { ep -> Row(ep.data, ep.label(), ep.season, ep.episode) }
            is AnimeLoadResponse -> response.episodes.flatMap { (dub, eps) ->
                eps.map { ep -> Row(ep.data, "${ep.label()} · ${if (dub == DubStatus.Dubbed) "Dub" else "Sub"}", ep.season, ep.episode) }
            }
            else -> emptyList()
        }
        list.mapIndexed { index, row ->
            val key = "$itemKey#$index"
            episodeData[key] = provider to row.data
            FenrirSourceEpisode(key, row.label, row.season, row.episode)
        }
    }

    private fun Episode.label(): String = buildString {
        if (season != null) append("S$season ")
        if (episode != null) append("E$episode")
        name?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append(" · "); append(it) }
        if (isEmpty()) append("Episode")
    }

    override suspend fun links(episode: FenrirSourceEpisode): List<FenrirSourceLink> = withContext(Dispatchers.IO) {
        val (provider, data) = episodeData[episode.key] ?: return@withContext emptyList()
        val subtitles = mutableListOf<Pair<String, String>>()
        val links = mutableListOf<ExtractorLink>()
        withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            runCatching {
                provider.loadLinks(
                    data = data,
                    isCasting = false,
                    subtitleCallback = { sub -> synchronized(subtitles) { subtitles += sub.url to sub.lang } },
                    callback = { link -> synchronized(links) { links += link } },
                )
            }.onFailure { Log.w(TAG, "${provider.name} links: ${it.message}") }
        }
        val subs = synchronized(subtitles) { subtitles.toList() }
        synchronized(links) { links.toList() }
            .filter { it.type != ExtractorLinkType.TORRENT && it.type != ExtractorLinkType.MAGNET }
            .map { link ->
                val headers = buildMap {
                    putAll(link.headers)
                    if (link.referer.isNotBlank() && keys.none { it.equals("Referer", ignoreCase = true) }) {
                        put("Referer", link.referer)
                    }
                }
                val quality = if (link.quality in 100..4320) "${link.quality}p" else null
                FenrirSourceLink(
                    sourceName = provider.name,
                    label = listOfNotNull(link.name.takeIf { it.isNotBlank() }, quality).joinToString(" · ").ifBlank { "Video" },
                    detail = "CloudStream · ${provider.name}",
                    url = link.url,
                    headers = headers,
                    subtitles = subs,
                )
            }
    }
}
