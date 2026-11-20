package com.nuvio.tv.core.streams

import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanishStreamFilterTest {

    @Test
    fun `torrent labelled latino is kept`() {
        assertTrue(SpanishStreamFilter.isSpanish(torrent(title = "Movie.2024.1080p.WEB-DL.LATINO")))
    }

    @Test
    fun `castellano and espanol labels are kept`() {
        assertTrue(SpanishStreamFilter.isSpanish(torrent(title = "Película [Castellano] 4K")))
        assertTrue(SpanishStreamFilter.isSpanish(torrent(name = "Torrentio\n🇪🇸", title = "Movie 1080p")))
        assertTrue(SpanishStreamFilter.isSpanish(torrent(description = "Audio: Español")))
    }

    @Test
    fun `filename hint is checked`() {
        assertTrue(SpanishStreamFilter.isSpanish(torrent(filename = "Movie.2024.SPA.1080p.mkv")))
    }

    @Test
    fun `multi audio is kept`() {
        assertTrue(SpanishStreamFilter.isSpanish(torrent(title = "Movie 2160p MULTI")))
    }

    @Test
    fun `english and french only are dropped`() {
        assertFalse(SpanishStreamFilter.isSpanish(torrent(title = "Movie.2024.1080p.BluRay.ENG")))
        assertFalse(SpanishStreamFilter.isSpanish(torrent(title = "Movie.2024.FRENCH.720p")))
    }

    @Test
    fun `words containing es are not mistaken for spanish`() {
        assertFalse(SpanishStreamFilter.isSpanish(torrent(title = "Best.Series.Episodes.1080p")))
    }

    @Test
    fun `addons left without streams are removed`() {
        val groups = listOf(
            AddonStreams("A", null, listOf(torrent(title = "LATINO"), torrent(title = "ENG"))),
            AddonStreams("B", null, listOf(torrent(title = "ENG")))
        )
        val result = SpanishStreamFilter.filterGroups(groups)
        assertEquals(1, result.size)
        assertEquals(1, result[0].streams.size)
    }

    private fun torrent(
        name: String? = "Torrentio",
        title: String? = null,
        description: String? = null,
        filename: String? = null
    ): Stream = Stream(
        name = name,
        title = title,
        description = description,
        url = null,
        ytId = null,
        infoHash = "abcdef0123456789",
        fileIdx = 0,
        externalUrl = null,
        behaviorHints = filename?.let {
            StreamBehaviorHints(
                notWebReady = null,
                bingeGroup = null,
                countryWhitelist = null,
                proxyHeaders = null,
                filename = it
            )
        },
        addonName = "Torrentio",
        addonLogo = null
    )
}
