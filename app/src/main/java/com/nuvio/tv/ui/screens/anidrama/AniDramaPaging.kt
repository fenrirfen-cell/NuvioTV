package com.nuvio.tv.ui.screens.anidrama

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A list that loads page by page (grids keep loading as you scroll). */
internal class AniDramaPagedList(
    private val scope: CoroutineScope,
    private val maxItems: Int,
    private val loader: suspend (page: Int) -> AniDramaPage?,
) {
    val items = mutableStateListOf<AniDramaItem>()
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(true)
        private set
    private var nextPage = 1

    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        failed = false
        scope.launch {
            val result = runCatching { loader(nextPage) }.getOrNull()
            if (result == null) {
                failed = true
            } else {
                val known = items.mapTo(HashSet()) { it.key }
                items.addAll(result.items.filter { known.add(it.key) })
                nextPage++
                hasMore = result.hasMore && items.size < maxItems && result.items.isNotEmpty()
            }
            loading = false
        }
    }

    fun retry() {
        failed = false
        loadMore()
    }
}

/** A paged list tied to [key]: changing the key starts a fresh list. */
@Composable
internal fun rememberAniDramaPagedList(
    key: Any,
    maxItems: Int = 600,
    loader: suspend (page: Int) -> AniDramaPage?,
): AniDramaPagedList {
    val scope = rememberCoroutineScope()
    val list = remember(key) { AniDramaPagedList(scope, maxItems, loader) }
    LaunchedEffect(list) { list.loadMore() }
    return list
}

/** Loads something once per [key]; null while loading. */
internal class AniDramaLoadState<T> {
    var value by mutableStateOf<T?>(null)
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)
}

@Composable
internal fun <T> rememberAniDramaLoad(key: Any, loader: suspend () -> T?): Pair<AniDramaLoadState<T>, () -> Unit> {
    val state = remember(key) { AniDramaLoadState<T>() }
    var attempt by remember(key) { mutableStateOf(0) }
    LaunchedEffect(key, attempt) {
        state.loading = true
        state.failed = false
        val result = runCatching { loader() }.getOrNull()
        state.value = result
        state.failed = result == null
        state.loading = false
    }
    return state to { attempt++ }
}
