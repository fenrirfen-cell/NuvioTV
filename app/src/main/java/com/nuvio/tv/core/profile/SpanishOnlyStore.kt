package com.nuvio.tv.core.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.spanishOnlyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "spanish_only_profiles",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Which profiles are "Solo español": they only get Spanish streams.
 *
 * Kept on this device rather than on [com.nuvio.tv.domain.model.UserProfile], because the profile
 * list is replaced by the account's copy on sync and the server has no field for this.
 */
@Singleton
class SpanishOnlyStore @Inject constructor(
    @ApplicationContext context: Context
) : ProfileScopedCredentialStore {

    private val dataStore = context.spanishOnlyDataStore
    private val profileIdsKey = stringSetPreferencesKey("profile_ids")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val spanishOnlyProfileIds: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[profileIdsKey].orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
    }

    suspend fun isSpanishOnly(profileId: Int): Boolean =
        profileId in spanishOnlyProfileIds.first()

    suspend fun setSpanishOnly(profileId: Int, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[profileIdsKey].orEmpty()
            prefs[profileIdsKey] = if (enabled) current + profileId.toString() else current - profileId.toString()
        }
    }

    // Profile ids are reused, so a deleted profile's setting must not carry over to a new one.
    override fun removeProfile(profileId: Int) {
        scope.launch { setSpanishOnly(profileId, false) }
    }

    override fun clearAllProfiles() {
        scope.launch { dataStore.edit { it.remove(profileIdsKey) } }
    }
}
