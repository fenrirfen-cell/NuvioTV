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
 * - Links with Spanish audio (Latino or Castellano) come first; when there are any, only those show.
 * - Only when no source has Spanish audio, other audio (Japanese, Korean, English…) is used
 *   when there are Spanish subtitles:
 *   either your subtitle add-ons have Spanish ones for that title, or the link brings its own
 *   (including "multi-sub" releases). Those links are marked "Subtítulos en español".
 * - Links with neither are hidden.
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

    /** "Multi-sub" releases (common for anime) usually include Spanish subtitles. */
    private val multiSubs = Regex("""\bmulti[\s\-_.]?(subs?|subtitles?|subtitulos)\b""")

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
        if (subs || multiSubs.containsMatchIn(text)) return Kind.SPANISH_SUBS
        val spanishSubtitleFile = stream.subtitles.any { sub -> isSpanishSubtitle(sub.lang, sub.label) }
        return if (spanishSubtitleFile) Kind.SPANISH_SUBS else Kind.OTHER
    }

    /**
     * Spanish audio first: when any source has a Spanish-audio link for this title, only those
     * are shown. Only when none has Spanish audio does it fall back to links with Spanish
     * subtitles (marked). When [spanishSubtitlesAvailable] (your subtitle add-ons have Spanish
     * ones for this title), every link counts as having Spanish subtitles, whatever its audio.
     */
    fun filter(groups: List<AddonStreams>, spanishSubtitlesAvailable: Boolean = false): List<AddonStreams> {
        val kinds = groups.map { group -> group.streams.map { it to classify(it) } }
        val anySpanishAudio = kinds.any { list -> list.any { it.second == Kind.SPANISH_AUDIO } }
        return groups.mapIndexedNotNull { index, group ->
            val kept = kinds[index].mapNotNull { (stream, kind) ->
                when {
                    kind == Kind.SPANISH_AUDIO -> stream.copy(
                        description = listOfNotNull("🇪🇸 Audio en español", stream.description).joinToString("\n"),
                    )
                    anySpanishAudio -> null
                    kind == Kind.SPANISH_SUBS -> stream.copy(
                        description = listOfNotNull("💬 $SUBS_LABEL", stream.description).joinToString("\n"),
                    )
                    spanishSubtitlesAvailable -> stream.copy(
                        description = listOfNotNull("💬 $SUBS_LABEL (de tus add-ons de subtítulos)", stream.description)
                            .joinToString("\n"),
                    )
                    else -> null
                }
            }
            if (kept.isEmpty()) null else group.copy(streams = kept)
        }
    }

    /** True for Spanish subtitle language codes and names ("es", "spa", "es-419", "Español"…). */
    fun isSpanishSubtitle(lang: String?, label: String? = null): Boolean {
        val code = lang.orEmpty().lowercase().trim()
        if (code == "es" || code == "spa" || code == "esp" || code.startsWith("es-") || code.startsWith("es_")) return true
        val name = plain("$lang ${label.orEmpty()}")
        return "espanol" in name || "spanish" in name || "latino" in name || "castellano" in name
    }

    private val subtitleChecks = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Remembers per title whether subtitle add-ons have Spanish subtitles. */
    fun cachedSubtitleCheck(key: String): Boolean? = subtitleChecks[key]

    fun rememberSubtitleCheck(key: String, available: Boolean) {
        if (subtitleChecks.size > 200) subtitleChecks.clear()
        subtitleChecks[key] = available
    }
}
