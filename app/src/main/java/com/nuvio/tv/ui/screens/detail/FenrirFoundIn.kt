package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.repository.StreamRepository
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** What "Play" on a show page would start, so "Found in" checks the same episode. */
data class FoundInTarget(val type: String, val videoId: String, val season: Int?, val episode: Int?)

/** How many links each of your sources found ("Found in" on show pages). */
data class FoundInState(
    val checking: Boolean = true,
    /** Source name to link count, in the order they answered. */
    val sources: List<Pair<String, Int>> = emptyList(),
)

/**
 * Fenrir: asks your sources for the title Play would start. It uses the same stream search as the
 * source list, so pressing Play afterwards can reuse what was found.
 */
@HiltViewModel
class FenrirFoundInViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
) : ViewModel() {
    fun check(target: FoundInTarget): Flow<FoundInState> = flow {
        emit(FoundInState(checking = true))
        var last = FoundInState(checking = true)
        streamRepository.getStreamsFromAllAddons(
            type = target.type,
            videoId = target.videoId,
            season = target.season,
            episode = target.episode,
        ).collect { result ->
            if (result is NetworkResult.Success) {
                val counts = LinkedHashMap<String, Int>()
                result.data.forEach { group ->
                    counts[group.addonName] = (counts[group.addonName] ?: 0) + group.streams.size
                }
                last = FoundInState(checking = true, sources = counts.toList())
                emit(last)
            }
        }
        emit(last.copy(checking = false))
    }
}

private val FoundColor = Color(0xFF1D9E75)

/** "Found in your sources" badges under the Play button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FenrirFoundInRow(
    target: FoundInTarget,
    modifier: Modifier = Modifier,
    viewModel: FenrirFoundInViewModel = hiltViewModel(),
) {
    val state by produceState(FoundInState(), target) {
        value = FoundInState()
        runCatching { viewModel.check(target).collect { value = it } }
        if (value.checking) value = value.copy(checking = false)
    }
    if (!state.checking && state.sources.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = buildString {
                append(if (state.checking && state.sources.isEmpty()) "Checking your sources…" else "Found in your sources")
                if (target.season != null && target.episode != null) append(" · S${target.season} E${target.episode}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = NuvioTheme.colors.TextSecondary,
        )
        if (state.sources.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(0.6f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Sources with links first, then the empty ones.
                state.sources
                    .sortedBy { (_, count) -> if (count > 0) 0 else 1 }
                    .forEach { (name, count) -> SourceBadge(name, count) }
                if (state.checking) SourceBadge("More", null)
            }
        }
    }
}

@Composable
private fun SourceBadge(name: String, count: Int?) {
    val found = count != null && count > 0
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (found) FoundColor.copy(alpha = 0.18f) else Color(0xFF1C1D26))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (found) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(FoundColor))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = when {
                count == null -> "$name · checking"
                count > 0 -> "$name · $count"
                else -> "$name · none"
            },
            style = MaterialTheme.typography.labelMedium,
            color = when {
                found -> FoundColor
                count == null -> NuvioTheme.colors.TextSecondary
                else -> NuvioTheme.colors.TextSecondary.copy(alpha = 0.6f)
            },
            maxLines = 1,
        )
    }
}
