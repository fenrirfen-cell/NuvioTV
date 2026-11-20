package com.nuvio.tv.fenrir

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.LocaleCache
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Turns "Modo español" on or off, saving and restoring your earlier language settings. */
@HiltViewModel
class FenrirSpanishModeViewModel @Inject constructor(
    private val playerSettingsDataStore: PlayerSettingsDataStore,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
) : ViewModel() {

    private companion object {
        const val SPANISH = "es"
        const val NONE = "\u0000"
    }

    /** [onDone] gets true when the app language changed (the screen should restart). */
    fun setEnabled(context: Context, enabled: Boolean, onDone: (languageChanged: Boolean) -> Unit) {
        val app = context.applicationContext
        val prefs = FenrirSpanishMode.prefs(app)
        val localePrefs = app.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
        viewModelScope.launch {
            val currentLocale = localePrefs.getString("locale_tag", "").orEmpty()
            val player = runCatching { playerSettingsDataStore.playerSettings.first() }.getOrNull()
            if (enabled) {
                prefs.edit()
                    .putString(FenrirSpanishMode.KEY_PREV_LOCALE, currentLocale)
                    .putString(FenrirSpanishMode.KEY_PREV_TMDB, tmdbSettingsDataStore.settings.value.language)
                    .putString(FenrirSpanishMode.KEY_PREV_AUDIO, player?.preferredAudioLanguage ?: NONE)
                    .putString(
                        FenrirSpanishMode.KEY_PREV_SUBS,
                        player?.subtitleStyle?.takeUnless { it.isPreferredLanguageSystemDefault }?.preferredLanguage ?: NONE,
                    )
                    .apply()
                runCatching { tmdbSettingsDataStore.setLanguage(SPANISH) }
                runCatching { playerSettingsDataStore.setPreferredAudioLanguage(SPANISH) }
                runCatching { playerSettingsDataStore.setSubtitlePreferredLanguage(SPANISH) }
                FenrirSpanishMode.setEnabledFlag(app, true)
                val changed = currentLocale != SPANISH
                localePrefs.edit().putString("locale_tag", SPANISH).apply()
                LocaleCache.localeTag = SPANISH
                onDone(changed)
            } else {
                val prevLocale = prefs.getString(FenrirSpanishMode.KEY_PREV_LOCALE, "").orEmpty()
                val prevTmdb = prefs.getString(FenrirSpanishMode.KEY_PREV_TMDB, null)
                val prevAudio = prefs.getString(FenrirSpanishMode.KEY_PREV_AUDIO, NONE)
                val prevSubs = prefs.getString(FenrirSpanishMode.KEY_PREV_SUBS, NONE)
                if (!prevTmdb.isNullOrBlank()) runCatching { tmdbSettingsDataStore.setLanguage(prevTmdb) }
                if (prevAudio != null && prevAudio != NONE) runCatching { playerSettingsDataStore.setPreferredAudioLanguage(prevAudio) }
                runCatching {
                    playerSettingsDataStore.setSubtitlePreferredLanguage(
                        if (prevSubs == null || prevSubs == NONE) com.nuvio.tv.data.local.SubtitleLanguageOption.DEVICE else prevSubs
                    )
                }
                FenrirSpanishMode.setEnabledFlag(app, false)
                val changed = currentLocale != prevLocale
                localePrefs.edit().putString("locale_tag", prevLocale).apply()
                LocaleCache.localeTag = prevLocale
                onDone(changed)
            }
        }
    }
}
