package com.nuvio.tv.ui.screens.player

import android.content.Context
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.StreamAutoPlaySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fenrir: "try the next source automatically" for any link, including ones you picked yourself
 * (Nuvio's own fallback only covers auto-selected sources). On by default; switch in
 * Settings > Playback.
 */
object FenrirSourceFallback {
    private const val PREFS = "fenrir_source_fallback"
    private const val KEY_ENABLED = "enabled"

    private val _enabled = MutableStateFlow<Boolean?>(null)

    fun enabledFlow(context: Context): StateFlow<Boolean?> {
        if (_enabled.value == null) _enabled.value = isEnabled(context)
        return _enabled.asStateFlow()
    }

    fun isEnabled(context: Context): Boolean =
        _enabled.value ?: context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
            .also { _enabled.value = it }

    fun setEnabled(context: Context, enabled: Boolean) {
        _enabled.value = enabled
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    /** Settings to pick the next source with: your add-on order when you pick sources by hand. */
    internal fun candidateSettings(settings: PlayerSettings): PlayerSettings =
        if (settings.streamAutoPlayMode == StreamAutoPlayMode.MANUAL) {
            settings.copy(
                streamAutoPlayMode = StreamAutoPlayMode.FIRST_STREAM,
                streamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES,
            )
        } else {
            settings
        }
}
