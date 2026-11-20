package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.profile.SpanishOnlyStore
import com.nuvio.tv.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpanishOnlySettingsViewModel @Inject constructor(
    profileManager: ProfileManager,
    private val spanishOnlyStore: SpanishOnlyStore
) : ViewModel() {

    val profiles: StateFlow<List<UserProfile>> = profileManager.profiles

    val spanishOnlyIds: StateFlow<Set<Int>> = spanishOnlyStore.spanishOnlyProfileIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun toggle(profileId: Int) {
        val enable = profileId !in spanishOnlyIds.value
        viewModelScope.launch { spanishOnlyStore.setSpanishOnly(profileId, enable) }
    }
}
