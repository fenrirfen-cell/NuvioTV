package com.nuvio.tv.ui.screens.anidrama

import com.nuvio.tv.domain.model.CatalogRow

/**
 * Decides which add-on rows are anime or Asian-drama rows (same words as the phone app),
 * so the Ani-drama tab can show them under "From your add-ons".
 */
internal object AniDramaRows {
    private val animeWords = listOf(
        "anime", "kitsu", "anilist", "myanimelist", "my anime list", "donghua",
        "crunchyroll", "hidive",
    )
    private val koreanWords = listOf("k-drama", "kdrama", "k drama", "korean", "korea")
    private val chineseWords = listOf("c-drama", "cdrama", "c drama", "chinese", "china", "mandarin", "wuxia", "xianxia")
    private val japaneseWords = listOf("j-drama", "jdrama", "j drama", "japanese drama", "dorama", "japan")
    private val asianWords = listOf("asian drama", "asian series", "mydramalist", "dramacool", "kisskh", "viki")

    /** Which side of the tab an add-on row belongs to, or null if it isn't anime / Asian drama. */
    fun regionOf(row: CatalogRow): AniDramaRegion? {
        val type = row.rawType.lowercase()
        val text = " ${row.catalogName} ${row.addonName} ".lowercase()
        if (type == "anime" || animeWords.any { it in text }) return AniDramaRegion.Anime
        if (koreanWords.any { it in text }) return AniDramaRegion.KDrama
        if (chineseWords.any { it in text }) return AniDramaRegion.CDrama
        if (japaneseWords.any { it in text }) return AniDramaRegion.JDrama
        if (asianWords.any { it in text }) return AniDramaRegion.KDrama
        return null
    }

    /** Loaded add-on rows that belong to [region]. */
    fun rowsFor(region: AniDramaRegion, rows: List<CatalogRow>): List<CatalogRow> =
        rows.filter { it.items.isNotEmpty() && regionOf(it) == region }
}
