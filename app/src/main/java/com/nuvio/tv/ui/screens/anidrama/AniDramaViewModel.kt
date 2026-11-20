package com.nuvio.tv.ui.screens.anidrama

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Gives the Ani-drama tab your add-ons (so anime opens with IDs they understand) and library. */
@HiltViewModel
class AniDramaViewModel @Inject constructor(
    addonRepository: AddonRepository,
    libraryRepository: LibraryRepository,
) : ViewModel() {
    /** IDs in your library, for the calendar's "My shows". */
    val libraryIds: StateFlow<Set<String>> = libraryRepository.libraryItems
        .map { items -> items.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val addons: StateFlow<List<Addon>> = addonRepository.getInstalledAddons()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
