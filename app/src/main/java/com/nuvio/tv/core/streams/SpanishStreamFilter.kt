package com.nuvio.tv.core.streams

import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream

/**
 * Keeps only Spanish streams for "Solo español" profiles. Works the same for torrents,
 * debrid and direct links, because it reads the labels the add-on gives each stream.
 */
object SpanishStreamFilter {

    /** Streams labelled MULTI / DUAL audio usually include a Spanish track, so they are kept. */
    private const val ALLOW_MULTI_AUDIO = true

    private val spanishPatterns = listOf(
        "\\bespa[ñn]ol\\b", "\\bspanish\\b", "\\bcastellano\\b", "\\blatino\\b",
        "\\blatam\\b", "\\blat\\b", "\\bspa\\b", "\\besp\\b", "\\bes[-_ ]?(es|mx|419)\\b",
        "[\\[(]\\s*es\\s*[\\])]", "\\bsub(titulado|s)?[ .-]?esp",
        // Flags: Spain, Mexico, Argentina, Colombia
        "🇪🇸|🇲🇽|🇦🇷|🇨🇴"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val multiAudioPatterns = listOf("\\bmulti\\b", "\\bdual\\b")
        .map { Regex(it, RegexOption.IGNORE_CASE) }

    fun isSpanish(stream: Stream): Boolean {
        // The user's own media server library (Jellyfin/Plex/etc.) is never hidden.
        if (stream.serverTarget != null) return true

        val text = listOfNotNull(
            stream.name,
            stream.title,
            stream.description,
            stream.behaviorHints?.filename,
            stream.pluginSourceName
        ).joinToString(" ")

        if (spanishPatterns.any { it.containsMatchIn(text) }) return true
        if (ALLOW_MULTI_AUDIO && multiAudioPatterns.any { it.containsMatchIn(text) }) return true
        return false
    }

    fun filter(streams: List<Stream>): List<Stream> = streams.filter(::isSpanish)

    /** Filters each add-on's streams and drops add-ons left with none. */
    fun filterGroups(groups: List<AddonStreams>): List<AddonStreams> =
        groups.map { it.copy(streams = filter(it.streams)) }.filter { it.streams.isNotEmpty() }
}
