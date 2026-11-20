package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.tv.material3.Text

/** Fenrir red used by the "READ" half of the wordmark. */
val FenrirWordmarkRed = Color(0xFFFF4D57)

/**
 * Fenrir Read wordmark: "FENRIR" with a smaller red "READ" beside it, like the phone app.
 * Sized to fit the height (and width, when bounded) it is given. The old Nuvio logo pictures
 * ([drawableOverride], [contentScale]) are no longer drawn; the parameters stay for callers.
 */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    @Suppress("UNUSED_PARAMETER") contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    @Suppress("UNUSED_PARAMETER") drawableOverride: Int? = null
) {
    val description = contentDescription ?: "Fenrir Read"
    BoxWithConstraints(
        modifier = modifier
            .alpha(alpha)
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        val byHeight: Dp = if (constraints.hasBoundedHeight) maxHeight * 0.6f else 22.dp
        val byWidth: Dp = if (constraints.hasBoundedWidth) maxWidth / 7.8f else byHeight
        val main: Dp = min(byHeight, byWidth).coerceAtLeast(8.dp)
        FenrirWordmarkText(main)
    }
}

/** The two words, with "FENRIR" at [mainSize] tall type. */
@Composable
fun FenrirWordmarkText(mainSize: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val small = mainSize * 0.6f
    with(density) {
        Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
            Text(
                text = "FENRIR",
                color = Color.White,
                fontSize = mainSize.toSp(),
                letterSpacing = (mainSize * 0.23f).toSp(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(mainSize * 0.27f))
            Box(Modifier.padding(bottom = mainSize * 0.14f)) {
                Text(
                    text = "READ",
                    color = FenrirWordmarkRed,
                    fontSize = small.toSp(),
                    letterSpacing = (small * 0.3f).toSp(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
