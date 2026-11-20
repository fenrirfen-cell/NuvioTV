package com.nuvio.tv.ui.screens.anidrama

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioTheme

private val TileShape = RoundedCornerShape(10.dp)

/** Poster width used by shelves and grids on the TV. */
internal val AniDramaTileWidth: Dp = 140.dp

/** A poster with a score/network badge, optional DUB badge and rank, then title and caption. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AniDramaPosterTile(
    item: AniDramaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDubBadge: Boolean = false,
) {
    Column(modifier = modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            shape = CardDefaults.shape(shape = TileShape),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                focusedContainerColor = NuvioTheme.colors.BackgroundCard,
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(3.dp),
                    shape = TileShape,
                ),
            ),
            scale = CardDefaults.scale(focusedScale = 1.06f),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (item.poster != null) {
                    AsyncImage(
                        model = item.poster,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = NuvioTheme.colors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    )
                }
                item.badge?.let { badge ->
                    PosterBadge(text = badge, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                item.rank?.let { rank ->
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                if (showDubBadge && item.hasEnglishDub) {
                    PosterBadge(
                        text = "DUB",
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        accent = true,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = NuvioTheme.colors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.caption?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = NuvioTheme.colors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PosterBadge(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = if (accent) NuvioTheme.colors.OnSecondary else Color.White,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (accent) NuvioTheme.colors.Secondary else Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** A rounded, focusable chip: red when selected, light when focused. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AniDramaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = NuvioTheme.colors.Secondary
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) accent else Color(0xFF1C1D26),
            focusedContainerColor = if (selected) accent else Color.White,
            contentColor = if (selected) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.TextSecondary,
            focusedContentColor = if (selected) NuvioTheme.colors.OnSecondary else Color(0xFF0B0B0F),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(2.dp),
                shape = CircleShape,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** A row of chips that scrolls sideways. */
@Composable
internal fun AniDramaChipRow(
    labels: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = AniDramaEdge, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(labels, key = { it }) { label ->
            AniDramaChip(label = label, selected = label == selected, onClick = { onSelect(label) })
        }
    }
}

/** Left/right page edge inside the tab. */
internal val AniDramaEdge: Dp = 32.dp

/** A titled horizontal row of posters with an optional red "See all" chip. */
@Composable
internal fun AniDramaShelf(
    title: String,
    items: List<AniDramaItem>,
    onItemClick: (AniDramaItem) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    showDubBadge: Boolean = false,
    onSeeAll: (() -> Unit)? = null,
    emptyText: String = "Nothing here right now.",
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AniDramaEdge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NuvioTheme.colors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            items.isNotEmpty() -> LazyRow(
                contentPadding = PaddingValues(horizontal = AniDramaEdge, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    AniDramaPosterTile(
                        item = item,
                        onClick = { onItemClick(item) },
                        showDubBadge = showDubBadge,
                        modifier = Modifier.width(AniDramaTileWidth),
                    )
                }
                if (onSeeAll != null) {
                    item(key = "see-all") {
                        Box(
                            modifier = Modifier.width(AniDramaTileWidth).aspectRatio(2f / 3f),
                            contentAlignment = Alignment.Center,
                        ) {
                            AniDramaChip(label = "See all", selected = true, onClick = onSeeAll)
                        }
                    }
                }
            }
            loading -> AniDramaLoading(Modifier.fillMaxWidth().height(AniDramaTileWidth * 1.5f))
            else -> AniDramaMessage(emptyText, Modifier.padding(horizontal = AniDramaEdge))
        }
    }
}

@Composable
internal fun AniDramaLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

@Composable
internal fun AniDramaMessage(text: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.colors.TextSecondary,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(8.dp))
            AniDramaChip(label = "Try again", selected = false, onClick = onRetry)
        }
    }
}

/** Header above a grid section ("November 2026 · 12 shows"). */
@Composable
internal fun AniDramaSectionHeader(title: String, note: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = NuvioTheme.colors.TextPrimary,
        )
        if (!note.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.labelMedium,
                color = NuvioTheme.colors.TextSecondary,
            )
        }
    }
}
