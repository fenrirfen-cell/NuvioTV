package com.nuvio.tv.fenrir

import android.content.Context
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer

/**
 * Fenrir "Modo español": the TV app in Spanish for Spanish speakers.
 * - The app, show info (TMDB), audio and subtitles are set to Spanish.
 * - The source list only keeps links with Spanish audio (Latino or Castellano).
 * - Links that aren't in Spanish but come with Spanish subtitles stay, marked
 *   "Subtítulos en español", after the Spanish-audio ones.
 * Turning it off puts your earlier settings back.
 */
object FenrirSpanishMode {
    private const val PREFS = "fenrir_spanish_mode"
    private const val KEY_ENABLED = "enabled"
    const val KEY_PREV_LOCALE = "prev_locale"
    const val KEY_PREV_TMDB = "prev_tmdb"
    const val KEY_PREV_AUDIO = "prev_audio"
    const val KEY_PREV_SUBS = "prev_subs"

    /** Label put on links that are Spanish only through subtitles. */
    const val SUBS_LABEL = "Subtítulos en español"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private var loaded = false

    fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Read once at app start. */
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        _enabled.value = prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabledFlag(context: Context, enabled: Boolean) {
        _enabled.value = enabled
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ------------------------------------------------------------------ language detection

    enum class Kind { SPANISH_AUDIO, SPANISH_SUBS, OTHER }

    private fun plain(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Spanish subtitle phrases ("sub español", "subtitulado", "VOSE", "spanish subs"...). */
    private val subsPhrases = listOf(
        Regex("""\bsubs?(titulos|titles|titulado|titulada|tituladas)?\s*[:\-]?\s*(en\s+)?(espanol|castellano|latino|spanish|esp|spa|es)\b"""),
        Regex("""\b(espanol|castellano|spanish|esp|spa)\s*subs?(titles|titulos)?\b"""),
        Regex("""\bsubtitulad[oa]s?\b"""),
        Regex("""\bvose\b"""),
        Regex("""\bsub\s*esp\b"""),
    )

    /** Spanish audio markers (Latino, Castellano, Español, flags, language codes). */
    private val audioWords = Regex(
        """\b(latino|latin\s*spanish|castellano|espanol|spanish|audio\s*latino|dual\s*latino|lat|spa|esp|es-mx|es-419|es-es|es_mx|es_es)\b"""
    )
    private val spanishFlags = listOf("🇪🇸", "🇲🇽", "🇦🇷", "🇨🇴", "🇨🇱", "🇵🇪", "🇻🇪", "🇺🇾", "🇪🇨", "🇧🇴", "🇵🇾", "🇨🇷", "🇬🇹", "🇭🇳", "🇸🇻", "🇳🇮", "🇵🇦", "🇩🇴", "🇵🇷", "🇨🇺")

    fun classify(stream: Stream): Kind {
        val raw = listOfNotNull(
            stream.name,
            stream.title,
            stream.description,
            stream.behaviorHints?.filename,
            stream.pluginSourceName,
        ).joinToString(" \n ") + " " + stream.badges.joinToString(" ") { it.name }
        var text = plain(raw)
        var subs = false
        subsPhrases.forEach { phrase ->
            if (phrase.containsMatchIn(text)) {
                subs = true
                text = phrase.replace(text, " ")
            }
        }
        val flagAudio = spanishFlags.any { it in raw }
        if (flagAudio || audioWords.containsMatchIn(text)) return Kind.SPANISH_AUDIO
        if (subs) return Kind.SPANISH_SUBS
        val spanishSubtitleFile = stream.subtitles.any { sub ->
            val lang = sub.lang.lowercase()
            lang == "es" || lang == "spa" || lang.startsWith("es-") || lang.startsWith("es_") ||
                plain(sub.label.orEmpty()).let { "espanol" in it || "spanish" in it || "latino" in it || "castellano" in it }
        }
        return if (spanishSubtitleFile) Kind.SPANISH_SUBS else Kind.OTHER
    }

    /** Keeps Spanish-audio links, then Spanish-subtitle links (marked), in each source. */
    fun filter(groups: List<AddonStreams>): List<AddonStreams> = groups.mapNotNull { group ->
        val audio = mutableListOf<Stream>()
        val subs = mutableListOf<Stream>()
        group.streams.forEach { stream ->
            when (classify(stream)) {
                Kind.SPANISH_AUDIO -> audio += stream
                Kind.SPANISH_SUBS -> subs += stream.copy(
                    description = listOfNotNull("⚠ $SUBS_LABEL (audio en otro idioma)", stream.description)
                        .joinToString("\n"),
                )
                Kind.OTHER -> Unit
            }
        }
        val kept = audio + subs
        if (kept.isEmpty()) null else group.copy(streams = kept)
    }
}
