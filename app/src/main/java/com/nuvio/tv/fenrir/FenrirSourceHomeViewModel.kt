package com.nuvio.tv.fenrir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** What Home shows for the picked CloudStream site. */
data class FenrirSourceHomeState(
    val sourceId: String? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val rows: List<FenrirHomeRow> = emptyList(),
)

/** Episode and link to play, found again for a Continue watching entry. */
data class FenrirResumePlay(
    val episode: FenrirSourceEpisode,
    val episodeIndex: Int,
    val episodeCount: Int,
    val link: FenrirSourceLink,
)

/**
 * Fenrir source home, like the phone app (and Zangetsu / CloudStream): Home can show one
 * CloudStream site's own categories. Posters open Nuvio's show page when one of your add-ons
 * knows the title, otherwise the site's own episode list.
 */
@HiltViewModel
class FenrirSourceHomeViewModel @Inject constructor(
    private val browser: FenrirCloudStreamBrowser,
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private companion object {
        /** Rows already loaded, kept while the app runs so going back to Home is instant. */
        val cache = HashMap<String, List<FenrirHomeRow>>()
    }

    private val _state = MutableStateFlow(FenrirSourceHomeState())
    val state: StateFlow<FenrirSourceHomeState> = _state.asStateFlow()

    private val _sources = MutableStateFlow<List<FenrirHomeSource>?>(null)
    /** Sites with their own home page; null until checked. */
    val sources: StateFlow<List<FenrirHomeSource>?> = _sources.asStateFlow()

    private var loadJob: Job? = null

    init {
        refreshSources()
    }

    fun refreshSources() {
        viewModelScope.launch {
            _sources.value = runCatching { browser.homeSources() }.getOrDefault(emptyList())
        }
    }

    /** Loads [sourceId]'s rows, showing the last ones straight away. */
    fun load(sourceId: String, force: Boolean = false) {
        val current = _state.value
        if (!force && current.sourceId == sourceId && (current.loading || current.rows.isNotEmpty())) return
        val cached = cache[sourceId]
        if (!force && cached != null) {
            _state.value = FenrirSourceHomeState(sourceId, rows = cached)
            return
        }
        loadJob?.cancel()
        _state.value = FenrirSourceHomeState(sourceId, loading = true, rows = cached.orEmpty())
        loadJob = viewModelScope.launch {
            // Plugins may still be loading right after start; wait for this site briefly.
            var known = runCatching { browser.homeSources() }.getOrDefault(emptyList())
            var waited = 0
            while (known.none { it.id == sourceId } && waited < 10_000) {
                delay(1_000)
                waited += 1_000
                known = runCatching { browser.homeSources() }.getOrDefault(emptyList())
            }
            _sources.value = known
            val rows = runCatching { browser.homeRows(sourceId) }.getOrDefault(emptyList())
            if (rows.isNotEmpty()) cache[sourceId] = rows
            _state.value = FenrirSourceHomeState(
                sourceId = sourceId,
                loading = false,
                failed = rows.isEmpty(),
                rows = rows,
            )
        }
    }

    suspend fun morePage(sourceId: String, row: FenrirHomeRow, page: Int): List<FenrirHomeItem> =
        runCatching { browser.homeRowPage(sourceId, row.categoryName, row.categoryData, page) }.getOrDefault(emptyList())

    suspend fun episodes(item: FenrirHomeItem): List<FenrirSourceEpisode> =
        runCatching { browser.episodes(item.key) }.getOrDefault(emptyList())

    suspend fun links(episode: FenrirSourceEpisode): List<FenrirSourceLink> =
        runCatching { browser.links(episode) }.getOrDefault(emptyList())

    /**
     * For a Continue watching entry played from a site ("fenrircs:Site|url"): finds the same
     * episode on that site again and its first link. Null when the site doesn't answer.
     */
    suspend fun resume(
        contentId: String,
        videoId: String?,
        episodeTitle: String?,
        season: Int?,
        episode: Int?,
    ): FenrirResumePlay? {
        val key = contentId.removePrefix(FenrirSourceHomePick.ID_PREFIX)
        var episodes = runCatching { browser.episodes(key) }.getOrDefault(emptyList())
        var waited = 0
        while (episodes.isEmpty() && waited < 8_000) {
            // Plugins may still be loading right after start.
            delay(2_000)
            waited += 2_000
            episodes = runCatching { browser.episodes(key) }.getOrDefault(emptyList())
        }
        if (episodes.isEmpty()) return null
        val index = videoId?.substringAfterLast("#", "")?.toIntOrNull()
        val target = episodes.getOrNull(index ?: -1)?.takeIf { episodeTitle == null || it.name == episodeTitle }
            ?: episodes.firstOrNull { episodeTitle != null && it.name == episodeTitle }
            ?: episodes.firstOrNull { episode != null && it.episode == episode && (season == null || it.season == season) }
            ?: episodes.getOrNull(index ?: -1)
            ?: return null
        val link = runCatching { browser.links(target) }.getOrDefault(emptyList()).firstOrNull() ?: return null
        return FenrirResumePlay(target, episodes.indexOf(target), episodes.size, link)
    }

    private fun normalize(text: String) = text.lowercase()
        .replace(Regex("\\(\\d{4}\\)"), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    /**
     * Looks the title up in your add-ons' search catalogs (like Zangetsu matching to its own
     * catalogue), so Nuvio's show page can open. Null when nothing matches well within a few seconds.
     */
    suspend fun matchToNuvio(item: FenrirHomeItem): MetaPreview? = withTimeoutOrNull(8_000) {
        val wanted = normalize(item.title)
        if (wanted.isEmpty()) return@withTimeoutOrNull null
        val types = if (item.isMovie) listOf("movie") else listOf("series", "anime", "movie")
        val addons = runCatching { addonRepository.getInstalledAddons().first() }.getOrDefault(emptyList())
            .filter { it.enabled }
        val requests = addons.flatMap { addon ->
            addon.catalogs
                .filter { catalog ->
                    catalog.apiType in types &&
                        catalog.extra.any { it.name == "search" } &&
                        catalog.extra.none { it.isRequired && it.name != "search" }
                }
                .map { catalog -> addon to catalog }
        }.take(8)
        if (requests.isEmpty()) return@withTimeoutOrNull null
        val results = coroutineScope {
            requests.map { (addon, catalog) ->
                async(Dispatchers.IO) {
                    withTimeoutOrNull(6_000) {
                        runCatching {
                            val result = catalogRepository.getCatalog(
                                addonBaseUrl = addon.baseUrl,
                                addonId = addon.id,
                                addonName = addon.name,
                                catalogId = catalog.id,
                                catalogName = catalog.name,
                                type = catalog.apiType,
                                extraArgs = mapOf("search" to item.title),
                            ).first { it !is NetworkResult.Loading }
                            (result as? NetworkResult.Success)?.data?.items.orEmpty()
                        }.getOrNull()
                    }.orEmpty()
                }
            }.awaitAll().flatten()
        }
        results
            .mapNotNull { meta ->
                val name = normalize(meta.name)
                var score = when {
                    name == wanted -> 100
                    name.startsWith(wanted) && name.length - wanted.length <= 6 -> 70
                    else -> 0
                }
                if (score == 0) return@mapNotNull null
                val year = meta.releaseInfo?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() }
                if (item.year != null && year != null) score += if (year == item.year) 20 else -40
                if (item.isMovie && meta.apiType != "movie") score -= 30
                if (!item.isMovie && meta.apiType == "movie") score -= 30
                meta to score
            }
            .filter { it.second >= 70 }
            .maxByOrNull { it.second }
            ?.first
    }
}
