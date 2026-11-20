package com.nuvio.tv.ui.screens.anidrama

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Gives the Ani-drama tab your installed add-ons, so anime opens with IDs they understand. */
@HiltViewModel
class AniDramaViewModel @Inject constructor(
    addonRepository: AddonRepository,
) : ViewModel() {
    val addons: StateFlow<List<Addon>> = addonRepository.getInstalledAddons()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
