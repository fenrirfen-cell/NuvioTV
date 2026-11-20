package com.nuvio.tv.ui.screens.anidrama

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape

/** Type and ID that Nuvio's own show page should open for an Ani-drama poster. */
internal data class AniDramaTarget(val type: String, val id: String)

/**
 * Anime prefers a Kitsu ID when one of your add-ons can show Kitsu pages (that's what most
 * anime stream add-ons expect); otherwise it uses IMDb or TMDB like any other show.
 */
internal fun AniDramaItem.target(addons: List<Addon>): AniDramaTarget = when (val source = source) {
    is AniDramaSource.Tmdb -> AniDramaTarget("series", "tmdb:${source.tmdbId}")
    is AniDramaSource.AniList -> source.preferredTarget(addons.filter { it.enabled })
}

internal fun AniDramaItem.toMetaPreview(addons: List<Addon>): MetaPreview {
    val target = target(addons)
    return MetaPreview(
        id = target.id,
        type = ContentType.fromString(target.type),
        rawType = target.type,
        name = title,
        poster = poster,
        posterShape = PosterShape.POSTER,
        background = backdrop,
        logo = null,
        description = description,
        releaseInfo = year,
        imdbRating = null,
        genres = genres,
        rawPosterUrl = poster,
    )
}

/** True when the add-on declares a meta resource for [type] whose ID prefixes match [id]. */
private fun Addon.supportsMeta(type: String, id: String): Boolean =
    resources.any { resource ->
        if (resource.name != "meta") return@any false
        val typeOk = if (resource.types.isNotEmpty()) {
            resource.types.any { it.equals(type, ignoreCase = true) }
        } else {
            rawTypes.any { it.equals(type, ignoreCase = true) }
        }
        val prefixes = resource.idPrefixes?.takeIf { it.isNotEmpty() } ?: idPrefixes
        typeOk && prefixes.any { id.startsWith(it, ignoreCase = true) }
    }

private fun AniDramaSource.AniList.preferredTarget(addons: List<Addon>): AniDramaTarget {
    val defaultType = if (isMovie) "movie" else "series"
    val typeOrder = if (isMovie) listOf("movie", "anime", "series") else listOf("anime", "series", "movie")

    fun addonType(id: String): String? =
        typeOrder.firstOrNull { type -> addons.any { it.supportsMeta(type, id) } }

    kitsuId?.let { kitsu ->
        val id = "kitsu:$kitsu"
        addonType(id)?.let { return AniDramaTarget(it, id) }
    }
    imdbId?.let { return AniDramaTarget(defaultType, it) }
    tmdbId?.let { return AniDramaTarget(defaultType, "tmdb:$it") }
    val aniList = "anilist:$aniListId"
    addonType(aniList)?.let { return AniDramaTarget(it, aniList) }
    malId?.let { mal ->
        val id = "mal:$mal"
        addonType(id)?.let { return AniDramaTarget(it, id) }
    }
    kitsuId?.let { return AniDramaTarget("anime", "kitsu:$it") }
    return AniDramaTarget("anime", aniList)
}
