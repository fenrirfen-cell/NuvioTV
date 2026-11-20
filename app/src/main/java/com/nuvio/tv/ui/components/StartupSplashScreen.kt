package com.nuvio.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Fenrir opening screen, like the phone app: near-black with "FENRIR", a short red line
 * and "READ", then a small loading spinner while the app gets ready.
 * The profile colour / background parameters are kept for the caller but no longer drawn.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun StartupSplashScreen(
    profileColorHex: String?,
    profileBackgroundUrl: String?,
    backgroundCacheKey: String? = null,
    skipGradient: Boolean = false,
    brandWordmarkRes: Int? = null,
    modifier: Modifier = Modifier
) {
    val wordAlpha = remember { Animatable(0f) }
    val lineWidth = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        wordAlpha.animateTo(1f, tween(350))
        lineWidth.animateTo(64f, tween(350))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.alpha(wordAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "FENRIR",
                color = Color.White,
                fontSize = 52.sp,
                letterSpacing = 18.sp,
                modifier = Modifier.padding(start = 18.dp)
            )
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .width(lineWidth.value.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FenrirWordmarkRed)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "READ",
                color = Color(0xFFA7A7B2),
                fontSize = 20.sp,
                letterSpacing = 11.sp,
                modifier = Modifier.padding(start = 11.dp)
            )
            Spacer(Modifier.height(NuvioTheme.spacing.xxl))
            LoadingIndicator(modifier = Modifier.size(NuvioTheme.spacing.xxxl))
        }
    }
}
