package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.components.NuvioDialog

/*
 * Fenrir: when the source was picked automatically (auto-play or the reused last link), the first
 * Back doesn't leave the player straight away. It offers a different source, a different episode,
 * or leaving. Back again leaves.
 */

@Composable
internal fun FenrirPlayerBackChoices(
    providerName: String?,
    isSeries: Boolean,
    onChooseSource: () -> Unit,
    onPickEpisode: () -> Unit,
    onLeave: () -> Unit,
    onKeepWatching: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    NuvioDialog(
        onDismiss = onLeave,
        title = "Not the right one?",
        subtitle = buildString {
            append("This source was picked automatically")
            if (!providerName.isNullOrBlank()) append(" (").append(providerName).append(")")
            append(".")
        },
        width = 460.dp,
    ) {
        BackChoiceButton("Choose a different source", primary = true, modifier = Modifier.focusRequester(firstFocus), onClick = onChooseSource)
        if (isSeries) BackChoiceButton("Pick a different episode", onClick = onPickEpisode)
        BackChoiceButton("Leave player", onClick = onLeave)
        BackChoiceButton("Keep watching", subtle = true, onClick = onKeepWatching)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BackChoiceButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    subtle: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ButtonDefaults.shape(shape = shape),
        colors = ButtonDefaults.colors(
            containerColor = when {
                primary -> Color.White
                subtle -> Color.Transparent
                else -> Color.White.copy(alpha = 0.1f)
            },
            contentColor = when {
                primary -> Color.Black
                subtle -> Color.White.copy(alpha = 0.7f)
                else -> Color.White
            },
            focusedContainerColor = Color(0xFFFF4D57),
            focusedContentColor = Color.White,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
